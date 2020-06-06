package com.nature.io.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BasicReactorServer implements IServer {

    private Reactor _reactor;

    private ServerSocketChannel _serverSocketChannel;

    private boolean _token;

    public BasicReactorServer(int port,Consumer<SocketChannel> closeCb,BiConsumer<byte[],SocketChannel> readCb) throws IOException
    {
        _serverSocketChannel = ServerSocketChannel.open();
        _serverSocketChannel.bind(new InetSocketAddress(port));
        _reactor = new Reactor(readCb, closeCb,(channel)->{});
        _token = false;
        _reactor.register(_serverSocketChannel);
    }

    @Override
    public void run() throws IOException {
        _reactor.run();
    }

    @Override
    public void close() throws IOException {
        _token =true;
        _reactor.stop();
    }
}