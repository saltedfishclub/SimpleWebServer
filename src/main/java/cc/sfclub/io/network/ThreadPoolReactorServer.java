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
    private ServerSocketChannel _serverSocketChannel;

    //反应器
    private Reactor _reactor;

    //线程池
    private ExecutorService _excutor;

    public ThreadPoolReactorServer(int port,BiConsumer<byte[],SocketChannel> readCb,Consumer<SocketChannel> closeCb) throws IOException
    {
        //新建ServerSocketChannel并监听指定端口
        _serverSocketChannel = ServerSocketChannel.open();
        _serverSocketChannel.bind(new InetSocketAddress(port));
        //使用固定大小的线程池
        _excutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        _reactor = new Reactor((buf,sock)->
        {
            /**
             * 数据到达回调
             */
            //放入线程池中执行
            _excutor.submit(()->
            {
                readCb.accept(buf, sock);
            });
        }, (sock)->
        {
            /**
             * 客户端断开回调
             */
            //放入线程池中执行
            _excutor.submit(()->
            {
                closeCb.accept(sock);
            });
        },(sock)->{
            /**
             * 新连接回调
             */
            try {
                _reactor.register(sock);
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        _reactor.register(_serverSocketChannel);
    }

    @Override
    public void run() throws IOException {
        _reactor.run();
    }

    @Override
    public void close() throws IOException {
        _reactor.stop();
        _serverSocketChannel.close();
    }
    
}