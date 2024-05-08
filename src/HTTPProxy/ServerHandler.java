package HTTPProxy;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerHandler implements Runnable {
    private final Socket conSock;
    private final DataInputStream clientIn;
    private final DataOutputStream clientOut;

    // Throw IOException to upper level since this Runnable should not execute
    public ServerHandler(Socket c) throws IOException {
        conSock = c;
        clientIn = new DataInputStream(conSock.getInputStream());
        clientOut = new DataOutputStream(conSock.getOutputStream());
    }

    @Override
    public void run() {
        String header;
        try {
            header = readHeader(clientIn);
            if (header == null) {
                throw new IOException("Header Reading Failed");
            }
        }
        catch (ArrayIndexOutOfBoundsException e) { // Invalid header size return 413
            error414();
            return;
        }
        catch (IOException e) {
            error500();
            return;
        }

        int methodFinIndex = header.indexOf(' ');
        int pathFinIndex = header.indexOf(' ', methodFinIndex + 1);
        int secondline = header.indexOf('\r') + 2;

        String method = header.substring(0, methodFinIndex); // GET http://example.com/path HTTP/1.1
        System.out.println("Log: " + method);
        String fullpath = header.substring(methodFinIndex + 1, pathFinIndex);
        System.out.println("Log: " + fullpath);
        String restHeader = header.substring(secondline);

        MimeHeader mH = new MimeHeader(restHeader);

        URL url;
        try {
            url = new URL(fullpath);
        } catch (MalformedURLException e) {
            error400();
            return;
        }

        String domain = url.getHost();
        String shortpath = url.getPath();

        if (method.equalsIgnoreCase("get")) {
            try {
                handleGet(domain, fullpath, mH);
            } catch (IOException ex) {

                throw new RuntimeException(ex);
            }
        }
        else if (method.equalsIgnoreCase("post")) {
            try {
                handlePost(domain, shortpath, mH);
            } catch (IOException ex) {
                error500();
                throw new RuntimeException(ex);
            }
        }
        else if (method.equalsIgnoreCase("head")) {
            try {
                handleHead(domain, shortpath, mH);
            } catch (IOException ex) {
                error500();
                throw new RuntimeException(ex);
            }
        }
        else {
            error405();
        }
    }

    public void handleHead(String domain, String shortpath, MimeHeader mH) throws IOException {
        var req = "HEAD " + shortpath + " HTTP/1.1\r\n" + mH;

        var sock = new Socket(domain, 80);

        var d_in = new DataInputStream(sock.getInputStream());
        var d_out = new DataOutputStream(sock.getOutputStream());

        d_out.writeBytes(req);
        System.out.println("Sent HEAD request to server");
        System.out.println(req);

        var response = readHeader(d_in);

        sock.close();
        clientOut.writeBytes(response);

        conSock.close();
    }

    public void handleGet(String domain, String fullpath, MimeHeader mH) throws IOException {

        String constructedRequest = "GET " + fullpath + " HTTP/1.1\r\n" + mH;

        Socket proxiedSock = new Socket(domain, 80);

        DataInputStream serverIn = new DataInputStream(proxiedSock.getInputStream());
        DataOutputStream serverOut = new DataOutputStream(proxiedSock.getOutputStream());

        serverOut.writeBytes(constructedRequest);
        System.out.println("Sent GET to Web Server:");
        System.out.println(constructedRequest);

        String responseHeader = readHeader(serverIn);

        byte[] data;
        int contentLengthStart = responseHeader.indexOf("Content-Length: ");
        if (contentLengthStart == -1) { // Transfer encoding detected
            data = readChunked(serverIn);
        }
        else {
            int contentLengthEnd = responseHeader.indexOf('\r', contentLengthStart);
            int contentSize = Integer.parseInt(responseHeader.substring(contentLengthStart + 16, contentLengthEnd));
            data = new byte[contentSize];
            serverIn.readFully(data);
        }
        proxiedSock.close();

        clientOut.writeBytes(responseHeader);
        clientOut.write(data);

        conSock.close();
    }

    public void handlePost(String domain, String shortpath, MimeHeader mH) throws IOException {

        int postSize = Integer.parseInt(mH.get("Content-Length"));

        byte[] postData = new byte[postSize];

        clientIn.readFully(postData);

        String constructedRequest = "POST " + shortpath + " HTTP/1.1\r\n" + mH;

        Socket proxiedSock = new Socket(domain, 80);

        DataInputStream dIS1 = new DataInputStream(proxiedSock.getInputStream());
        DataOutputStream dOS1 = new DataOutputStream(proxiedSock.getOutputStream());

        dOS1.writeBytes(constructedRequest);
        dOS1.write(postData);

        System.out.println("Sent POST to Web Server:");
        System.out.println(constructedRequest);
        System.out.println("Sent POST data " + postSize + " bytes.");

        String responseHeader = readHeader(dIS1);

        int contlengthnumindex1 = responseHeader.indexOf("Content-Length: ") + 16;
        int contlengthnumindex2 = responseHeader.indexOf('\r', contlengthnumindex1);
        int contlengthnum = Integer.parseInt(
                responseHeader.substring(contlengthnumindex1, contlengthnumindex2));

        byte[] data = new byte[contlengthnum];

        dIS1.readFully(data);

        proxiedSock.close();

        clientOut.writeBytes(responseHeader);
        clientOut.write(data);

        conSock.close();
    }

    public String readHeader(DataInputStream dIS) throws IOException, ArrayIndexOutOfBoundsException {
        // Apache header limit is 8KB so I also use this limit as well
        byte[] headerArr = new byte[8192];
        int i = 0;
        do {
            headerArr[i++] = (byte) dIS.read();
        } while (headerArr[i - 1] != '\n' || headerArr[i - 2] != '\r'
                || headerArr[i - 3] != '\n' || headerArr[i - 4] != '\r');

        return new String(headerArr, 0, i);
    }

    private byte[] readChunked(DataInputStream serverIn) throws IOException {
        // 1KB limit for each chunk metadata
        byte[] tempSizeStorage = new byte[1024];
        int sizeIndex = 0;
        int totalSize = 0;
        int currentChunkSize = 0;
        List<byte[]> httpContent = new ArrayList<>();

        while (true) {
            try {
                //byte currentByte = serverIn.readByte();
                tempSizeStorage[sizeIndex++] = serverIn.readByte();
                totalSize++;
                // Detect size with optional parameters
                if (tempSizeStorage[sizeIndex - 1] == ';' && currentChunkSize == 0) {
                    String hexSize;
                    try {
                        hexSize = new String (tempSizeStorage, 0, sizeIndex - 1);
                        currentChunkSize = Integer.parseInt(hexSize, 16) + 2;
                    }
                    catch (IndexOutOfBoundsException e) {
                        throw new IOException("Invalid Chunk Size");
                    }
                }

                // Did we reach end of chunk metadata?
                if (tempSizeStorage[sizeIndex - 1] == '\n' && tempSizeStorage[sizeIndex - 2] == '\r') {
                    // Do we need to calculate buffer size?
                    if (currentChunkSize == 0) {
                        String hexSize;
                        try {
                            hexSize = new String(tempSizeStorage, 0, sizeIndex - 2);
                            currentChunkSize= Integer.parseInt(hexSize, 16) + 2;
                        } catch (IndexOutOfBoundsException e) {
                            throw new IOException("Invalid Chunk Size");
                        }
                    }

                    // Are we in the final segment?
                    if (currentChunkSize == 2) {
                        // Consume rest of the arguments
                        do {
                            tempSizeStorage[sizeIndex++] = serverIn.readByte();
                            totalSize++;
                        } while (tempSizeStorage[sizeIndex - 1] != '\n' || tempSizeStorage[sizeIndex - 2] != '\r'
                                || tempSizeStorage[sizeIndex - 3] != '\n' || tempSizeStorage[sizeIndex - 4] != '\r');
                        httpContent.add(tempSizeStorage);
                        break;
                    }

                    httpContent.add(tempSizeStorage);
                    tempSizeStorage = new byte[1024];
                    sizeIndex = 0;

                    // Read chunk
                    byte[] currentChunk = serverIn.readNBytes(currentChunkSize);
                    totalSize += currentChunkSize;
                    currentChunkSize = 0;
                    httpContent.add(currentChunk);
                }
            }
            catch (IndexOutOfBoundsException e) {
                throw new IOException("Chunk Size Buffer Limit Exceeded");
            }
            catch (NumberFormatException e) {
                throw new IOException("Invalid Chunk Size");
            }
        }

        byte[] final_data = new byte[totalSize];

        int i = 0;
        for (byte[] element : httpContent) {
            for (byte b : element) {
                if (b == 0) {
                    break;
                }
                final_data[i++] = b;
            }
        }

        return final_data;
    }

    private void sendErrorToClient(String response) {
        try {
            clientOut.writeBytes(response);
            clientOut.close();
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
}