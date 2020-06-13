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

    private void runInLoop(Runnable task)
    {
        _lock.lock();
        try {
            _tasks.add(task);
        } 
        finally
        {
            _lock.unlock();
        }
        _selector.wakeup();
    }

    private LinkedList<Runnable> getTasks()
    {
        LinkedList<Runnable> tasks = new LinkedList<>(),tmp = tasks;
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

    private boolean handleTasks()
    {
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
    private void rebuildSelector() throws IOException
    {
        Selector selector = Selector.open();
        for(SelectionKey key:_selector.keys())
        {
            key.channel().register(selector,key.interestOps());
            key.cancel();
        }
        Selector tmp = _selector;
        _selector = selector;
        tmp.close();
    }

    //注册客户端连接
    public void register(SocketChannel channel) throws IOException
    {
        runInLoop(()->
        {
            try {
                //设置为非阻塞
                channel.configureBlocking(false);
                //将Channel注册到Selector
                 //在生产环境中您应该同时监听write
                channel.register(_selector, SelectionKey.OP_READ);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    //注册服务器监听器
    public void register(ServerSocketChannel channel) throws IOException
    {
        runInLoop(()->
        {
            try {
                //设置为非阻塞
                channel.configureBlocking(false);
                //将Channel注册到Selector
                 //在生产环境中您应该同时监听write
                channel.register(_selector, SelectionKey.OP_ACCEPT);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    //处理IO事件
    private void handleIoEvent(SelectionKey key) throws IOException
    {
        if(key.isValid())
        {
             //可读事件
            if(key.isReadable())
            {
                SocketChannel channel = (SocketChannel) key.channel();
                //8192是系统Socket缓冲区的大小
                ByteBuffer buf = ByteBuffer.allocate(8192);
                try 
                {
                    int r = channel.read(buf);
                    if(r > 0)
                    {
                        _readCb.accept(buf.array(),channel);
                    }
                    else
                    {
                        _closeCb.accept(channel);
                    }
                }    
                catch (ClosedChannelException ex) 
                {
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
                //注册到Selector
                //register(client);
                //调用客户端连接回调
                _acceptCb.accept(client);
            }
            //可能是客户端关闭
            else
            {
                Channel _ch = key.channel();
                //客户端关闭
                if(_ch instanceof SocketChannel)
                {
                    SocketChannel channel = (SocketChannel)_ch;
                    _closeCb.accept(channel);
                }
            }
        }
    }

    private void run_once() throws IOException
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
        if(!r && num_ev == 0 && !_token)
        {
            rebuildSelector();
        }
    }

    public void run() throws IOException
    {
        while(!_token)
        {
            run_once();
        }
    }

    public void stop() throws IOException
    {
        _token = true;
        _selector.close();
    }
}