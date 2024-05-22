package proxy.HTTPSProxy;

import proxy.AbstractProxyHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;

public final class SSLHandler extends AbstractProxyHandler {
    // A buffer to store possible SNI Client Hello
    private final byte[] sharedBuffer = new byte[102400];
    int bufferIndex = 0;

    public SSLHandler (Socket socket) throws IOException {
        super(socket);
        SERVER_TIMEOUT = 300;
    }

    @Override
    public void run() {
        InetAddress hostAddr = null;
        boolean connectReq = false;
        try {
            do {
                // Reset buffer
                bufferIndex = 0;
                // If the client connected with the connect method no need to parse the contents again just relay information
                if (!connectReq) {
                    bufferIndex += clientIn.read(sharedBuffer, 0, 7);
                    if (bufferIndex == -1) {
                        return;
                    }
                    // Check if CONNECT
                    try {
                        if (
                                sharedBuffer[0] == 'C' && sharedBuffer[1] == 'O' && sharedBuffer[2] == 'N' && sharedBuffer[3] == 'N'
                                        && sharedBuffer[4] == 'E' && sharedBuffer[5] == 'C' && sharedBuffer[6] == 'T'
                        ) {
                            String header = readHeader();
                            int firstSpace = header.indexOf(' ');
                            int portDiv = header.indexOf(':');
                            hostAddr = InetAddress.getByName(header.substring(firstSpace + 1, portDiv));
                            connectReq = true;
                        } else {
                            // Send rest of the data to the server
                            String strHost = readSNI();
                            if (strHost != null) {
                                hostAddr = InetAddress.getByName(strHost);
                            }
                        }
                    } catch (UnknownHostException ignore) {
                        // Ignore for now
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    // Reset this flag since client may want to initiate another connection in the future
                    connectReq = false;
                }

                if (hostAddr == null) {
                    // Host address not found! Drop the connection
                    return;
                }

                if (storage.isBlocked(hostAddr)) {
                    // Drop the HTTPS connection since domain is blocked
                    return;
                }

                if (serverSocket == null) {
                    serverSocket = new Socket(hostAddr, 443);
                    serverSocket.setSoTimeout(SERVER_TIMEOUT);
                    serverIn = new DataInputStream(serverSocket.getInputStream());
                    serverOut = new DataOutputStream(serverSocket.getOutputStream());
                } else if (serverSocket.getInetAddress() != hostAddr) {
                    serverSocket.close();
                    serverSocket = new Socket(hostAddr, 80);
                    serverSocket.setSoTimeout(SERVER_TIMEOUT);
                    serverIn = new DataInputStream(serverSocket.getInputStream());
                    serverOut = new DataOutputStream(serverSocket.getOutputStream());
                }

                // Return 200 OK to the client
                if (connectReq) {
                    clientOut.writeBytes("HTTP/1.1 200 OK\r\n\r\n");
                    continue;
                }

                // Transfer buffered data to the server
                if (bufferIndex != 0) {
                    serverOut.write(sharedBuffer, 0, bufferIndex);
                }

                try {
                    clientIn.transferTo(serverOut);
                } catch (SocketTimeoutException ignore) {

                }

                // Transfer the response back to the client
                try {
                    serverIn.transferTo(clientOut);
                } catch (SocketTimeoutException ignore) {

                }
                clientLogs.addLog(clientSock.getInetAddress(), hostAddr.getHostName());
            } while (true);
        } catch (SocketTimeoutException ignore) {
            // Close the connection
        } catch (SocketException ignore) {
            // Closed from remote ignore
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("Closing HTTPS connection");
            try {
                clientSock.close();
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error while closing HTTPS socket");
            }
        }
    }

    protected String readHeader() throws IOException, ArrayIndexOutOfBoundsException {
        int temp;
        do {
            // readByte throws IOException instead of writing -1 to array so, I use this one
            temp = clientIn.read();
            if (temp == -1) {
                throw new SocketTimeoutException("Client Disconnected");
            }
            sharedBuffer[bufferIndex++] = (byte) temp;
        } while (sharedBuffer[bufferIndex - 1] != '\n' || sharedBuffer[bufferIndex - 2] != '\r'
                || sharedBuffer[bufferIndex - 3] != '\n' || sharedBuffer[bufferIndex - 4] != '\r');

        return new String(sharedBuffer, 0, bufferIndex);
    }

    private String readSNI() throws IOException {
        if (bufferIndex == 0) {
            bufferIndex += clientIn.read(sharedBuffer, bufferIndex, 1);
            if (bufferIndex == 0) {
                return null;
            }

            if (sharedBuffer[bufferIndex - 1] != (byte) 0x16) {
                return null;
            }

            bufferIndex += clientIn.read(sharedBuffer, bufferIndex, 5);

            if (sharedBuffer[bufferIndex - 1] != (byte) 0x01) {
                return null;
            }
            bufferIndex += clientIn.read(sharedBuffer, bufferIndex, 38);
        } else {
            if (sharedBuffer[0] != (byte) 0x16 || sharedBuffer[5] != (byte) 0x01) {
                return null;
            }
            bufferIndex += clientIn.read(sharedBuffer, bufferIndex, 37);
        }
        // Variable to store the current fields length
        int currentLength;

        // Session ID 1 Byte
        currentLength = sharedBuffer[bufferIndex - 1];
        bufferIndex += clientIn.read(sharedBuffer, bufferIndex, currentLength + 2);

        // Cipher Suite Length 2 Bytes
        currentLength = ((sharedBuffer[bufferIndex - 2] & 0xff) << 8) | (sharedBuffer[bufferIndex - 1] & 0xff);
        bufferIndex += clientIn.read(sharedBuffer, bufferIndex, currentLength + 1);

        // Compression Method Length 1 Byte
        currentLength = sharedBuffer[bufferIndex - 1];
        bufferIndex += clientIn.read(sharedBuffer, bufferIndex, currentLength + 2);

        // Extension field total length
        currentLength = ((sharedBuffer[bufferIndex - 2] & 0xff) << 8) | (sharedBuffer[bufferIndex - 1] & 0xff);
        boolean nameFound = false;
        for (int readSize = 0, extensionLength; readSize < currentLength; ) {
            // Read extension type and length
            bufferIndex += clientIn.read(sharedBuffer, bufferIndex, 4);
            readSize += 4;
            // Is this the server name extension?
            if (sharedBuffer[bufferIndex - 3] == (byte) 0x00 && sharedBuffer[bufferIndex - 4] == (byte) 0x00) {
                // Read server name length
                bufferIndex += clientIn.read(sharedBuffer, bufferIndex, 5);
                nameFound = true;
                break;
            } else {
                extensionLength = ((sharedBuffer[bufferIndex - 2] & 0xff) << 8) | (sharedBuffer[bufferIndex - 1] & 0xff);
                bufferIndex += clientIn.read(sharedBuffer, bufferIndex, extensionLength);
            }
        }

        if (!nameFound) {
            return null;
        }

        // Hostname length
        currentLength = ((sharedBuffer[bufferIndex - 2] & 0xff) << 8) | (sharedBuffer[bufferIndex - 1] & 0xff);
        bufferIndex += clientIn.read(sharedBuffer, bufferIndex, currentLength);
        String hostname = new String(sharedBuffer, bufferIndex-currentLength, currentLength);
        System.out.println(hostname);
        return hostname;
    }
}
