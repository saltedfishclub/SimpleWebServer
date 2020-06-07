package com.nature.io;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

import com.nature.io.network.*;

/**
 * Hello world!
 *
 */
public class App 
{
    private static void on_read(final byte[] data, final Socket sock) throws IOException {
        sock.getOutputStream().write("HTTP/1.0 200 OK\r\nContent-Length: 11\r\n\r\nHello World".getBytes());
        sock.close();
    }

    private static void on_read(final byte[] data, final SocketChannel sock) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap("HTTP/1.0 200 OK\r\nContent-Length: 11\r\n\r\nHello World".getBytes());
        sock.write(buf);
        sock.close();
    }

    private static void on_close(final Socket sock) throws IOException {
        System.out.println(sock.getInetAddress().toString()+" close");
        sock.close();
    }

    private static void on_close(final SocketChannel sock) throws IOException {
        System.out.println(sock.socket().getInetAddress().toString()+" close");
        sock.close();
    }

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

    public static void main(final String[] args) {
        System.out.println("Please input port:");
        Scanner scanner = new Scanner(System.in);
        int port = scanner.nextInt();
        System.out.println("Please chose a server:");
        System.out.println("1)Multithread Model Server - one connection per thread");
        System.out.println("2)Reactor Model Server - basic reactor");
        int model = scanner.nextInt();
        IServer server = null;
        try {
            switch (model) {
                case 1:
                    Consumer<Socket> closeCb = (sock) -> {
                        try {
                             on_close(sock);
                        } 
                        catch (final Exception e) {
                            // 在生产环境中您应该做异常处理
                              e.printStackTrace();
                        }
                    };
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
                    Consumer<SocketChannel> br_closeCb = (sock) -> {
                        try {
                            on_close(sock);
                        } 
                        catch (final Exception e) {
                            // 在生产环境中您应该做异常处理
                          e.printStackTrace();
                        }
                    };
                    BiConsumer<byte[], SocketChannel> br_readCb = (buf, sock) -> {
                        try {
                            on_read(buf, sock);
                        } 
                        catch (final Exception e) {
                            e.printStackTrace();
                        }
                    };
                    server = new BasicReactorServer(port, br_closeCb, br_readCb);
                    break;
                default:
                    System.out.println("Unknow Server Type");
                    System.exit(-1);
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        App app = new App();
        ServerThread thread = app.new ServerThread(server);
        thread.start();
        ServerCloserThread closer = new ServerCloserThread(thread, server);
        Runtime.getRuntime().addShutdownHook(closer);
        System.out.println("please enter ctrl+c to close server");
        try {
            closer.join();
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
