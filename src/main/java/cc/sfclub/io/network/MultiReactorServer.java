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
    //用于轮询调度
    private AtomicInteger _dispath_pos;

    //ServerChannel
    private ServerSocketChannel _serverSocketChannel;

    //运行Reactor的线程池
    private ExecutorService _threadPool;

    public MultiReactorServer(int port,int num_reactors,BiConsumer<byte[],SocketChannel> readCb,Consumer<SocketChannel> closeCb) throws IOException
    {
        //num_reactors必须大于或等于1
        if(num_reactors < 1)
        {
            return;
        }
        //新建ServerChannel并监听指定端口
        _serverSocketChannel = ServerSocketChannel.open();
        _serverSocketChannel.bind(new InetSocketAddress(port));
        //初始化指派位置
        _dispath_pos = new AtomicInteger(0);
        //初始化Reactor列表
        _reactors = new ArrayList<>();
        //批量创建Reactor
        for(int i = 0;i < num_reactors;i++)
        {
            _reactors.add(new Reactor(readCb, closeCb,(sock)->
            {
                //原子性地递增指派位置并返回它原来的值
                int pos = _dispath_pos.getAndIncrement();
                //对size求模获取Reactor
                pos = pos %_reactors.size();
                Reactor reactor = _reactors.get(pos);
                //将channel指派给指定的Reactor
                try {
                    reactor.register(sock);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }
        //第一个Reactor总是用来监听ServerChannel
        _reactors.get(0).register(_serverSocketChannel);
        //大于1则需要创建线程池
        if(num_reactors > 1)
        {
            _threadPool = Executors.newFixedThreadPool(_reactors.size()-1);
        }
    }

    //运行服务器
    //mainReactor总是由调用run的线程运行
    //其他Reactor则是由线程池运行
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
        //遍历关闭Reactor
        for ( Reactor r : _reactors) {
            r.stop();
        }
        _serverSocketChannel.close();
    }
}