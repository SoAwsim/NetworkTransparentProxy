package logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Logger {
    private static Logger loggerInstance;
    private static final Object instanceLock = new Object();

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> clientReports = new ConcurrentHashMap<>();

    public static Logger getLogger() {
        Logger obj = loggerInstance;
        if (obj == null) {
            synchronized (instanceLock) {
                // While a thread was waiting for this lock another thread might have initialized it
                obj = loggerInstance;
                if (obj == null) {
                    obj = new Logger();
                    loggerInstance = obj;
                }
            }
        }
        return obj;
    }

    private Logger() {
    }

    public void addLog(InetAddress clientIP, URL domain, String method, String responseCode) {
        var clientQueue = clientReports.putIfAbsent(clientIP.getHostAddress(), new ConcurrentLinkedQueue<>());
        if (clientQueue == null) {
            clientQueue = clientReports.get(clientIP.getHostAddress());
        }
        String log = "IP: " + clientIP.getHostAddress() + ", Domain: " + domain.getHost() +
                ", Resource path: " + domain.getPath() + ", Method: " + method + ", Response: " + responseCode;
        clientQueue.add(log);
    }
}
