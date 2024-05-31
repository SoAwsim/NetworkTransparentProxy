package gui;

import javax.swing.*;
import java.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReportCreator extends SwingWorker<Integer, Object> {
    private final File selectedFile;
    private final ConcurrentLinkedQueue<String> clientLogs;
    private final JFrame mainWindow;

    public ReportCreator (File selectedFile, ConcurrentLinkedQueue<String> clientLogs, JFrame mainWindow) {
        this.selectedFile = selectedFile;
        this.clientLogs = clientLogs;
        this.mainWindow = mainWindow;
    }

    @Override
    protected Integer doInBackground() {
        try (FileOutputStream txtOut = new FileOutputStream(selectedFile);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(txtOut))) {
            for (String s : clientLogs) {
                writer.write(s);
                writer.newLine();
            }
        } catch (IOException ex) {
            return 1;
        }
        return 0;
    }

    @Override
    protected void done() {
        int result;
        try {
            result = get();
            if (result == 0) {
                JOptionPane.showMessageDialog(
                        mainWindow,
                        "File saved",
                        "Confirmation",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    mainWindow,
                    "Report saving failed!",
                    "IO Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
