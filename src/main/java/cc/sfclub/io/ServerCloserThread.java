package cc.sfclub.io;

import cc.sfclub.io.network.IServer;

//负责关闭服务器的线程
public class ServerCloserThread extends Thread {

    //服务器线程
    Thread _thread;

    //需要关闭的服务器
    IServer _server;

    public ServerCloserThread(Thread thread,IServer server)
    {
        _thread = thread;
        _server = server;
    }

    @Override
    public void run()
    {
        System.out.println("Server Closing");
        try {
            //关闭服务器
            _server.close();
            //等待服务器线程结束
            _thread.join();
            System.out.println("Server Closed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}