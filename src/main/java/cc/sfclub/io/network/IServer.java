package cc.sfclub.io.network;

import java.io.IOException;

//服务器接口
//调用run启动服务器并阻塞当前线程直到服务器关闭
//调用close关闭服务器
public interface IServer {
    void run() throws IOException;

    void close() throws IOException;
}