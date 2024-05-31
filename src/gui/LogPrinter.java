package gui;

import proxy.utils.Logger;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class LogPrinter extends SwingWorker<Object, String> {
    private final Logger logger = Logger.getLogger();
    private final JTextArea logArea;

    public LogPrinter(JTextArea logArea) {
        this.logArea = logArea;
    }

    @Override
    protected Object doInBackground() {
        LinkedBlockingQueue<String> logQueue = logger.getLogQueue();
        String currentLog;
        try {
            while (true) {
                currentLog = logQueue.take();
                publish(currentLog);
                System.out.println(currentLog);
                if (isCancelled()) {
                    return null;
                }
            }
        } catch (InterruptedException e) {
            return null;
        }
    }

    @Override
    protected void process(List<String> chunks) {
        for (String logMsg : chunks) {
            logArea.append(logMsg + "\n");
        }
    }
}
