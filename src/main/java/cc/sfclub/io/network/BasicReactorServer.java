package cc.sfclub.io.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

//BasicReactor Server
//使用Basic Reactor 模型
public class BasicReactorServer implements IServer {

    //Reactor
    private Reactor _reactor;

    //Server Channel
    private ServerSocketChannel _serverSocketChannel;

    public BasicReactorServer(int port,Consumer<SocketChannel> closeCb,BiConsumer<byte[],SocketChannel> readCb) throws IOException
    {
        //新建ServerSocketChannel
        //并监听指定的端口
        _serverSocketChannel = ServerSocketChannel.open();
        _serverSocketChannel.bind(new InetSocketAddress(port));
        //新建Ractor
        _reactor = new Reactor(readCb, closeCb,(channel)->
        {
            /**
             * 新连接回调
             */
            try {
                //将SocketChannel注册到Reactor
                _reactor.register(channel);
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        //将ServerChannel注册到Reactor
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