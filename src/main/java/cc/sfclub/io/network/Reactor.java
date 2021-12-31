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

    // selector
    // 事件收集器,用于收集发生在channel上的事件
    // channel需要注册到selector才能被收集
    private Selector selector_;

    // 回调由外部传入,负责与业务逻辑对接

    // 数据到达回调
    // 当客户端数据到达服务器时调用
    private BiConsumer<byte[], SocketChannel> readCb_;

    // 客户端关闭回调
    // 当客户端关闭连接时调用
    private Consumer<SocketChannel> closeCb_;

    // 客户端连接回调
    // 当客户端连接到服务器时调用
    private Consumer<SocketChannel> acceptCb_;

    // cancel token
    // 运行被取消时为true
    // 必须为volatile
    private volatile boolean token_;

    // tasks
    // 单线程任务列表
    // 可以让其他线程将任务推送给reactor执行
    private LinkedList<Runnable> tasks_;

    // 自旋锁
    // 用于保护tasks
    private SpinLock lock_;

    public Reactor(BiConsumer<byte[], SocketChannel> readCb, Consumer<SocketChannel> closeCb,
            Consumer<SocketChannel> acceptCb) throws IOException {
        // 使用Selector.Open()创建Selector的实例
        selector_ = Selector.open();
        // 注册回调
        readCb_ = readCb;
        closeCb_ = closeCb;
        token_ = false;
        acceptCb_ = acceptCb;
        // 初始化任务列表
        tasks_ = new LinkedList<>();
        lock_ = new SpinLock();
    }

    // 用于其他线程向Reactor线程投递任务
    private void runInLoop(Runnable task) {
        // 这里有一个优化点,可以想办法减少唤醒次数
        // 因为唤醒十分耗时

        // 将任务放到_tasks里
        lock_.lock();
        try {
            tasks_.add(task);
        } finally {
            lock_.unlock();
        }
        // 唤醒Reactor线程
        selector_.wakeup();
    }

    // 获取其他线程投递的任务
    private LinkedList<Runnable> getTasks() {
        // 交换taks和_tasks
        // 为什么要这样？防止执行任务时长时间持有锁
        LinkedList<Runnable> tasks = new LinkedList<>();
        LinkedList<Runnable> tmp = tasks;
        lock_.lock();
        try {
            tasks = tasks_;
            tasks_ = tmp;
        } finally {
            lock_.unlock();
        }
        return tasks;
    }

    // 执行其他线程投递的操作
    // 返回boolean是为了甄别是否出现了epoll空轮询bug
    private boolean handleTasks() {
        // 遍历调用run
        LinkedList<Runnable> tasks = getTasks();
        for (Runnable task : tasks) {
            task.run();
        }
        return tasks.size() != 0;
    }

    // JVM bug:
    // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=2147719
    // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6403933
    // 重建selector
    private void rebuildSelector() throws IOException {
        // 创建新selector
        Selector selector = Selector.open();
        // 将原来selector的key移动到新建的selector上
        for (SelectionKey key : selector_.keys()) {
            key.channel().register(selector, key.interestOps());
            key.cancel();
        }
        // 交换两个selector
        Selector tmp = selector_;
        selector_ = selector;
        // 关闭旧的selector
        tmp.close();
    }

    // 注册客户端连接
    // 注意: selector的register和select都不是线程安全的
    // 如果其他线程调用了select方法
    // 另一线程在这时调用了register
    // 被注册的channel有事件到达时并不会select方法返回
    public void register(SocketChannel channel) throws IOException {
        // 在Reactor线程中注册
        runInLoop(() -> {
            try {
                // 设置为非阻塞
                channel.configureBlocking(false);
                // 将Channel注册到Selector
                // 并让selector监听read操作
                // 在能read时发起通知
                // 在生产环境中您可能要监听OP_WRITE来支持非阻塞的写操作
                // 在有数据需要写时监听OP_WRITE
                // 无数据需写入时必须将OP_WRITE移除
                // 否则将导致忙循环
                channel.register(selector_, SelectionKey.OP_READ);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 注册服务器监听器
    public void register(ServerSocketChannel channel) throws IOException {
        // 在Reactor线程中注册
        runInLoop(() -> {
            try {
                // 设置为非阻塞
                channel.configureBlocking(false);
                // 将Channel注册到Selector
                // 并让selector监听accept操作
                // 在能进行accept时发起通知
                channel.register(selector_, SelectionKey.OP_ACCEPT);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 处理IO事件
    private void handleIoEvent(SelectionKey key) throws IOException {
        // 必须先判断key是否有效
        // 在处理之前它可能被取消
        if (key.isValid()) {
            // 可读事件
            if (key.isReadable()) {
                SocketChannel channel = (SocketChannel) key.channel();
                // 4096是系统Socket读缓冲区的大小
                // 这意味着您一次最多读取4096字节
                // 你可以通过设置改变这个大小
                ByteBuffer buf = ByteBuffer.allocate(4096);
                try {
                    // 可能在读的过程中对端关闭
                    // 导致read出错
                    int r = channel.read(buf);
                    if (r > 0) {
                        // 调用数据到达回调
                        readCb_.accept(buf.array(), channel);
                    } else {
                        // 对端关闭了
                        // 调用客户端断开回调
                        closeCb_.accept(channel);
                    }
                } catch (ClosedChannelException ex) {
                    // 对端关闭了
                    // 调用客户端断开回调
                    closeCb_.accept(channel);
                }
            }
            // 客户端连接事件
            else if (key.isAcceptable()) {
                // 获取ServerSocketChannel
                ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                // 使用accept获取客户端
                SocketChannel client = channel.accept();
                // 调用客户端连接回调
                // 注意: 在回调中注册到Reactor是用户的责任
                acceptCb_.accept(client);
            }
            // 可能是客户端关闭
            else {
                Channel ch = key.channel();
                // 客户端关闭
                if (ch instanceof SocketChannel) {
                    SocketChannel channel = (SocketChannel) ch;
                    closeCb_.accept(channel);
                }
            }
        }
    }

    // 获取并处理事件
    private void runOnce() throws IOException {
        // 这里是事件循环的主体逻辑
        // 获取发生的事件
        int num_ev = selector_.select();
        // 判断事件个数大于0
        if (num_ev > 0) {
            // 获取有事件的Channel
            Set<SelectionKey> keys = selector_.selectedKeys();
            // 遍历Channel
            Iterator<SelectionKey> iter = keys.iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();
                // 处理IO事件
                handleIoEvent(key);
            }
        }
        // 处理单线程任务
        boolean r = handleTasks();
        // 如果发生的事件为0
        // 且handleTasks返回false
        // 则可能发生了空轮询bug
        // 也可能是其他线程调用了close方法来关闭了selector
        if (!r && num_ev == 0 && !token_) {
            rebuildSelector();
        }
    }

    // 运行直到Reactor关闭
    public void run() throws IOException {
        while (!token_) {
            runOnce();
        }
    }

    // 关闭Reactor
    public void stop() throws IOException {
        token_ = true;
        selector_.close();
    }

    // 非阻塞写

    public void enableWrite(SocketChannel channel) {
        // 您应该自己实现这里
        // 添加对某channel的可写监听
    }

    public void disableWrite(SocketChannel channel) {
        // 您应该自己实现这里
        // 移除对某channel的可写监听
    }
}