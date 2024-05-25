package gui;

import proxy.utils.Logger;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LogPrinter extends SwingWorker<Object, String> {
    private final Logger logger = Logger.getLogger();
    private final JTextArea logArea;

    public LogPrinter(JTextArea logArea) {
        this.logArea = logArea;
    }

    @Override
    protected Object doInBackground() {
        ConcurrentLinkedQueue<String> logQueue = logger.getLogQueue();
        while (!isCancelled()) {
            String currentLog;
            while ((currentLog = logQueue.poll()) != null) {
                publish(currentLog);
                System.out.println(currentLog);
            }
        }
        return null;
    }

    @Override
    protected void process(List<String> chunks) {
        for (String logMsg : chunks) {
            logArea.append(logMsg + "\n");
        }
    }
}
