package proxy.HTTPSProxy;

import proxy.utils.ProxyStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SSLProxy implements Runnable {
    private ServerSocket serverSocket;
    private final ProxyStorage storage;

    public SSLProxy (ProxyStorage storage) throws IOException {
        this.storage = storage;
        this.initSock();
    }

    @Override
    public void run() {
        ExecutorService executorThreads = Executors.newFixedThreadPool(10);
        try {
            while (true) {
                Socket sslSocket = serverSocket.accept();
                try {
                    executorThreads.execute(new SSLHandler(sslSocket, storage));
                }
                catch (IOException ex) {
                    // TODO handle this better
                    System.out.println("Client connection failed");
                }
            }
        }
        catch (SocketException se) {
            System.out.println("Socket closed shutting down server");
        }
        catch (IOException e) {
            System.out.println("IO error in HTTPs server");
        }
        finally {
            executorThreads.shutdown(); // Stop accepting new jobs
            try {
                // Wait for termination
                if (!executorThreads.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    executorThreads.shutdownNow();
                }
            }
            catch (InterruptedException e) { // If interrupted anyway force shutdown
                executorThreads.shutdownNow();
            }
            finally {
                System.out.println("All threads stopped executing");
            }
        }
    }

    public void initSock() {
        if (serverSocket == null) {
            try {
                serverSocket = new ServerSocket(443);
            } catch (IOException e) {
                System.out.println("Failed to create socket");
            }
        }
    }

    public void closeSock() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                System.out.println("Failed to close socket!");
            }
        }
    }
}
