package com.nature.io.network;

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
        _serverSocketChannel = ServerSocketChannel.open();
        _serverSocketChannel.bind(new InetSocketAddress(port));
        _reactor = new Reactor(readCb, closeCb,(channel)->
        {
            try {
                _reactor.register(channel);
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