package cc.sfclub.io.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

//MultiReactor Server
//使用one loop per thread 模型
//适用于密集IO 有较强的处理突发IO的能力
public class MultiReactorServer implements IServer {

    //多个Reactor
    private List<Reactor> _reactors;

    //指派位置
    private AtomicInteger _dispath_pos;

    private ServerSocketChannel _serverSocketChannel;

    private ExecutorService _threadPool;

    public MultiReactorServer(int port,int num_reactors,BiConsumer<byte[],SocketChannel> readCb,Consumer<SocketChannel> closeCb) throws IOException
    {
        if(num_reactors < 1)
        {
            return;
        }
        _serverSocketChannel = ServerSocketChannel.open();
        _serverSocketChannel.bind(new InetSocketAddress(port));
        _dispath_pos = new AtomicInteger();
        _dispath_pos.set(0);
        _reactors = new ArrayList<>();
        for(int i = 0;i < num_reactors;i++)
        {
            _reactors.add(new Reactor(readCb, closeCb,(sock)->
            {
                int pos = _dispath_pos.getAndIncrement();
                pos = pos %_reactors.size();
                Reactor reactor = _reactors.get(pos);
                try {
                    reactor.register(sock);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }
        _reactors.get(0).register(_serverSocketChannel);
        if(num_reactors > 1)
        {
            _threadPool = Executors.newFixedThreadPool(_reactors.size()-1);
        }
    }

    @Override
    public void run() throws IOException {
        Reactor mainReactor = _reactors.get(0);
        if(_reactors.size() > 1)
        {
            for(int i = 1;i < _reactors.size();++i)
            {
                final int index = i;
                _threadPool.submit(()->
                {
                    System.out.println("Reactor number: "+index+" running");
                    try {
                        _reactors.get(index).run();
                    } 
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
        System.out.println("Main Reactor running");
        mainReactor.run();
    }

    @Override
    public void close() throws IOException {
        for ( Reactor r : _reactors) {
            r.stop();
        }
        _serverSocketChannel.close();
    }
}