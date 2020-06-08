package cc.sfclub.io;

import cc.sfclub.io.network.IServer;

public class ServerCloserThread extends Thread {

    //等待的线程
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
            _server.close();
            _thread.join();
            System.out.println("Server Closed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}