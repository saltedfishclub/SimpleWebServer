package cc.sfclub.io.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

//Acceptor
//负责接受连接
public class Acceptor {
    private ServerSocket socket_;

    public Acceptor(ServerSocket socket)
    {
        socket_ = socket;
    }

    public Socket accept() throws IOException
    {
        return socket_.accept();
    }

    public void close() throws IOException
    {
        socket_.close();
    }
}