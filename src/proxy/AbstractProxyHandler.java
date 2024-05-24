package proxy;

import proxy.utils.Logger;
import proxy.utils.ProxyStorage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public abstract class AbstractProxyHandler implements Runnable {
    // Variables to store the client connection and its IO
    protected final Socket clientSocket;
    protected final DataInputStream clientIn;
    protected final DataOutputStream clientOut;

    // Variables to store the server connection and its IO
    protected Socket serverSocket;
    protected DataInputStream serverIn;
    protected DataOutputStream serverOut;

    // Access the storage component (blocked domains and website caching) and the logger component
    protected final ProxyStorage storage;
    protected final Logger clientLogs;

    // Default wait time (in ms) for blocking read calls for network IO
    protected final static int SERVER_TIMEOUT = 300;

    public AbstractProxyHandler(Socket clientSocket) throws IOException {
        // Load singleton components
        clientLogs = Logger.getLogger();
        storage = ProxyStorage.getStorage();

        // Accept the client connection
        this.clientSocket = clientSocket;
        this.clientSocket.setSoTimeout(SERVER_TIMEOUT);
        clientIn = new DataInputStream(this.clientSocket.getInputStream());
        clientOut = new DataOutputStream(this.clientSocket.getOutputStream());
    }

    /**
     * Reads the HTTP header from clientIN
     *
     * @return      the HTTP header in a string
     * @throws  ArrayIndexOutOfBoundsException  If default the header buffer size is overflown.
     * @throws  IOException If an I/O error occurs
     *
     */
    protected abstract String readHeaderFromClient() throws IOException, ArrayIndexOutOfBoundsException;
}
