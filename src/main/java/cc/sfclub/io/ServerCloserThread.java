package cc.sfclub.io;

import cc.sfclub.io.network.IServer;

//负责关闭服务器的线程
public class ServerCloserThread extends Thread {

    //服务器线程
    private Thread thread_;

    //需要关闭的服务器
    private IServer server_;

    public ServerCloserThread(Thread thread,IServer server)
    {
        thread_ = thread;
        server_ = server;
    }

    @Override
    public void run()
    {
        System.out.println("Server Closing");
        try {
            //关闭服务器
            server_.close();
            //等待服务器线程结束
            thread_.join();
            System.out.println("Server Closed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}