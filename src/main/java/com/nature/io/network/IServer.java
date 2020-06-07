package com.nature.io.network;

import java.io.IOException;

//服务器接口
public interface IServer {
    public void run() throws IOException;

    public void close() throws IOException;
}