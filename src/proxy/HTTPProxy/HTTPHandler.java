package proxy.HTTPProxy;

import proxy.AbstractProxyHandler;

import java.io.*;
import java.net.*;
import java.util.Date;

public final class HTTPHandler extends AbstractProxyHandler {
    private boolean keepConnection = true;
    private int responseCode = -1;
    private int tempData = -2;

    // Throw IOException to upper level since this Runnable should not execute
    public HTTPHandler(Socket clientSocket) throws IOException {
        super(clientSocket);
    }

    @Override
    public void run() {
        try {
            do {
                String header = null;
                try {
                    header = readHeaderFromClient();
                } catch (ArrayIndexOutOfBoundsException ex) { // Invalid header size return 414
                    error414();
                    return;
                } catch (SocketTimeoutException ignore) {
                } catch (SocketException ex) { // Connection closed by peer
                    return;
                } catch (IOException ex) {
                    error500();
                    return;
                }

                if (header == null) {
                    // Client did not send any headers drop the connection
                    return;
                }

                int methodFinIndex = header.indexOf(' ');
                int pathFinIndex = header.indexOf(' ', methodFinIndex + 1);

                String method = header.substring(0, methodFinIndex);
                String fullPath = header.substring(methodFinIndex + 1, pathFinIndex);

                URL url;
                try {
                    url = new URL(fullPath);
                } catch (MalformedURLException e) {
                    // Maybe the hostname exists inside the header?
                    int secondLine = header.indexOf('\r') + 2;
                    MimeHeader mh = new MimeHeader(header.substring(secondLine));
                    String host = mh.get("Host");
                    if (!host.startsWith("http://")) {
                        host = "http://" + host + fullPath;
                    }
                    try {
                        url = new URL(host);
                    } catch (MalformedURLException ex) {
                        error400();
                        return;
                    }
                }

                InetAddress serverIP;
                try {
                    serverIP = InetAddress.getByName(url.getHost());
                } catch (UnknownHostException e) {
                    // Drop the connection host not found
                    return;
                }

                // Check if host is blocked
                if (storage.isBlocked(serverIP)) {
                    error401();
                    return;
                }

                try {
                    // Connect to the server
                    if (serverSocket == null) {
                        serverSocket = new Socket(serverIP, 80);
                        serverSocket.setSoTimeout(SERVER_TIMEOUT);
                        serverIn = new DataInputStream(serverSocket.getInputStream());
                        serverOut = new DataOutputStream(serverSocket.getOutputStream());
                    } else if (serverSocket.getInetAddress() != serverIP) {
                        // Client using the same port to connect other hosts
                        serverSocket.close();
                        serverSocket = new Socket(serverIP, 80);
                        serverSocket.setSoTimeout(SERVER_TIMEOUT);
                        serverIn = new DataInputStream(serverSocket.getInputStream());
                        serverOut = new DataOutputStream(serverSocket.getOutputStream());
                    }

                    if (method.equalsIgnoreCase("get")) {
                        handleGet(header, url);
                        clientLogs.addLog(clientSocket.getInetAddress(), url, "GET", Integer.toString(responseCode));
                    } else if (method.equalsIgnoreCase("post")) {
                        handlePost(header);
                        clientLogs.addLog(clientSocket.getInetAddress(), url, "POST", Integer.toString(responseCode));
                    } else if (method.equalsIgnoreCase("head")) {
                        handleHead(header, url);
                        clientLogs.addLog(clientSocket.getInetAddress(), url, "HEAD", Integer.toString(responseCode));
                    } else if (method.equalsIgnoreCase("options")) {
                        handleOptions(header);
                        clientLogs.addLog(clientSocket.getInetAddress(), url, "OPTIONS", Integer.toString(responseCode));
                    } else {
                        // Other methods are not allowed as in the project requirements
                        error405();
                        return;
                    }
                } catch (BadGatewayException ex) {
                    error502();
                    return;
                } catch (SocketTimeoutException ignore) {
                    // Continue with the persistent connection until one of the peers disconnect
                } catch (SocketException exs) {
                    // Connection closed by one of the peers safe to exit
                    return;
                } catch (IOException ex) {
                    // This should never reach here rethrow as runtime exception and crash the thread
                    throw new RuntimeException(ex);
                }
            } while (keepConnection);
        } finally {
            clientLogs.addVerboseLog("Closing HTTP connection");
            try {
                clientIn.close();
                clientOut.close();
                clientSocket.close();

                if (serverSocket != null) {
                    serverIn.close();
                    serverOut.close();
                    serverSocket.close();
                }
            } catch (IOException ignore) {
                // IOException does not matter at this point
            }
        }
    }

