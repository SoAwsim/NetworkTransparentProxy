package proxy.HTTPSProxy;

import proxy.AbstractProxyListener;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SSLProxy extends AbstractProxyListener {
    public SSLProxy (int port) throws IOException {
        super(port);
    }

    @Override
    public void run() {
        ExecutorService executorThreads = Executors.newFixedThreadPool(SERVER_THREADS);
        try {
            while (serverOn) {
                Socket clientConnection = serverListener.accept();
                try {
                    executorThreads.execute(new SSLHandler(clientConnection));
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
}
