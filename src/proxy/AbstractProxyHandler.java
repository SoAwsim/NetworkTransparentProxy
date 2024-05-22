package proxy;

import proxy.utils.Logger;
import proxy.utils.ProxyStorage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public abstract class AbstractProxyHandler implements Runnable {
    protected final Socket clientSock;
    protected final DataInputStream clientIn;
    protected final DataOutputStream clientOut;

    protected Socket serverSocket;
    protected DataInputStream serverIn;
    protected DataOutputStream serverOut;

    protected final ProxyStorage storage;
    protected final Logger clientLogs;

    protected int SERVER_TIMEOUT = 2000;

    public AbstractProxyHandler(Socket clientSocket) throws IOException {
        clientSock = clientSocket;
        clientLogs = Logger.getLogger();
        storage = ProxyStorage.getStorage();
        clientSock.setSoTimeout(SERVER_TIMEOUT);
        clientIn = new DataInputStream(clientSock.getInputStream());
        clientOut = new DataOutputStream(clientSock.getOutputStream());
    }

    protected abstract String readHeader() throws IOException, ArrayIndexOutOfBoundsException;
}
