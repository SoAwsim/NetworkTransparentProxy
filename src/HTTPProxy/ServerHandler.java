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
    private static final int SERVER_TIMEOUT = 1000;

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
                        handleGet(header, url);
                    } else if (method.equalsIgnoreCase("post")) {
                        handlePost(header);
                    } else if (method.equalsIgnoreCase("head")) {
                        handleHead(header, url);
                    } else if (method.equalsIgnoreCase("options")) {
                        handleOptions(header);
                    } else {
                        error405();
                        return;
                    }
                } catch (BadRequestException ex) {
                    error400();
                } catch (BadGatewayException ex) {
                    error502();
                    return;
                } catch (SocketTimeoutException ex) { // Did the server close the connection?
                    return;
                } catch (UnknownHostException ex) {
                    // Do not return anything to the client and close the connection
                    return;
                } catch (IOException ex) {
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

    // Overloaded function, this one does not do caching suitable for post, options
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

    private void sendAllDataToClient(URL url, String cacheHeader) throws IOException {
        String responseHeader;
        if (cacheHeader != null) {
            responseHeader = cacheHeader;
        } else {
            try {
                responseHeader = readHeader(serverIn);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new BadGatewayException("Header limit exceeded by server", e);
            }
        }

        int secondLine = responseHeader.indexOf('\r') + 2;
        String cacheDate = canCache(new MimeHeader(responseHeader.substring(secondLine)));
        System.out.println("Data cache: " + cacheDate);

        FileOutputStream cacheFile = null;
        if (cacheDate != null) {
            try {
                cacheFile = storage.getCacheInput(url.getHost() + url.getPath());
            } catch (IOException e) {
                // Disable cache saving due to IO error
                cacheDate = null;
            }
        }

        boolean serverRead = false;
        clientOut.writeBytes(responseHeader);
        if (cacheDate != null && cacheFile != null) {
            cacheFile.write(responseHeader.getBytes());
            cacheFile.flush();
        }
        while (true) {
            try {
                if (cacheDate != null && cacheFile != null) {
                    byte [] buffer = new byte[8192];
                    int read;
                    while((read = serverIn.read(buffer, 0, 8192)) >= 0) {
                        clientOut.write(buffer, 0, read);
                        cacheFile.write(buffer, 0, read);
                        cacheFile.flush();
                    }
                } else {
                    serverIn.transferTo(clientOut);
                }
            } catch (SocketTimeoutException ex) {
                // no data sent in SERVER_TIMEOUT ms
            }

            if (cacheFile != null) {
                cacheFile.flush();
                cacheFile.close();
                storage.saveCacheIndex(url.getHost() + url.getPath(), cacheDate);
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

    private String canCache(MimeHeader parameters) {
        // Can we cache the content?
        return parameters.get("Last-Modified");
    }

    private void handleHead(String header, URL url) throws IOException {
        Object[] cacheResult = storage.isCached(url.getHost() + url.getPath());
        boolean headerSent = false;
        int secondLine = header.indexOf('\r') + 2;
        MimeHeader mh = new MimeHeader(header.substring(secondLine));
        String cacheResponse = null;

        // Did the client cache it?
        if (mh.get("If-Modified-Since") == null) {
            if (cacheResult != null) {
                String cacheDate = (String) cacheResult[1];
                mh.put("If-Modified-Since", cacheDate);
                String request = header.substring(0, secondLine) + mh;
                System.out.println("Asking if the cache is valid");
                serverOut.writeBytes(request);
                headerSent = true;
                cacheResponse = readHeader(serverIn);
                // Can use the website in the cache?
                if (cacheResponse.substring(0, 3).equalsIgnoreCase("304")) {
                    System.out.println("Cached website found for " + url.getHost() + url.getPath());
                    FileInputStream cache = (FileInputStream) cacheResult[0];
                    cache.transferTo(clientOut);
                    cache.close();
                    return;
                } else {
                    System.out.println("Cache requires update!");
                }
            }
        }

        if (!headerSent) {
            serverOut.writeBytes(header);
        }

        System.out.println("Sent HEAD request to server");
        System.out.println(header);
        sendAllDataToClient(url, cacheResponse);
    }

    private void handleGet(String header, URL url) throws IOException {
        Object[] cacheResult = storage.isCached(url.getHost() + url.getPath());
        boolean headerSent = false;
        int secondLine = header.indexOf('\r') + 2;
        MimeHeader mh = new MimeHeader(header.substring(secondLine));
        String cacheResponse = null;

        // Did the client cache it?
        if (mh.get("If-Modified-Since") == null) {
            if (cacheResult != null) {
                String cacheDate = (String) cacheResult[1];
                mh.put("If-Modified-Since", cacheDate);
                String request = header.substring(0, secondLine) + mh;
                System.out.println("Asking if the cache is valid");
                System.out.println(request + "\n");
                serverOut.writeBytes(request);
                headerSent = true;
                cacheResponse = readHeader(serverIn);
                // Can use the website in the cache?
                int firstStatus = cacheResponse.indexOf(" ") + 1;
                if (cacheResponse.substring(firstStatus, firstStatus + 3).equalsIgnoreCase("304")) {
                    System.out.println("Cached website found for " + url.getHost() + url.getPath());
                    FileInputStream cache = (FileInputStream) cacheResult[0];
                    cache.transferTo(clientOut);
                    cache.close();
                    return;
                } else {
                    System.out.println("Cache requires update!");
                }
            }
        }

        if (!headerSent) {
            serverOut.writeBytes(header);
        }

        System.out.println("Sent GET to Web Server:");
        System.out.println(header);
        sendAllDataToClient(url, cacheResponse);
    }

    private void handlePost(String header) throws IOException {
        // TODO maybe use threads here
        serverOut.writeBytes(header);
        clientSock.setSoTimeout(100);
        try {
            clientIn.transferTo(serverOut);
        } catch (SocketTimeoutException ignore) {
            // Ignore since all data transfer should have finished
        }
        clientSock.setSoTimeout(SERVER_TIMEOUT);

        System.out.println("Sent POST to Web Server:");
        System.out.println(header);

        String responseHeader = readHeader(serverIn);
        clientOut.writeBytes(responseHeader);
        sendAllDataToClient();
    }

    private void handleOptions(String header) throws IOException {
        serverOut.writeBytes(header);
        System.out.println("Sent OPTIONS request to server");
        System.out.println(header);
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