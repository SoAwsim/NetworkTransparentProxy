package HTTPSProxy;

import HTTPProxy.ProxyStorage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class SSLHandler implements Runnable {
    private final Socket clientSock;
    private final DataInputStream clientIn;
    private final DataOutputStream clientOut;

    private final ProxyStorage storage;

    private static final int SERVER_TIMEOUT = 1000;
    public SSLHandler (Socket socket, ProxyStorage storage) throws IOException {
        clientSock = socket;
        this.storage = storage;
        clientSock.setSoTimeout(SERVER_TIMEOUT);
        clientIn = new DataInputStream(clientSock.getInputStream());
        clientOut = new DataOutputStream(clientSock.getOutputStream());
    }

    @Override
    public void run() {

    }

    private InetAddress readSNI() throws IOException {
        byte[] handshakeBuffer = new byte[8192];
        byte[] hostname;
        InetAddress address = null;
        int index = 0;
        index = clientIn.read(handshakeBuffer, 0, 44);
        int sessionIdLength = handshakeBuffer[index - 1];
        index += clientIn.read(handshakeBuffer, index, sessionIdLength+2);
        int cipherSuiteLength = ((handshakeBuffer[index - 2] & 0xff) << 8) | (handshakeBuffer[index - 1] & 0xff);
        index += clientIn.read(handshakeBuffer, index, cipherSuiteLength+1);
        int compressionMethodLength = handshakeBuffer[index - 1];
        index += clientIn.read(handshakeBuffer, index, compressionMethodLength+2);
        int currentExtensionLength = 0;
        index += clientIn.read(handshakeBuffer, index, 9);
        currentExtensionLength = ((handshakeBuffer[index - 2] & 0xff) << 8) | (handshakeBuffer[index - 1] & 0xff);
        hostname = new byte[currentExtensionLength];
        clientIn.read(hostname, 0, currentExtensionLength);
        String a = new String(hostname);
        System.out.println(a);
        address = InetAddress.getByName(a);
        for (byte b: hostname) {
            handshakeBuffer[index++] = b;
        }
        return address;
    }
}
