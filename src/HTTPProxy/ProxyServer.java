package HTTPProxy;

import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// curl --proxy localhost:8080 --head http://www.google.com

public class ProxyServer extends Thread {

    @Override
    public void run() {
        try {
            ServerSocket s = new ServerSocket(8080);
            while (true) {
                Socket conSock = s.accept();
                new ServerHandler(conSock).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}