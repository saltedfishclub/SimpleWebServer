package cc.sfclub.io;

import cc.sfclub.io.network.IServer;

public class ServerThread extends Thread {
    private IServer _server;

    public ServerThread(IServer server)
    {
        super();
        _server = server;
    }
    
    @Override
    public void run()
    {
        try {
            _server.run();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }   
}