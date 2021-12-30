package cc.sfclub.io.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

//Reactor With Thread Pool Server
//使用Reactor + Thread Pool 模型
//适合密集计算 逻辑型服务器
public class ThreadPoolReactorServer implements IServer {

    //Server Channel
    private ServerSocketChannel serverSocketChannel_;

    //反应器
    private Reactor reactor_;

    //线程池
    private ExecutorService threadPool_;

    public ThreadPoolReactorServer(int port,BiConsumer<byte[],SocketChannel> readCb,Consumer<SocketChannel> closeCb) throws IOException
    {
        //新建ServerSocketChannel并监听指定端口
        serverSocketChannel_ = ServerSocketChannel.open();
        serverSocketChannel_.bind(new InetSocketAddress(port));
        //使用固定大小的线程池
        threadPool_ = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        reactor_ = new Reactor((buf,sock)->
        {
            /**
             * 数据到达回调
             */
            //放入线程池中执行
            threadPool_.submit(()->
            {
                readCb.accept(buf, sock);
            });
        }, (sock)->
        {
            /**
             * 客户端断开回调
             */
            //放入线程池中执行
            threadPool_.submit(()->
            {
                closeCb.accept(sock);
            });
        },(sock)->{
            /**
             * 新连接回调
             */
            try {
                reactor_.register(sock);
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        reactor_.register(serverSocketChannel_);
    }

    @Override
    public void run() throws IOException {
        reactor_.run();
    }

    @Override
    public void close() throws IOException {
        reactor_.stop();
        serverSocketChannel_.close();
    }
    
}