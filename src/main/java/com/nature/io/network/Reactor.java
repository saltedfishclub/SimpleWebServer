package com.nature.io.network;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import sun.nio.ch.*;

public class Reactor {
    private Selector _selector;
    private BiConsumer<ByteBuffer,SocketChannel> _readCb;
    private Consumer<SocketChannel> _closeCb;

    public Reactor(BiConsumer<ByteBuffer,SocketChannel> readCb,Consumer<SocketChannel> closeCb) throws IOException
    {
        //不使用Selector.open()
        _selector = sun.nio.ch.DefaultSelectorProvider.create().openSelector();
        _readCb = readCb;
        _closeCb = closeCb;
    }

    public void register(Socket sock) throws ClosedChannelException
    {
        //在生产环境中您应该同时监听accept & write
        sock.getChannel().register(_selector, SelectionKey.OP_READ);
    }

    private void handleIoEvent(SelectionKey key)
    {
        //可读事件
        if(key.isReadable())
        {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buf = ByteBuffer.allocate(8192);
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
                handleIoEvent(iter.next());
            }
        }
    }
}