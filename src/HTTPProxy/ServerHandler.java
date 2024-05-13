package HTTPProxy;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerHandler implements Runnable {
    private final Socket clientSock;
    private final DataInputStream clientIn;
    private final DataOutputStream clientOut;

    private Socket serverSocket;
    private DataInputStream serverIn;
    private DataOutputStream serverOut;

    private final ProxyStorage storage;

    private boolean keepConnection = true;

    private int tempData = -2;
    private static final int SERVER_TIMEOUT = 2000;

    // Throw IOException to upper level since this Runnable should not execute
    public ServerHandler(Socket c, ProxyStorage storage) throws IOException {
        clientSock = c;
        this.storage = storage;
        c.setSoTimeout(SERVER_TIMEOUT);
        clientIn = new DataInputStream(clientSock.getInputStream());
        clientOut = new DataOutputStream(clientSock.getOutputStream());
    }

    @Override
    public void run() {
        try {
            do {
                String header;
                try {
                    header = readHeader();
                } catch (ArrayIndexOutOfBoundsException ex) { // Invalid header size return 413
                    error414();
                    return;
                } catch (SocketTimeoutException ex) {
                    // Close socket and exit
                    return;
                } catch (IOException ex) {
                    error500();
                    return;
                }

                int methodFinIndex = header.indexOf(' ');
                int pathFinIndex = header.indexOf(' ', methodFinIndex + 1);

                String method = header.substring(0, methodFinIndex); // GET http://example.com/path HTTP/1.1
                System.out.println("Log: " + method);
                String fullPath = header.substring(methodFinIndex + 1, pathFinIndex);
                System.out.println("Log: " + fullPath);

                URL url;
                try {
                    url = new URL(fullPath);
                } catch (MalformedURLException e) {
                    error400();
                    return;
                }

                InetAddress serverIP;
                try {
                    serverIP = InetAddress.getByName(url.getHost());
                } catch (UnknownHostException e) {
                    error400();
                    return;
                }

                try {
                    if (serverSocket == null) {
                        serverSocket = new Socket(serverIP, 80);
                        serverSocket.setSoTimeout(SERVER_TIMEOUT);
                        serverIn = new DataInputStream(serverSocket.getInputStream());
                        serverOut = new DataOutputStream(serverSocket.getOutputStream());
                    } else if (serverSocket.getInetAddress() != serverIP) {
                        serverSocket.close();
                        serverSocket = new Socket(serverIP, 80);
                        serverSocket.setSoTimeout(SERVER_TIMEOUT);
                        serverIn = new DataInputStream(serverSocket.getInputStream());
                        serverOut = new DataOutputStream(serverSocket.getOutputStream());
                    }

                    if (method.equalsIgnoreCase("get")) {
                        handleGet(header);
                    } else if (method.equalsIgnoreCase("post")) {
                        handlePost(header);
                    } else if (method.equalsIgnoreCase("head")) {
                        handleHead(header);
                    } else {
                        error405();
                        return;
                    }
                } catch (BadRequestException ex) {
                    error400();
                } catch (BadGatewayException ex) {
                    error502();
                    return;
                } catch (SocketException ex) { // Did the server close the connection?
                    return;
                } catch (UnknownHostException ex) {
                    // Do not return anything to the client and close the connection
                    return;
                } catch (IOException ex) {
                    error500();
                    throw new RuntimeException(ex);
                }
            } while (keepConnection);
        } finally {
            System.out.println("Closing connection from server side");
            try {
                clientIn.close();
                clientOut.close();
                clientSock.close();

                if (serverSocket != null) {
                    serverIn.close();
                    serverOut.close();
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing the socket!");
            }
        }
    }

    private void sendAllDataToClient() throws IOException {
        boolean serverRead = false;
        while (true) {
            try {
                serverIn.transferTo(clientOut);
            } catch (SocketTimeoutException ex) {
                // no data sent in SERVER_TIMEOUT ms
            }

            try {
                tempData = clientIn.read();
                // Did the client close the connection?
                if (tempData == -1) {
                    System.out.println("Client closed connection");
                    keepConnection = false;
                    return;
                }
                // Client has a reply continue with the persistent connection
                break;
            } catch (SocketTimeoutException ex) {
                // If this is the first timeout allow more time to the server
                // If second time assume connection was lost or timeout
                if (!serverRead) {
                    serverRead = true;
                } else {
                    break;
                }
                // Allow for longer wait for next connection
                clientSock.setSoTimeout(SERVER_TIMEOUT * 2);
                serverSocket.setSoTimeout(SERVER_TIMEOUT * 2);
            }
        }
        clientSock.setSoTimeout(SERVER_TIMEOUT);
        serverSocket.setSoTimeout(SERVER_TIMEOUT);
    }

    private boolean cacheData(String header) {
        MimeHeader parameters = new MimeHeader(header);
        byte[] data = new byte[10];
        // Can we cache the content?
        if(parameters.get("Last-Modified") != null) {
            String a = parameters.get("Host");
            try {
                storage.saveToCache(a, data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }
        return false;
    }

    private void handleHead(String header) throws IOException {
        serverOut.writeBytes(header);
        System.out.println("Sent HEAD request to server");
        System.out.println(header);
        sendAllDataToClient();
    }

    private void handleGet(String header) throws IOException {
        serverOut.writeBytes(header);
        System.out.println("Sent GET to Web Server:");
        System.out.println(header);

        String responseHeader;
        try {
            responseHeader = readHeader(serverIn);
            int secondLine = responseHeader.indexOf('\r') + 2;
            System.out.println(responseHeader);
            System.out.println(cacheData(responseHeader.substring(secondLine)));
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new BadGatewayException("Header limit exceeded by server", ex);
        }

        clientOut.writeBytes(responseHeader);
        sendAllDataToClient();
    }

    private void handlePost(String header) throws IOException {
        // TODO maybe use threads here
        serverOut.writeBytes(header);
        clientSock.setSoTimeout(100);
        try {
            clientIn.transferTo(serverOut);
        } catch (SocketTimeoutException ex) {
            // Ignore since all data transfer should have finished
        }
        clientSock.setSoTimeout(SERVER_TIMEOUT);

        System.out.println("Sent POST to Web Server:");
        System.out.println(header);

        String responseHeader = readHeader(serverIn);
        clientOut.writeBytes(responseHeader);
        sendAllDataToClient();
    }

    private String readHeader() throws IOException, ArrayIndexOutOfBoundsException {
        // Apache header limit is 8KB, so I also use this limit as well
        byte[] headerArr = new byte[8192];
        int temp;
        int i = 0;
        if (tempData != -2) {
            headerArr[i++] = (byte) tempData;
            tempData = -2;
        }
        do {
            // readByte throws IOException instead of writing -1 to array so, I use this one
            temp = clientIn.read();
            if (temp == -1) {
                throw new SocketTimeoutException("Client Disconnected");
            }
            headerArr[i++] = (byte) temp;
        } while (headerArr[i - 1] != '\n' || headerArr[i - 2] != '\r'
                || headerArr[i - 3] != '\n' || headerArr[i - 4] != '\r');

        return new String(headerArr, 0, i);
    }

    private String readHeader(DataInputStream dataIn) throws IOException, ArrayIndexOutOfBoundsException{
        byte[] headerArr = new byte[8192];
        int i = 0;
        int temp;
        do {
            // readByte throws IOException instead of writing -1 to array so, I use this one
            temp = dataIn.read();
            if (temp == -1) {
                throw new SocketTimeoutException("Client Disconnected");
            }
            headerArr[i++] = (byte) temp;
        } while (headerArr[i - 1] != '\n' || headerArr[i - 2] != '\r'
                || headerArr[i - 3] != '\n' || headerArr[i - 4] != '\r');

        return new String(headerArr, 0, i);
    }

    private void sendErrorToClient(String response) {
        try {
            clientOut.writeBytes(response);
        } catch (IOException ex) {
            // TODO show ui error
            throw new RuntimeException(ex);
        }
    }

    private void error400() {
        String html = "<html><body><h1>400 Bad Request</h1></body></html>";
        String response = "HTTP/1.1 400 Bad Request\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error405() {
        String html = "<html><body><h1>405 Method Not Allowed</h1></body></html>";
        String response = "HTTP/1.1 405 Method Not Allowed\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error414() {
        String html = "<html><body><h1>413 Entity Too Large</h1></body></html>";
        String response = "HTTP/1.1 413 Content Too Large\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error500() {
        String html = "<html><body><h1>500 Internal Server Error</h1></body></html>";
        String response = "HTTP/1.1 500 Internal Server Error\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error502() {
        String html = "<html><body><h1>502 Bad Gateway</h1></body></html>";
        String response = "HTTP/1.1 502 Bad Gateway\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }
}