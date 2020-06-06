package com.nature.io.network;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Reactor {
    //selector
    private Selector _selector;
    //数据到达回调
    private BiConsumer<byte[],Socket> _readCb;
    //客户端关闭回调
    private Consumer<Socket> _closeCb;
    //cancel token
    private boolean _token;

    public Reactor(BiConsumer<byte[],Socket> readCb,Consumer<Socket> closeCb) throws IOException
    {
        _selector = Selector.open();
        _readCb = readCb;
        _closeCb = closeCb;
        _token = false;
    }

    public void register(Socket sock) throws ClosedChannelException
    {
        //在生产环境中您应该同时监听accept & write
        sock.getChannel().register(_selector, SelectionKey.OP_READ);
    }

    private void handleIoEvent(SelectionKey key) throws IOException
    {
        //可读事件
        if(key.isReadable())
        {
            SocketChannel channel = (SocketChannel) key.channel();
            //8192是系统Socket缓冲区的大小
            ByteBuffer buf = ByteBuffer.allocate(8192);
            int r = channel.read(buf);
            //返回0说明对端关闭
            if(r == 0)
            {
                _closeCb.accept(channel.socket());
            }
            else if(r > 0)
            {
                _readCb.accept(buf.array(),channel.socket());
            }
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