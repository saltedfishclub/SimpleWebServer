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
    private final ServerSocketChannel _serverSocketChannel;

    //反应器
    private Reactor _reactor;

    //线程池
    private final ExecutorService _excutor;

    public ThreadPoolReactorServer(int port,BiConsumer<byte[],SocketChannel> readCb,Consumer<SocketChannel> closeCb) throws IOException
    {
        _serverSocketChannel = ServerSocketChannel.open();
        _serverSocketChannel.bind(new InetSocketAddress(port));
        //使用固定大小的线程池
        _excutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        _reactor = new Reactor((buf,sock)->
        {
            //放入线程池中执行
            _excutor.submit(()->
            {
                readCb.accept(buf, sock);
            });
        }, (sock)->
        {
            //放入线程池中执行
            _excutor.submit(()->
            {
                closeCb.accept(sock);
            });
        },(sock)->{
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