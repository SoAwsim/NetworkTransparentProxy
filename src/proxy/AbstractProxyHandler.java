package proxy;

import proxy.utils.Logger;
import proxy.utils.ProxyStorage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public abstract class AbstractProxyHandler implements Runnable {
    protected final Socket clientSocket;
    protected final DataInputStream clientIn;
    protected final DataOutputStream clientOut;

    protected Socket serverSocket;
    protected DataInputStream serverIn;
    protected DataOutputStream serverOut;

    protected final ProxyStorage storage;
    protected final Logger clientLogs;

    protected final static int SERVER_TIMEOUT = 300;

    public AbstractProxyHandler(Socket clientSocket) throws IOException {
        this.clientSocket = clientSocket;
        clientLogs = Logger.getLogger();
        storage = ProxyStorage.getStorage();
        this.clientSocket.setSoTimeout(SERVER_TIMEOUT);
        clientIn = new DataInputStream(this.clientSocket.getInputStream());
        clientOut = new DataOutputStream(this.clientSocket.getOutputStream());
    }

    protected abstract String readHeaderFromClient() throws IOException, ArrayIndexOutOfBoundsException;
}
