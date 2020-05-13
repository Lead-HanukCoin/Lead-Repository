package il.ac.tau.cs.hanukcoin;

import java.io.IOException;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Server {




    public static void main (String[]args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(8086);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while(true) {
            try {
                Socket socket = serverSocket.accept(); // this blocks
                DataInputStream intput = new DataInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        String threadName;
//        Runnable r = new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        };
//        Thread t = new Thread(r);
//        t.start();
    }
}
