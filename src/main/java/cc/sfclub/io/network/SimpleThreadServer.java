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


    //Acceptor
    private Acceptor acceptor_;

    //数据到达回调
    private BiConsumer<byte[],Socket> readCb_;

    //服务器关闭回调
    private Consumer<Socket> closeCb_;

    //cancel token
    private volatile boolean token_;

    public SimpleThreadServer(int port,BiConsumer<byte[],Socket> readCb,Consumer<Socket> closeCb) throws IOException
    {
        //新建ServerSocket并监听指定端口
        ServerSocket sock = new ServerSocket(port);
        readCb_ = readCb;
        closeCb_ = closeCb;
        token_ = false;
        //Acceptor用于接受连接
        acceptor_ = new Acceptor(sock);
    }

    public void run() throws IOException
    {
        //循环接受连接
        while(!token_)
        {
             Socket sock = acceptor_.accept();
             //为每一个连接开启一个新的线程
             Thread thred = new Thread(()->
             {
                 try
                 {
                     //处理Socket
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

    //关闭监听
    public void close() throws IOException
    {
        token_ = true;
        acceptor_.close();
    }

    private void handleSocket(Socket sock) throws IOException
    {
        while(!token_)
        {
            //阻塞读取数据
            InputStream stream = sock.getInputStream();
            byte[] buf = new byte[8192];
            int r = stream.read(buf);
            //返回0说明对端关闭
            if(r == 0)
            {
                closeCb_.accept(sock);  
            }
            else if (r >0)
            {
                readCb_.accept(buf,sock);
            }
        }
    }
}