package proxy;

import proxy.utils.Logger;
import proxy.utils.ProxyStorage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;

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

    protected void performAuthentication() throws IOException {
        InetAddress clientIP = clientSocket.getInetAddress();
        // Client not authenticated
        if (storage.checkIfClientAllowed(clientIP)) {
            return;
        }
        String html = "<html><body>\n\r" +
                "<html>\n\r" +
                "<body>\n\r" +
                "<form action=\"http://192.168.123.11\" method=\"GET\">\n\r" +
                "   <label for=\"token\">Token:</label><br>\n\r" +
                "   <input type=\"text\" id=\"token\" name=\"token\" value=\"\"><br><br>\n\r" +
                "   <input type=\"submit\" value=\"Submit\">\n\r" +
                "</form>\n\r" +
                "</body>\n\r" +
                "</html>\n\r";
        String response = "HTTP/1.1 401 Unauthorized\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Captive Portal\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Host: 192.168.123.11\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        clientOut.writeBytes(response);
    }
}
