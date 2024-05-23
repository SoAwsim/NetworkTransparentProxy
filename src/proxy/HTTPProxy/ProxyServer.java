package proxy.HTTPProxy;

import gui.ErrorDisplay;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// curl --proxy localhost:8080 --head http://www.google.com

public class ProxyServer implements Runnable {
    private ServerSocket ServerSock;
    private final ErrorDisplay edManager;

    public ProxyServer(ErrorDisplay ed) {
        this.edManager = ed;
        this.initSock();
    }
    @Override
    public void run() {
        ExecutorService executorThreads = Executors.newFixedThreadPool(10);
        try {
            while (true) {
                Socket conSock = ServerSock.accept();
                try {
                    executorThreads.execute(new HTTPHandler(conSock));
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
            // TODO Handle this better
            edManager.showExceptionWindow(e);
        }
        finally {
            // Oracle recommended way of shutting down the executor service
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
        if (ServerSock == null) {
            try {
                ServerSock = new ServerSocket(80);
            } catch (IOException e) {
                edManager.showExceptionWindow(e);
            }
        }
    }

    public void closeSock() {
        if (ServerSock != null) {
            try {
                ServerSock.close();
                ServerSock = null;
            } catch (IOException e) {
                edManager.showExceptionWindow(e);
            }
        }
    }
}