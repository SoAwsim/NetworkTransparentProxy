package proxy.utils;

import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Logger {
    private static Logger loggerInstance;
    private static final Object instanceLock = new Object();

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-HH-dd HH:mm:ss");

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> clientReports = new ConcurrentHashMap<>();

    public final Object broadcaster = new Object();
    private final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();

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
        Date currentDate = new Date();
        String log = dateFormat.format(currentDate) + ", IP: " + clientIP.getHostAddress() + ", Domain: " + domain.getHost() +
                ", Resource path: " + domain.getPath() + ", Method: " + method + ", Response: " + responseCode;
        clientQueue.add(log);
        logQueue.add(log);
        synchronized (broadcaster) {
            notifyAll();
        }
    }

    public void addLog(InetAddress clientIP, String host) {
        var clientQueue = clientReports.putIfAbsent(clientIP.getHostAddress(), new ConcurrentLinkedQueue<>());
        if (clientQueue == null) {
            clientQueue = clientReports.get(clientIP.getHostAddress());
        }
        Date currentDate = new Date();
        String log = dateFormat.format(currentDate) + ", IP: " + clientIP.getHostAddress() + ", Domain: " + host + ", Connection: HTTPS";
        clientQueue.add(log);
        logQueue.add(log);
        synchronized (broadcaster) {
            notifyAll();
        }
    }

    public void addVerboseLog(String message) {
        Date currentDate = new Date();
        String log = dateFormat.format(currentDate) + ", " + message;
        logQueue.add(log);
        synchronized (broadcaster) {
            notifyAll();
        }
    }

    public ConcurrentLinkedQueue<String> getLogQueue() {
        return logQueue;
    }

    public String[] getClientLog(String clientIP) {
        var clientQueue = clientReports.get(clientIP);
        if (clientQueue == null) {
            return null;
        }
        return clientQueue.toArray(String[]::new);
    }
}
