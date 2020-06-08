package cc.sfclub.io.network;

import java.io.IOException;

//服务器接口
public interface IServer {
    void run() throws IOException;

    void close() throws IOException;
}