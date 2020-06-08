package cc.sfclub.io.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

//Acceptor
//负责接受连接
public class Acceptor {
    private final ServerSocket _socket;

    public Acceptor(ServerSocket socket)
    {
        _socket = socket;
    }

    public Socket accept() throws IOException
    {
        return _socket.accept();
    }

    public void close() throws IOException
    {
        _socket.close();
    }
}