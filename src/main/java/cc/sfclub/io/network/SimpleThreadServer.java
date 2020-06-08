package cc.sfclub.io.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import java.net.Socket;

//基于多线程设计服务器
//使用 one connection per thread 模型
public class SimpleThreadServer implements IServer{

    //Server Socket
    private final ServerSocket _sock;

    //Acceptor
    private final Acceptor _acceptor;

    //数据到达回调
    private final BiConsumer<byte[],Socket> _readCb;

    //服务器关闭回调
    private final Consumer<Socket> _closeCb;

    //cancel token
    private volatile boolean _token;

    public SimpleThreadServer(int port,BiConsumer<byte[],Socket> readCb,Consumer<Socket> closeCb) throws IOException
    {
        _sock = new ServerSocket(port);
        _readCb = readCb;
        _closeCb = closeCb;
        _token = false;
        _acceptor = new Acceptor(_sock);
    }

    public void run() throws IOException
    {
        //循环接受连接
        while(!_token)
        {
             Socket sock = _acceptor.accept();
             //为每一个连接开启一个新的线程
             Thread thred = new Thread(()->
             {
                 try
                 {
                    handleSocket(sock);
                 }
                 catch(IOException err)
                 {
                     //System.out.println("Read Error "+err.getMessage());
                 }
             });
             thred.start();
        }
    }

    public void close() throws IOException
    {
        _token = true;
        _acceptor.close();
    }

    private void handleSocket(Socket sock) throws IOException
    {
        while(!_token)
        {
            //阻塞读取数据
            InputStream stream = sock.getInputStream();
            byte[] buf = new byte[8192];
            int r = stream.read(buf);
            //返回0说明对端关闭
            if(r == 0)
            {
                _closeCb.accept(sock);  
            }
            else if (r >0)
            {
                _readCb.accept(buf,sock);
            }
        }
    }
}