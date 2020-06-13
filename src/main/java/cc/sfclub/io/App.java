package cc.sfclub.io;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

import cc.sfclub.io.network.*;

public class App 
{
    //数据到达回调
    private static void on_read(final byte[] data, final Socket sock) throws IOException 
    {
        //发送HTTP请求后关闭socket
        sock.getOutputStream().write("HTTP/1.0 200 OK\r\nContent-Length: 11\r\n\r\nHello World".getBytes());
        sock.close();
    }

    private static void on_read(final byte[] data, final SocketChannel sock) throws IOException 
    {
        //发送HTTP请求后关闭socket
        ByteBuffer buf = ByteBuffer.wrap("HTTP/1.0 200 OK\r\nContent-Length: 11\r\n\r\nHello World".getBytes());
        sock.write(buf);
        sock.close();
    }

    //客户端断开回调
    private static void on_close(final Socket sock) throws IOException 
    {
        System.out.println(sock.getInetAddress().toString()+" close");
        sock.close();
    }

    private static void on_close(final SocketChannel sock) throws IOException 
    {
        System.out.println(sock.socket().getInetAddress().toString()+" close");
        sock.close();
    }

    //服务器线程
    public class ServerThread  extends Thread {
        IServer _server;

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

    public static void main(final String[] args) 
    {
        Scanner scanner = new Scanner(System.in);
        //获取监听的端口
        System.out.println("Please input port:");
        int port = scanner.nextInt();
        //获取用户选择的服务器并发模型
        System.out.println("Please chose a server:");
        System.out.println("1)Multithread Model Server - one connection per thread");
        System.out.println("2)Reactor Model Server - basic reactor");
        System.out.println("3)Reactor Model Server With Thread Pool - reactor + thread pool");
        System.out.println("4)MultiReactor Model Server - one loop per thread");
        int model = scanner.nextInt();
        //释放scanner
        scanner.close();
        IServer server = null;
        //NIO 客户端断开回调
        Consumer<SocketChannel> br_closeCb = (sock) -> {
            try {
                on_close(sock);
            } 
            catch (final Exception e) {
                // 在生产环境中您应该做异常处理
              e.printStackTrace();
            }
        };
        //NIO 数据到达回调
        BiConsumer<byte[], SocketChannel> br_readCb = (buf, sock) -> {
            try {
                on_read(buf, sock);
            } 
            catch (final Exception e) {
                e.printStackTrace();
            }
        };
        try {
            switch (model) {
                case 1:
                    //model == 1
                    //BIO one connection per thread 模型
                    //BIO 客户端断开回调
                    Consumer<Socket> closeCb = (sock) -> {
                        try {
                             on_close(sock);
                        } 
                        catch (final Exception e) {
                            // 在生产环境中您应该做异常处理
                              e.printStackTrace();
                        }
                    };
                    //BIO 数据到达回调
                    BiConsumer<byte[], Socket> readCb = (buf, sock) -> {
                        try {
                          on_read(buf, sock);
                        } 
                        catch (final Exception e) {
                         e.printStackTrace();
                        }
                    };
                    server = new SimpleThreadServer(port, readCb, closeCb);
                    break;
                case 2:
                    //model == 2
                    //basic reactor 模型
                    server = new BasicReactorServer(port, br_closeCb, br_readCb);
                    break;
                case 3:
                    //model == 3
                    //reactor + thread pool 模型
                    server = new ThreadPoolReactorServer(port, br_readCb, br_closeCb);
                    break;
                case 4:
                    //model == 4
                    //one loop per thread 模型
                    server = new MultiReactorServer(port,Runtime.getRuntime().availableProcessors(), br_readCb, br_closeCb);
                    break;
                default:
                    //未知的类型
                    System.out.println("Unknow Server Type");
                    return;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        //创建服务器线程
        App app = new App();
        ServerThread thread = app.new ServerThread(server);
        //启动服务器线程
        thread.start();
        //创建Closer线程
        ServerCloserThread closer = new ServerCloserThread(thread, server);
        //hook ctrl + c
        Runtime.getRuntime().addShutdownHook(closer);
        System.out.println("please enter ctrl+c to close server");
        try {
            //等待closer线程结束
            closer.join();
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
