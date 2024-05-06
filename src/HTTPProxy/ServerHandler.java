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

        String header = readHeader(dIS);

        int fsp = header.indexOf(' ');
        int ssp = header.indexOf(' ', fsp + 1);
        int secondline = header.indexOf('\r') + 2;

        String method = header.substring(0, fsp); // GET http://example.com/path HTTP/1.1
        System.out.println("Log: " + method);
        String fullpath = header.substring(fsp + 1, ssp);
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
                    handleGet(domain, shortpath, mH);
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

        System.out.println(response);

        sock.close();
        dOS.writeBytes(response);

        conSock.close();
    }

    public void handleGet(String domain, String shortpath, MimeHeader mH) throws Exception {

        String constructedRequest = "GET " + shortpath + " HTTP/1.1\r\n" + mH;

        Socket proxiedSock = new Socket(domain, 80);

        DataInputStream dIS1 = new DataInputStream(proxiedSock.getInputStream());
        DataOutputStream dOS1 = new DataOutputStream(proxiedSock.getOutputStream());

        dOS1.writeBytes(constructedRequest);
        System.out.println("Sent GET to Web Server:");
        System.out.println(constructedRequest);

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

    public String readHeader(DataInputStream dIS) {
        byte[] headerArr = new byte[15000];
        int i = 0;

        while (true) {
            try {
                headerArr[i++] = (byte) dIS.read();
                if (headerArr[i - 1] == '\n' && headerArr[i - 2] == '\r'
                        && headerArr[i - 3] == '\n' && headerArr[i - 4] == '\r') {
                    break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new String(headerArr, 0, i);
    }

}