    // Overloaded function, this one does not do caching suitable for post, options
    private void sendAllDataToClient() throws IOException {
        for (int tryAttempt = 0; tryAttempt < 4; tryAttempt++) {
            try {
                serverIn.transferTo(clientOut);
            } catch (SocketTimeoutException ignore) {
                // Ignore for now
            }

            try {
                tempData = -2;
                tempData = clientIn.read();
                // Did the client close the connection?
                if (tempData == -1) {
                    clientLogs.addVerboseLog("HTTP Connection closed by the client");
                    keepConnection = false;
                    return;
                }
                // Client has a reply continue with the persistent connection
                break;
            } catch (SocketTimeoutException ex) {
                // Allow for longer wait for next connection
                clientSocket.setSoTimeout(clientSocket.getSoTimeout() * 3);
                serverSocket.setSoTimeout(serverSocket.getSoTimeout() * 3);
            }
        }
        clientSocket.setSoTimeout(SERVER_TIMEOUT);
        serverSocket.setSoTimeout(SERVER_TIMEOUT);
    }

    private void sendAllDataToClient(URL url, String cacheHeader) throws IOException {
        String responseHeader;
        if (cacheHeader != null) {
            responseHeader = cacheHeader;
        } else {
            try {
                responseHeader = readHeaderFromServer();
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new BadGatewayException("Header limit exceeded by server", e);
            }
        }

        int firstSpace = responseHeader.indexOf(' ');
        try {
            responseCode = Integer.parseInt(responseHeader.substring(firstSpace + 1, firstSpace + 4));
        } catch (NumberFormatException e) {
            responseCode = -1;
        }
        int secondLine = responseHeader.indexOf('\r') + 2;
        String cacheDate = canCache(new MimeHeader(responseHeader.substring(secondLine)));
        clientLogs.addVerboseLog("Can cache: " + cacheDate);

        FileOutputStream cacheFile = null;
        File cacheLocation = null;
        if (cacheDate != null) {
            try {
                cacheLocation = storage.getCacheInput(url);
                if (cacheLocation != null) {
                    cacheFile = new FileOutputStream(cacheLocation);
                }
            } catch (IOException e) {
                // Disable cache saving due to IO error
                cacheDate = null;
            }
        }

        clientOut.writeBytes(responseHeader);
        if (cacheDate != null && cacheFile != null) {
            cacheFile.write(responseHeader.getBytes());
        }

        int tryAttempt = 0;
        try {
            for (tryAttempt = 0; tryAttempt < 4; tryAttempt++) {
                try {
                    if (cacheDate != null && cacheFile != null) {
                        byte [] buffer = new byte[8192];
                        int read;
                        while((read = serverIn.read(buffer, 0, 8192)) >= 0) {
                            clientOut.write(buffer, 0, read);

                            if (cacheFile != null) {
                                try {
                                    // Separate catch for the cache since there can a disk related error here
                                    cacheFile.write(buffer, 0, read);
                                } catch (IOException ex) {
                                    // Cache file broken, delete it and do not try to cache the data again
                                    try {
                                        cacheFile.close();
                                    } catch (IOException ignore) {
                                        // ignore any exception while deleting since we will delete it anyway
                                    }
                                    // Delete the file if not successful then log it
                                    if (cacheLocation.exists() && !cacheLocation.delete()) {
                                        clientLogs.addVerboseLog("Failed to delete the cache file " + cacheLocation);
                                    }
                                    storage.removeBrokenCache(url);
                                    cacheFile = null;
                                }
                            }
                        }
                        break;
                    } else {
                        serverIn.transferTo(clientOut);
                    }
                } catch (SocketTimeoutException ignore) {
                    // A possible delay on the network but since the client will not have a reply here
                    // it will time out bellow and the loop will try to read from the server again
                }

                try {
                    tempData = -2; // Reset temp data flag
                    tempData = clientIn.read();
                    // Did the client close the connection?
                    if (tempData == -1) {
                        clientLogs.addVerboseLog("HTTP Connection closed by the client");
                        keepConnection = false;
                        return;
                    }
                    // Client has a reply continue with the persistent connection
                    break;
                } catch (SocketTimeoutException ex) {
                    // Allow for longer wait for next connection
                    clientSocket.setSoTimeout(clientSocket.getSoTimeout() * 3);
                    serverSocket.setSoTimeout(serverSocket.getSoTimeout() * 3);
                }
            }
        } finally {
            // Save cache if it is open
            if (cacheFile != null) {
                cacheFile.flush();
                cacheFile.close();

                // Timeout detected possibly broken cache
                if (tryAttempt >= 4) {
                    if (cacheLocation.exists() && !cacheLocation.delete()) {
                        clientLogs.addVerboseLog("Failed to delete broken cache file " + cacheLocation);
                    }
                    storage.removeBrokenCache(url);
                } else {
                    storage.saveCacheIndex(url, cacheDate);
                }
            }

            // Reset timeout values back to the default
            clientSocket.setSoTimeout(SERVER_TIMEOUT);
            serverSocket.setSoTimeout(SERVER_TIMEOUT);
        }
    }

