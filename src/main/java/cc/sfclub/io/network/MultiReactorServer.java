package cc.sfclub.io.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

//MultiReactor Server
//使用one loop per thread 模型
public class MultiReactorServer implements IServer {

    //多个Reactor
    private List<Reactor> reactors_;

    //指派位置
    //用于轮询调度
    //将channel平均分给reactor
    private AtomicInteger dispathPos_;

    //ServerChannel
    private ServerSocketChannel serverSocketChannel_;

    //运行Reactor的线程池
    private ExecutorService threadPool_;

    public MultiReactorServer(int port,int numOfReactor,BiConsumer<byte[],SocketChannel> readCb,Consumer<SocketChannel> closeCb) throws IOException
    {
        //numOfReactors必须大于或等于1
        if(numOfReactor < 1)
        {
            return;
        }
        //新建ServerSocketChannel并监听指定端口
        serverSocketChannel_ = ServerSocketChannel.open();
        serverSocketChannel_.bind(new InetSocketAddress(port));
        //初始化指派位置
        dispathPos_ = new AtomicInteger(0);
        //初始化Reactor列表
        reactors_ = new ArrayList<>();
        //批量创建Reactor
        for(int i = 0;i < numOfReactor;i++)
        {
            reactors_.add(new Reactor(readCb, closeCb,(sock)->
            {
                //原子性地递增指派位置并返回它原来的值
                int pos = dispathPos_.getAndIncrement();
                //对size求模获取Reactor
                pos = pos %reactors_.size();
                Reactor reactor = reactors_.get(pos);
                //将channel指派给指定的Reactor
                try {
                    reactor.register(sock);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }
        //第一个Reactor总是用来监听ServerChannel
        reactors_.get(0).register(serverSocketChannel_);
        //大于1则需要创建线程池
        if(numOfReactor > 1)
        {
            threadPool_ = Executors.newFixedThreadPool(reactors_.size()-1);
        }
    }

    //运行服务器
    //mainReactor总是由调用run的线程运行
    //其他Reactor则是由线程池运行
    @Override
    public void run() throws IOException {
        Reactor mainReactor = reactors_.get(0);
        if(reactors_.size() > 1)
        {
            //将Reactor分配给线程池
            for(int i = 1;i < reactors_.size();++i)
            {
                final int index = i;
                threadPool_.submit(()->
                {
                    System.out.println("Reactor number: "+index+" running");
                    try {
                        reactors_.get(index).run();
                    } 
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
        System.out.println("Main Reactor running");
        mainReactor.run();
    }

    @Override
    public void close() throws IOException {
        //遍历关闭Reactor
        for ( Reactor r : reactors_) {
            r.stop();
        }
        //关闭server socket channel
        serverSocketChannel_.close();
        //关闭线程池
        if(threadPool_ != null) {
            threadPool_.shutdown();
        }
    }
}