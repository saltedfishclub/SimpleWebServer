package cc.sfclub.io;

import cc.sfclub.io.network.IServer;

public class ServerThread extends Thread {
    private IServer server_;

    public ServerThread(IServer server)
    {
        super();
        server_ = server;
    }
    
    @Override
    public void run()
    {
        try {
            server_.run();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }   
}