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
        ExecutorService executorThreads = Executors.newFixedThreadPool(SERVER_THREADS);
        try {
            while (serverOn) {
                Socket clientConnection = serverListener.accept();
                try {
                    executorThreads.execute(new HTTPHandler(clientConnection));
                }
                catch (IOException ex) {
                    logger.addVerboseLog("Client failed to connect to the HTTP proxy!");
                }
            }
        }
        catch (SocketException se) {
            logger.addVerboseLog("HTTP Proxy shutting down");
        }
        catch (IOException e) {
            logger.addVerboseLog("IOException in HTTP proxy exiting...");
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
                logger.addVerboseLog("HTTP Proxy stopped executing");
            }
        }
    }
}