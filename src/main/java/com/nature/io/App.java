package com.nature.io;

import java.beans.Customizer;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
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

    private static void on_close(final Socket sock) throws IOException {
        // System.out.println(sock.getInetAddress().toString()+" close");
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
        Consumer<Socket> closeCb = (sock) -> {
            try {
                on_close(sock);
            } catch (final Exception e) {
                // 在生产环境中您应该做异常处理
                e.printStackTrace();
            }
        };
        BiConsumer<byte[], Socket> readCb = (buf, sock) -> {
            try {
                on_read(buf, sock);
            } catch (final Exception e) {
                e.printStackTrace();
            }
        };
        try {
            switch (model) {
                case 1:
                    server = new SimpleThreadServer(port, readCb, closeCb);
                    break;
                case 2:
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
        System.out.println("please enter close to close server");
        String str = scanner.nextLine();
        if(str.equals("close"))
        {
            try {
                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            scanner.close();
        }
    }
}