    private String canCache(MimeHeader parameters) {
        // Can we cache the content?
        return parameters.get("Last-Modified");
    }

    private void handleHead(String header, URL url) throws IOException {
        Object[] cacheResult = storage.isCached(url);
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
                clientLogs.addVerboseLog("Checking if the cache is valid");
                serverOut.writeBytes(request);
                headerSent = true;

                cacheResponse = readHeaderFromServer();
                // Can use the website in the cache?
                if (cacheResponse.substring(0, 3).equalsIgnoreCase("304")) {
                    clientLogs.addVerboseLog("Cached website found for " + url.getHost() + url.getPath());
                    FileInputStream cache = (FileInputStream) cacheResult[0];
                    cache.transferTo(clientOut);
                    cache.close();
                    return;
                } else {
                    clientLogs.addVerboseLog("Cache requires update!");
                }
            }
        }

        if (!headerSent) {
            serverOut.writeBytes(header);
        }

        clientLogs.addVerboseLog("Sent HEAD request to server");
        sendAllDataToClient(url, cacheResponse);
    }

    private void handleGet(String header, URL url) throws IOException {
        Object[] cacheResult = storage.isCached(url);
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
                clientLogs.addVerboseLog("Asking if the cache is valid");
                serverOut.writeBytes(request);
                headerSent = true;
                try {
                    cacheResponse = readHeaderFromServer();
                } catch (SocketTimeoutException ex) { // Slow server

                }
                if (cacheResponse == null) {
                    throw new IOException("Reading response from the server failed!");
                }
                // Can use the website in the cache?
                int firstStatus = cacheResponse.indexOf(" ") + 1;
                if (cacheResponse.substring(firstStatus, firstStatus + 3).equalsIgnoreCase("304")) {
                    clientLogs.addVerboseLog("Cached website found for " + url.getHost() + url.getPath());
                    FileInputStream cache = (FileInputStream) cacheResult[0];
                    cache.transferTo(clientOut);
                    cache.close();
                    return;
                } else {
                    clientLogs.addVerboseLog("Cache requires update!");
                }
            }
        }

        if (!headerSent) {
            serverOut.writeBytes(header);
        }

        clientLogs.addVerboseLog("Sent GET to Web Server:");
        sendAllDataToClient(url, cacheResponse);
    }

    private void handlePost(String header) throws IOException {
        serverOut.writeBytes(header);
        clientSocket.setSoTimeout(300);
        try {
            long readBytes = clientIn.transferTo(serverOut);
            if (readBytes == -1) {
                keepConnection = false;
            }
        } catch (SocketTimeoutException ignore) {
            // Ignore since all data transfer should have finished
        }
        clientSocket.setSoTimeout(SERVER_TIMEOUT);

        clientLogs.addVerboseLog("Sent POST to Web Server:");

        String responseHeader = readHeaderFromServer();
        int firstSpace = responseHeader.indexOf(' ');
        try {
            responseCode = Integer.parseInt(responseHeader.substring(firstSpace + 1, firstSpace + 4));
        } catch (NumberFormatException e) {
            responseCode = -1;
        }
        clientOut.writeBytes(responseHeader);
        sendAllDataToClient();
    }

    private void handleOptions(String header) throws IOException {
        serverOut.writeBytes(header);
        clientLogs.addVerboseLog("Sent OPTIONS request to server");

        String responseHeader = readHeaderFromServer();
        int firstSpace = responseHeader.indexOf(' ');
        try {
            responseCode = Integer.parseInt(responseHeader.substring(firstSpace + 1, firstSpace + 4));
        } catch (NumberFormatException e) {
            responseCode = -1;
        }
        clientOut.writeBytes(responseHeader);
        sendAllDataToClient();
    }

    @Override
    protected String readHeaderFromClient() throws IOException, ArrayIndexOutOfBoundsException {
        // Apache header limit is 8KB, so I also use this limit as well
        byte[] headerArr = new byte[8192];
        int temp;
        int i = 0;
        if (tempData != -2) {
            headerArr[i++] = (byte) tempData;
            tempData = -2;
        }

        int tryAttempt;
        for (tryAttempt = 0; tryAttempt < 4; tryAttempt++) {
            try {
                do {
                    // readByte throws IOException instead of writing -1 to array so, I use this one
                    temp = clientIn.read();
                    if (temp == -1) {
                        throw new SocketException("Client Disconnected");
                    }
                    headerArr[i++] = (byte) temp;
                } while (headerArr[i - 1] != '\n' || headerArr[i - 2] != '\r'
                        || headerArr[i - 3] != '\n' || headerArr[i - 4] != '\r');
                clientSocket.setSoTimeout(SERVER_TIMEOUT);
                break;
            } catch (SocketTimeoutException ex) {
                // Slow client
                int timeout = clientSocket.getSoTimeout();
                clientSocket.setSoTimeout(timeout * 3);
            }
        }

        if (tryAttempt >= 4) {
            // Very slow client timeout the connection
            throw new SocketException("Client timeout");
        }

        return new String(headerArr, 0, i);
    }

    private String readHeaderFromServer() throws IOException, ArrayIndexOutOfBoundsException{
        byte[] headerArr = new byte[8192];
        int i = 0;
        int temp;

        int tryAttempt;
        for (tryAttempt = 0; tryAttempt < 4; tryAttempt++) {
            try {
                do {
                    // readByte throws IOException instead of writing -1 to array so, I use this one
                    temp = serverIn.read();
                    if (temp == -1) {
                        throw new SocketException("Client Disconnected");
                    }
                    headerArr[i++] = (byte) temp;
                } while (headerArr[i - 1] != '\n' || headerArr[i - 2] != '\r'
                        || headerArr[i - 3] != '\n' || headerArr[i - 4] != '\r');
                serverSocket.setSoTimeout(SERVER_TIMEOUT);
                break;
            } catch (SocketTimeoutException e) {
                // Slow server
                int timeout = serverSocket.getSoTimeout();
                serverSocket.setSoTimeout(timeout * 3);
            }
        }

        if (tryAttempt >= 4) {
            // Very slow server timeout the connection
            throw new SocketException("Server timeout");
        }

        return new String(headerArr, 0, i);
    }

    private void sendErrorToClient(String response) {
        try {
            clientOut.writeBytes(response);
        } catch (IOException ignore) {
            // Failed to send error to the client ignore
        }
    }

    private void error400() {
        String html = "<html><body><h1>400 Bad Request</h1></body></html>\r\n";
        String response = "HTTP/1.1 400 Bad Request\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error401() {
        String html = "<html><body><h1>401 Unauthorized</h1></body></html>\r\n";
        String response = "HTTP/1.1 401 Unauthorized\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error405() {
        String html = "<html><body><h1>405 Method Not Allowed</h1></body></html>\r\n";
        String response = "HTTP/1.1 405 Method Not Allowed\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error414() {
        String html = "<html><body><h1>413 Entity Too Large</h1></body></html>\r\n";
        String response = "HTTP/1.1 413 Content Too Large\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error500() {
        String html = "<html><body><h1>500 Internal Server Error</h1></body></html>\r\n";
        String response = "HTTP/1.1 500 Internal Server Error\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }

    private void error502() {
        String html = "<html><body><h1>502 Bad Gateway</h1></body></html>\r\n";
        String response = "HTTP/1.1 502 Bad Gateway\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: CSE471 Proxy\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        sendErrorToClient(response);
    }
}