package com.nature.io.network;

import java.io.IOException;

public interface IServer {
    public void run() throws IOException;

    public void close() throws IOException;
}