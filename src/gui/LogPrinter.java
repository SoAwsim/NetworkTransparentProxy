package gui;

import proxy.utils.Logger;

import javax.swing.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LogPrinter extends SwingWorker<Object, String> {
    private final Logger logger = Logger.getLogger();
    @Override
    protected Object doInBackground() throws Exception {
        ConcurrentLinkedQueue<String> logQueue = logger.getLogQueue();
        while (!isCancelled()) {
            String currentLog;
            while ((currentLog = logQueue.poll()) != null) {
                System.out.println(currentLog);
            }
            synchronized (logger.broadcaster) {
                logger.broadcaster.wait();
            }
        }
        return null;
    }
}
