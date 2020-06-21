package cc.sfclub.io.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import cc.sfclub.io.SpinLock;

//反应器
//在构造函数中传入回调后调用run方法
//run方法将会导致线程阻塞直到反应器关闭
//注意: 在客户端连接回调中将SocketChannel注册到Reactor是用户的责任
public class Reactor {
    //selector
    private Selector _selector;
    //数据到达回调
    private BiConsumer<byte[],SocketChannel> _readCb;
    //客户端关闭回调
    private Consumer<SocketChannel> _closeCb;
    //客户端连接回调
    private Consumer<SocketChannel> _acceptCb;
    //cancel token
    private volatile boolean _token;
    //tasks
    private LinkedList<Runnable> _tasks;

    //自旋锁
    //用于保护_tasks
    private SpinLock _lock;

    public Reactor(BiConsumer<byte[],SocketChannel> readCb,Consumer<SocketChannel> closeCb,Consumer<SocketChannel> acceptCb) throws IOException
    {
        _selector = Selector.open();
        _readCb = readCb;
        _closeCb = closeCb;
        _token = false;
        _acceptCb = acceptCb;
        _tasks = new LinkedList<>();
        _lock = new SpinLock();
    }

    //用于其他线程向Reactor线程投递任务
    private void runInLoop(Runnable task)
    {
        //将任务放到_tasks里
        _lock.lock();
        try {
            _tasks.add(task);
        } 
        finally
        {
            _lock.unlock();
        }
        //唤醒Reactor线程
        _selector.wakeup();
    }

    //获取其他线程投递的任务
    private LinkedList<Runnable> getTasks()
    {
        //交换taks和_tasks
        LinkedList<Runnable> tasks = new LinkedList<>();
        LinkedList<Runnable> tmp = tasks;
        _lock.lock();
        try {
            tasks = _tasks;
            _tasks = tmp;
        } 
        finally
        {
            _lock.unlock();
        }
        return tasks;
    }

    //执行其他线程投递的操作
    //返回boolean是为了甄别是否出现了epoll空轮询bug
    private boolean handleTasks()
    {
        //遍历调用run
        LinkedList<Runnable> tasks = getTasks();
        for(Runnable task:tasks)
        {
            task.run();
        }
        return tasks.size() != 0;
    }

    //JVM bug:
    //https://bugs.java.com/bugdatabase/view_bug.do?bug_id=2147719
    //https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6403933
    //重建selector
    private void rebuildSelector() throws IOException
    {
        //创建新selector
        Selector selector = Selector.open();
        //将原来selector的key移动到新建的selector上
        for(SelectionKey key:_selector.keys())
        {
            key.channel().register(selector,key.interestOps());
            key.cancel();
        }
        //交换两个selector
        Selector tmp = _selector;
        _selector = selector;
        //关闭旧的selector
        tmp.close();
    }

    //注册客户端连接
    //注意: selector的register和select都不是线程安全的
    //如果其他线程调用了select方法
    //另一线程在这时调用了register
    //被注册的channel有事件到达时并不会select方法返回
    public void register(SocketChannel channel) throws IOException
    {
        //在Reactor线程中注册
        runInLoop(()->
        {
            try {
                //设置为非阻塞
                channel.configureBlocking(false);
                //将Channel注册到Selector
                 //在生产环境中您可能要监听OP_WRITE来支持非阻塞的写操作
                 //在有数据需要写时监听OP_WRITE
                 //无数据需写入时必须将OP_WRITE移除
                 //否则将导致忙循环
                channel.register(_selector, SelectionKey.OP_READ);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    //注册服务器监听器
    public void register(ServerSocketChannel channel) throws IOException
    {
        //在Reactor线程中注册
        runInLoop(()->
        {
            try {
                //设置为非阻塞
                channel.configureBlocking(false);
                //将Channel注册到Selector
                channel.register(_selector, SelectionKey.OP_ACCEPT);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    //处理IO事件
    private void handleIoEvent(SelectionKey key) throws IOException
    {
        //必须先判断key是否有效
        //在处理之前它可能被取消
        if(key.isValid())
        {
             //可读事件
            if(key.isReadable())
            {
                SocketChannel channel = (SocketChannel) key.channel();
                //4096是系统Socket读缓冲区的大小
                //这意味着您一次最多读取4096字节
                ByteBuffer buf = ByteBuffer.allocate(4096);
                try 
                {
                    //可能在读的过程中对端关闭
                    //导致read出错
                    int r = channel.read(buf);
                    if(r > 0)
                    {
                        //调用数据到达回调
                        _readCb.accept(buf.array(),channel);
                    }
                    else
                    {
                        //对端关闭了
                        //调用客户端断开回调
                        _closeCb.accept(channel);
                    }
                }    
                catch (ClosedChannelException ex) 
                {
                    //对端关闭了
                    //调用客户端断开回调
                    _closeCb.accept(channel);    
                }
            }
            //客户端连接事件
            else if(key.isAcceptable())
            {
                //获取ServerSocketChannel
                ServerSocketChannel channel = (ServerSocketChannel)key.channel();
                //使用accept获取客户端
                SocketChannel client = channel.accept();
                //调用客户端连接回调
                //注意: 在回调中注册到Reactor是用户的责任
                _acceptCb.accept(client);
            }
            //可能是客户端关闭
            else
            {
                Channel ch = key.channel();
                //客户端关闭
                if(ch instanceof SocketChannel)
                {
                    SocketChannel channel = (SocketChannel)ch;
                    _closeCb.accept(channel);
                }
            }
        }
    }

    //获取并处理事件
    private void runOnce() throws IOException
    {
        //获取发生的事件
        int num_ev = _selector.select();
        //判断事件个数大于0
        if(num_ev > 0)
        {
            //获取有事件的Channel
            Set<SelectionKey> keys = _selector.selectedKeys();
            //遍历Channel
            Iterator<SelectionKey> iter = keys.iterator();
            while(iter.hasNext())
            {
                SelectionKey key = iter.next();
                iter.remove();
                //处理IO事件
                handleIoEvent(key);
            }
        }
        boolean r = handleTasks();
        //如果发生的事件为0
        //且handleTasks返回false
        //则可能发生了空轮询bug
        //也可能是其他线程调用了close方法来关闭了selector
        if(!r && num_ev == 0 && !_token)
        {
            rebuildSelector();
        }
    }

    //运行直到Reactor关闭
    public void run() throws IOException
    {
        while(!_token)
        {
            runOnce();
        }
    }

    //关闭Reactor
    public void stop() throws IOException
    {
        _token = true;
        _selector.close();
    }

    public void enableWrite(SocketChannel channel)
    {
        
    }

    public void disableWrite(SocketChannel channel)
    {

    }
}