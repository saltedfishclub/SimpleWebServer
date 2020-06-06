package com.nature.io.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Acceptor {
    private ServerSocket _socket;

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