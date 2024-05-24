package proxy;

import proxy.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;

public abstract class AbstractProxyListener implements Runnable {
    protected ServerSocket serverListener;
    protected final int listenPort;
    protected boolean serverOn = false;
    protected Logger logger = Logger.getLogger();

    protected static final int SERVER_THREADS = 150;

    public AbstractProxyListener(int port) throws IOException {
        listenPort = port;
        initSock();
    }

    public void initSock() throws IOException {
        if (serverListener == null) {
            serverListener = new ServerSocket(listenPort);
            serverOn = true;
        }
    }

    public void closeSocket() throws IOException {
        if (serverListener != null) {
            serverOn = false;
            serverListener.close();
            serverListener = null;
        }
    }
}
