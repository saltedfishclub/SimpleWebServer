package com.nature.io.network;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
    private boolean _token;

    public Reactor(BiConsumer<byte[],SocketChannel> readCb,Consumer<SocketChannel> closeCb,Consumer<SocketChannel> acceptCb) throws IOException
    {
        _selector = Selector.open();
        _readCb = readCb;
        _closeCb = closeCb;
        _token = false;
        _acceptCb = acceptCb;
    }

    public void register(SocketChannel channel) throws IOException
    {
        //设置为非阻塞
        channel.configureBlocking(false);
        //在生产环境中您应该同时监听write
        channel.register(_selector, SelectionKey.OP_READ);
    }

    public void register(ServerSocketChannel channel) throws IOException
    {
        channel.configureBlocking(false);
        channel.register(_selector,SelectionKey.OP_ACCEPT);
    }

    private void handleIoEvent(SelectionKey key) throws IOException
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
                _readCb.accept(buf.array(),channel);
            } 
            catch (ClosedChannelException ex) 
            {
                _closeCb.accept(channel);    
            }
        }
        else if(key.isAcceptable())
        {
            ServerSocketChannel channel = (ServerSocketChannel)key.channel();
            SocketChannel client = channel.accept();
            _acceptCb.accept(client);
            register(client);
        }
        else if(key.isValid())
        {
            SocketChannel channel = (SocketChannel)key.channel();
            _closeCb.accept(channel);
        }
    }

    private void run_once() throws IOException
    {
        //获取发生的事件
        int num_ev = _selector.select();
        if(num_ev > 0)
        {
            Set<SelectionKey> keys = _selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            while(iter.hasNext())
            {
                SelectionKey key = iter.next();
                iter.remove();
                handleIoEvent(key);
            }
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