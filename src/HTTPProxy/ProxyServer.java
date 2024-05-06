package HTTPProxy;

import gui.ProxyGui;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// curl --proxy localhost:8080 --head http://www.google.com

public class ProxyServer implements Runnable {
    private ServerSocket ServerSock;

    public ProxyServer() {
        this.initSock();
    }
    @Override
    public void run() {
        try {
            while (true) {
                Socket conSock = ServerSock.accept();
                new ServerHandler(conSock).start();
            }
        }
        catch (SocketException se) {
            System.out.println("Socket closed shutting down server");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initSock() {
        if (ServerSock == null) {
            try {
                ServerSock = new ServerSocket(8080);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void closeSock() {
        if (ServerSock != null) {
            try {
                ServerSock.close();
                ServerSock = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}