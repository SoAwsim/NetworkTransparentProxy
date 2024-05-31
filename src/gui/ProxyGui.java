package gui;

import proxy.HTTPProxy.PlainProxy;
import proxy.HTTPSProxy.SSLProxy;
import proxy.utils.Logger;
import proxy.utils.ProxyStorage;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ProxyGui {
    private final JFrame mainWindow; // The main window of the application

    private final JFrame blockedWindow; // The window for showing the blocked hosts

    private Thread serverPlainThread; // HTTP proxy thread
    private Thread serverSSLThread; // HTTPS proxy thread
    private PlainProxy httpProxy; // HTTP proxy runnable
    private SSLProxy httpsProxy; // HTTPS proxy runnable

    private final ProxyStorage storage; // Storage for proxy configuration
    private final Logger clientLogs; // Class for the logging component

    private final DefaultTableModel blockedTableModel;
    private final LogPrinter logWorker;

    public ProxyGui() {
        try { // Initialize the storage component
            storage = ProxyStorage.getStorage();
        } catch (IOException ex) {
            // Critical error cannot recover from this
            throw new RuntimeException(ex);
        }

        clientLogs = Logger.getLogger(); // Initialize the logger

        mainWindow = new JFrame("Transparent Proxy");
        mainWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        WindowListener exitListener = new WindowAdapter() { // Close operation
            @Override
            public void windowClosing(WindowEvent e) {
                exitProxy();
            }
        };
        mainWindow.addWindowListener(exitListener);
        mainWindow.setSize(800, 450);
        mainWindow.setResizable(true);

        // Blocked domains window
        blockedWindow = new JFrame("Blocked Hosts");
        blockedWindow.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        blockedWindow.setSize(600, 500);
        blockedWindow.setResizable(true);
        blockedWindow.setLayout(new GridBagLayout());

        String[] cname = {"IP", "Hostname"};
        blockedTableModel = new DefaultTableModel(cname, 0);

        GridBagConstraints gc = new GridBagConstraints();
        JTable blockedTable = new JTable(blockedTableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JScrollPane sp = new JScrollPane(blockedTable);
        gc.fill = GridBagConstraints.BOTH;
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weighty = 1.0;
        gc.weightx = 1.0;
        blockedWindow.add(sp, gc);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BorderLayout());
        JButton tableButton = new JButton("Unblock Selected Hosts");
        tableButton.addActionListener(e -> {
            int[] selectedRows = blockedTable.getSelectedRows();
            InetAddress[] addrArray = new InetAddress[selectedRows.length];
            int index = 0;
            for (int row: selectedRows) {
                try {
                    addrArray[index] = InetAddress.getByName(blockedTable.getModel().getValueAt(row, 0).toString().trim());
                } catch (UnknownHostException ex) {
                    clientLogs.addVerboseLog("Unknown host log at row " + row);
                    continue;
                }
                index++;
            }
            try {
                storage.unblockHosts(addrArray);
            } catch (IOException ex) {
                clientLogs.addVerboseLog("Unblocking the selected hosts failed!");
            }
            refreshBlockedList();
        });
        buttonPanel.add(tableButton, BorderLayout.CENTER);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.weighty = 0.0;
        gc.weightx = 1.0;
        blockedWindow.add(buttonPanel, gc);

        // Menu bar for the UI
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenu helpMenu = new JMenu("Help");

        JMenuItem startProxy = new JMenuItem("Start");
        startProxy.addActionListener(e -> {
            if (serverPlainThread == null) { // Did we initialize the server thread?
                try {
                    httpProxy = new PlainProxy(80);
                    httpsProxy = new SSLProxy(443);
                } catch (IOException ex) {
                    clientLogs.addVerboseLog("Failed to start the transparent proxy");
                    JOptionPane.showMessageDialog(
                            mainWindow,
                            ex.toString(),
                            "IO Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                serverPlainThread = new Thread(httpProxy);
                serverSSLThread = new Thread(httpsProxy);
                serverPlainThread.start();
                serverSSLThread.start();
                clientLogs.addVerboseLog("Transparent proxy server started");
            } else if (!serverPlainThread.isAlive()) { // Is the server thread running?
                serverPlainThread = new Thread(httpProxy);
                serverSSLThread = new Thread(httpsProxy);
                try {
                    httpProxy.initSock();
                    httpsProxy.initSock();
                } catch (IOException ex) {
                    clientLogs.addVerboseLog("Failed to restart transparent proxy");
                    JOptionPane.showMessageDialog(
                            mainWindow,
                            ex.toString(),
                            "IO Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                serverPlainThread.start();
                serverSSLThread.start();
            } else { // Server already running
                JOptionPane.showMessageDialog(
                        mainWindow,
                        "Server already running",
                        "Info",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });

        JMenuItem stopButton = new JMenuItem("Stop");
        stopButton.addActionListener(e -> stopProxy(false));

        JMenuItem createReport = new JMenuItem("Report");
        createReport.addActionListener(e -> {
            do {
                String client = JOptionPane.showInputDialog("Enter Client IP address");
                if (client == null) { // User selected cancel
                    break;
                } else if (client.isEmpty()) { // Empty ip
                    JOptionPane.showMessageDialog(
                            mainWindow,
                            "Please enter a valid client",
                            "Address Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    continue;
                }
                ConcurrentLinkedQueue<String> logs = clientLogs.getClientLog(client);
                if (logs == null) {
                    JOptionPane.showMessageDialog(
                            mainWindow,
                            "Client Not Found",
                            "Address Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    continue;
                }
                JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
                int result = fileChooser.showSaveDialog(mainWindow);
                if (result == JFileChooser.APPROVE_OPTION) { // User selected a valid file
                    ReportCreator saveToText = new ReportCreator(fileChooser.getSelectedFile(), logs, mainWindow);
                    saveToText.execute();
                }
                break;
            } while (true);
        });

        JMenuItem filterHost = new JMenuItem("Add host to filter");
        filterHost.addActionListener(e -> {
            do {
                String address = JOptionPane.showInputDialog(
                        mainWindow,
                        "Enter IP address or hostname to block: "
                );
                if (address == null) { // User selected cancel
                    break;
                }
                try {
                    if (address.isEmpty()) { // User entered empty ip
                        throw new UnknownHostException();
                    }
                    storage.blockAddress(address);
                } catch (UnknownHostException ex) {
                    JOptionPane.showMessageDialog(
                            mainWindow,
                            "Hostname or IP address is not valid",
                            "Address Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    continue;
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(
                            mainWindow,
                            "IO error during writing to disk",
                            "IO Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    break;
                }
                JOptionPane.showMessageDialog(
                        mainWindow,
                        "Hostname is blocked",
                        "Confirmation",
                        JOptionPane.INFORMATION_MESSAGE
                );
                break;
            } while (true);
        });

        JMenuItem displayFilter = new JMenuItem("Display current filtered host");
        displayFilter.addActionListener(e -> {
            refreshBlockedList();
            blockedWindow.setVisible(!blockedWindow.isVisible());
        });

        JMenuItem exitApp = new JMenuItem("Exit");
        exitApp.addActionListener(e -> exitProxy());

        fileMenu.add(startProxy);
        fileMenu.add(stopButton);
        fileMenu.add(createReport);
        fileMenu.add(filterHost);
        fileMenu.add(displayFilter);
        fileMenu.add(exitApp);

        JMenuItem aboutMenuItem = new JMenuItem("About");
        aboutMenuItem.addActionListener(e -> JOptionPane.showMessageDialog(
                mainWindow,
                "Name Surname: Oğuzhan İçelliler\nSchool Number: 20200702042\nEmail: oguzhan.icelliler@std.yeditepe.edu.tr",
                "Developer Information",
                JOptionPane.INFORMATION_MESSAGE
        ));

        helpMenu.add(aboutMenuItem);
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        mainWindow.setJMenuBar(menuBar);

        // Log window
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollLog = new JScrollPane(
                logArea,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
        );

        mainWindow.add(scrollLog);
        mainWindow.setVisible(true);

        // Start log worker to print the logs
        logWorker = new LogPrinter(logArea);
        logWorker.execute();
    }

    // Method to stop the proxy server
    private void stopProxy(boolean suppressWindow) {
        try {
            // Did we initialize the proxy before?
            if (httpProxy == null || httpsProxy == null) {
                throw new ProxyAlreadyClosedException();
            }
            httpProxy.closeSocket();
            httpsProxy.closeSocket();
        } catch (ProxyAlreadyClosedException ex) {
            if (!suppressWindow) {
                JOptionPane.showMessageDialog(mainWindow,
                        "Proxy server already closed!",
                        "Information",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        } catch (IOException ex) {
            clientLogs.addVerboseLog(ex.toString());
            JOptionPane.showMessageDialog(
                    mainWindow,
                    ex.toString(),
                    "IO Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // Exit the proxy application
    private void exitProxy() {
        int selection = JOptionPane.showConfirmDialog(
                mainWindow,
                "Do you want to exit?",
                "Exit Confirmation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (selection == 0) { // User selected YES
            stopProxy(true);
            logWorker.cancel(true);
            System.exit(0);
        }
    }

    // Not the most efficient thing but works fine for this project
    private void refreshBlockedList() {
        blockedTableModel.setRowCount(0);
        var allBlocked = storage.getAllBlocked();
        for (var element: allBlocked) {
            Object[] row = {element.getKey(), element.getValue()};
            blockedTableModel.addRow(row);
        }
    }
}
