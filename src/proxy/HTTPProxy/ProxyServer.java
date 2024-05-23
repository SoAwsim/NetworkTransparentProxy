package proxy.HTTPProxy;

import proxy.AbstractProxyListener;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProxyServer extends AbstractProxyListener {
    public ProxyServer(int port) throws IOException {
        super(port);
    }

    @Override
    public void run() {
        ExecutorService executorThreads = Executors.newFixedThreadPool(10);
        try {
            while (serverOn) {
                Socket clientConnection = serverListener.accept();
                try {
                    executorThreads.execute(new HTTPHandler(clientConnection));
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
            System.out.println("IO exception in HTTP proxy");
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
}