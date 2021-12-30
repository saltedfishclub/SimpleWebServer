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
    private Reactor reactor_;

    //Server Channel
    private ServerSocketChannel serverSocketChannel_;

    public BasicReactorServer(int port,Consumer<SocketChannel> closeCb,BiConsumer<byte[],SocketChannel> readCb) throws IOException
    {
        //新建ServerSocketChannel
        //并监听指定的端口
        serverSocketChannel_ = ServerSocketChannel.open();
        serverSocketChannel_.bind(new InetSocketAddress(port));
        //新建Ractor
        reactor_ = new Reactor(readCb, closeCb,(channel)->
        {
            /*
            * 新连接回调
            */
            try {
                //将SocketChannel注册到Reactor
                reactor_.register(channel);
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        //将ServerChannel注册到Reactor
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