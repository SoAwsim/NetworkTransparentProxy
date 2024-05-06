package HTTPProxy;

import gui.ErrorDisplay;

import java.io.IOException;
import java.net.*;

// curl --proxy localhost:8080 --head http://www.google.com

public class ProxyServer implements Runnable {
    private ServerSocket ServerSock;
    private final ErrorDisplay edManager;

    public ProxyServer(ErrorDisplay ed) {
        this.edManager = ed;
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
        catch (IOException e) {
            // TODO Handle this better
            edManager.showExceptionWindow(e);
        }
    }

    public void initSock() {
        if (ServerSock == null) {
            try {
                ServerSock = new ServerSocket(8080);
            } catch (IOException e) {
                edManager.showExceptionWindow(e);
            }
        }
    }

    public void closeSock() {
        if (ServerSock != null) {
            try {
                ServerSock.close();
                ServerSock = null;
            } catch (IOException e) {
                edManager.showExceptionWindow(e);
            }
        }
    }
}