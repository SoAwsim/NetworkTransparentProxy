package HTTPProxy;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerHandler implements Runnable {
    private Socket conSock;
    private DataInputStream dIS;
    private DataOutputStream dOS;

    public ServerHandler(Socket c) {
        conSock = c;

        try {
            dIS = new DataInputStream(conSock.getInputStream());
            dOS = new DataOutputStream(conSock.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void run() {
        String header = null;
        try {
            header = readHeader(dIS);
            if (header == null) { // This should not happen but is there just in case
                throw new ArrayIndexOutOfBoundsException();
            }
        }
        catch (ArrayIndexOutOfBoundsException e) { // Invalid header size return 413
            String html = "<html><body><h1>Entity Too Large</h1></body></html>";
            String response = "HTTP/1.1 413 Content Too Large\r\n"
                    + "Date: " + new Date() + "\r\n"
                    + "Server: CSE471 Proxy\r\n"
                    + "Content-Length: " + html.length() + "\r\n"
                    + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
            try {
                dOS.writeBytes(response);
                dOS.close();
            } catch (IOException ex) {
                e.printStackTrace();
            }
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

        // TODO fix this, should return 40x on invalid URL
        URL url = null;
        try {
            url = new URL(fullpath);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        String domain = url.getHost();
        String shortpath = url.getPath();

        System.out.println(domain);
        System.out.println(shortpath);

        if (method.equalsIgnoreCase("get")) {
            try {
                if (domain.equals("www.facebook.com")) {
                    String html = "<html><body><h1>" + domain
                            + " is not allowed!</h1></body></html>";
                    String response = "HTTP/1.1 401 Not Authorized\r\n"
                            + "Date: " + new Date() + "\r\n"
                            + "Server: CSE471 Proxy\r\n"
                            + "Content-Length: " + html.length() + "\r\n"
                            + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;

                    dOS.writeBytes(response);
                    dOS.close();
                } else {
                    handleGet(domain, fullpath, mH);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (method.equalsIgnoreCase("post")) {
            try {
                handlePost(domain, shortpath, mH);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (method.equalsIgnoreCase("head")) {
            try {
                handleHead(domain, shortpath, mH);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            System.out.println("other methods");
        }

    }

    public void handleHead(String domain, String shortpath, MimeHeader mH) throws Exception {
        var req = "HEAD " + shortpath + " HTTP/1.1\r\n" + mH;

        var sock = new Socket(domain, 80);

        var d_in = new DataInputStream(sock.getInputStream());
        var d_out = new DataOutputStream(sock.getOutputStream());

        d_out.writeBytes(req);
        System.out.println("Sent HEAD request to server");
        System.out.println(req);

        var response = readHeader(d_in);

        //System.out.println(response);

        sock.close();
        dOS.writeBytes(response);

        conSock.close();
    }

    public void handleGet(String domain, String fullpath, MimeHeader mH) throws Exception {

        String constructedRequest = "GET " + fullpath + " HTTP/1.1\r\n" + mH;

        Socket proxiedSock = new Socket(domain, 80);

        DataInputStream dIS1 = new DataInputStream(proxiedSock.getInputStream());
        DataOutputStream dOS1 = new DataOutputStream(proxiedSock.getOutputStream());

        dOS1.writeBytes(constructedRequest);
        System.out.println("Sent GET to Web Server:");
        System.out.println(constructedRequest);

        String responseHeader = readHeader(dIS1);

        byte[] data;
        int contlengthnum = 100000;
        int contlengthnumindex1 = responseHeader.indexOf("Content-Length: ");
        if (contlengthnumindex1 == -1) {
            int transferTypeIndex = responseHeader.indexOf("Transfer-Encoding: ");
            data = readChunked(dIS1);
        }
        else {
            int contlengthnumindex2 = responseHeader.indexOf('\r', contlengthnumindex1);
            contlengthnum = Integer.parseInt(
                    responseHeader.substring(contlengthnumindex1, contlengthnumindex2));
            data = new byte[contlengthnum];
        }

        /*int i = 0;
        String a = null;
        while (true) {
            // TODO implement chunked data!! check for number the \r\n
            data[i++] = (byte) dIS1.read();
            a = new String(data, 0, i);
            if (i == 30000) {
                break;
            }
        }*/
        //dIS1.readFully(data);

        proxiedSock.close();

        dOS.writeBytes(responseHeader);
        dOS.write(data);

        conSock.close();

    }

    public void handlePost(String domain, String shortpath, MimeHeader mH) throws Exception {

        int postSize = Integer.parseInt(mH.get("Content-Length"));

        byte[] postData = new byte[postSize];

        dIS.readFully(postData);

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

        dOS.writeBytes(responseHeader);
        dOS.write(data);

        conSock.close();

    }

    public String readHeader(DataInputStream dIS) throws ArrayIndexOutOfBoundsException {
        // Apache header limit is 8KB so I also use this limit as well
        byte[] headerArr = new byte[8192];
        int i = 0;

        while (true) {
            try {
                headerArr[i++] = (byte) dIS.read();
                if (headerArr[i - 1] == '\n' && headerArr[i - 2] == '\r'
                        && headerArr[i - 3] == '\n' && headerArr[i - 4] == '\r') {
                    break;
                }

            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new String(headerArr, 0, i);
    }

    private byte[] readChunked(DataInputStream dataIn) throws IOException {
        byte[] tempSizeStorage = new byte[512];
        int sizeIndex = 0;
        int totalSize = 0;
        int currentChunkSize = 0;
        byte[] currentChunk = null;
        List<byte[]> httpContent = new ArrayList<byte[]>();

        while (true) {
            try {
                byte currentByte = (byte) dataIn.read();
                if (currentByte == ';' && currentChunk == null) {
                    String hexSize = null;
                    try {
                        hexSize = new String (tempSizeStorage, 0, sizeIndex);
                        currentChunkSize = Integer.parseInt(hexSize, 16) + 2;
                        // Are we in the last segment?
                        /*if (currentChunkSize == 2) {
                            size = 512; // For other optional parameters after the content
                        }*/
                        currentChunk = new byte[currentChunkSize];
                    }
                    catch (IndexOutOfBoundsException e) {
                        throw new IOException("Invalid Chunk Size");
                    }
                    tempSizeStorage[sizeIndex++] = currentByte;
                    totalSize++;
                }

                // Did we reach end of chunk metadata?
                if (currentByte == '\n' && tempSizeStorage[sizeIndex - 1] == '\r') {
                    // Do we need to allocate buffer for content?
                    if (currentChunk == null) {
                        String hexSize = null;
                        try {
                            hexSize = new String(tempSizeStorage, 0, sizeIndex - 1);
                            currentChunkSize= Integer.parseInt(hexSize, 16) + 2;
                            currentChunk = new byte[currentChunkSize];
                        } catch (IndexOutOfBoundsException e) {
                            throw new IOException("Invalid Chunk Size");
                        }
                    }

                    // Are we in the final segment?
                    if (currentChunkSize == 2) {
                        tempSizeStorage[sizeIndex++] = currentByte;
                        totalSize++;
                        currentByte = (byte) dataIn.read();
                        byte nextByte = (byte) dataIn.read();
                        // Did we reach content end?
                        if (currentByte == '\r' && nextByte == '\n') {
                            tempSizeStorage[sizeIndex++] = currentByte;
                            tempSizeStorage[sizeIndex++] = nextByte;
                            totalSize += 2;
                            httpContent.add(tempSizeStorage);
                            break;
                        }
                    }
                    tempSizeStorage[sizeIndex++] = currentByte;
                    totalSize++;
                    httpContent.add(tempSizeStorage);
                    tempSizeStorage = new byte[512];
                    sizeIndex = 0;

                    // Read chunk
                    for (int i = 0; i < currentChunkSize; i++) {
                        currentChunk[i] = (byte) dataIn.read();
                        totalSize++;
                    }
                    httpContent.add(currentChunk);
                    currentChunk = null;
                    continue;
                }
                tempSizeStorage[sizeIndex++] = currentByte;
                totalSize++;
            }
            catch (IndexOutOfBoundsException e) {
                throw new IOException("Chunk Size Buffer Limit Exceeded");
            }
            catch (NumberFormatException e) {
                throw new IOException("Invalid Chunk Size");
            }
        }

        //totalSize = 0;
        /*for (byte[] element : httpContent) {
            totalSize += element.length;
        }*/
        byte[] final_data = new byte[totalSize];

        int i = 0;

        for (byte[] element : httpContent) {
            for (byte b : element) {
                if (b == 0) {
                    break;
                };
                final_data[i++] = b;
            }
        }
        return final_data;
    }
}