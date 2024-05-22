package gui;

import proxy.HTTPProxy.ProxyServer;
import proxy.utils.ProxyStorage;
import proxy.HTTPSProxy.SSLProxy;
import proxy.utils.Logger;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ProxyGui implements ErrorDisplay {
    private final JFrame mainWindow;
    private JLabel proxyStatus;

    private final JFrame blockedWindow;

    private Thread serverThread;
    private Thread serverSSLThread;
    private ProxyServer httpProxy;
    private SSLProxy httpsProxy;
    private ProxyStorage storage;
    private final Logger clientLogs;

    private final DefaultTableModel blockedTableModel;

    public ProxyGui() {
        try {
            storage = ProxyStorage.getStorage();
        } catch (IOException e) {
            System.out.println("IO error occurred while getting saved data");
            storage = null;
        }

        clientLogs = Logger.getLogger();

        mainWindow = new JFrame("Transparent Proxy");
        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWindow.setSize(800, 450);
        mainWindow.setResizable(true);
        mainWindow.setLayout(new GridBagLayout());

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
                    System.out.println("Unknown host at row " + row + "!");
                    continue;
                }
                index++;
            }
            try {
                storage.unblockHosts(addrArray);
            } catch (IOException ex) {
                // todo handle this better
                System.out.println("Unlocking failed!");
            }
            refreshBlockedList();
        });
        buttonPanel.add(tableButton, BorderLayout.CENTER);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.weighty = 0.0;
        gc.weightx = 1.0;
        blockedWindow.add(buttonPanel, gc);

        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenu helpMenu = new JMenu("Help");

        JMenuItem startProxy = new JMenuItem("Start");
        startProxy.addActionListener(e -> {
            /* TODO implement proxy start logic*/
            if (serverThread == null) {
                httpProxy = new ProxyServer(this, storage);
                try {
                    httpsProxy = new SSLProxy(storage);
                } catch (IOException ex) {
                    System.out.println("Failed to create HTTPS proxy");
                }
                serverThread = new Thread(httpProxy);
                serverSSLThread = new Thread(httpsProxy);
                serverThread.start();
                serverSSLThread.start();
                proxyStatus.setText("Proxy Server is Running...");
            }
            else if (!serverThread.isAlive()) {
                serverThread = new Thread(httpProxy);
                serverSSLThread = new Thread(httpsProxy);
                httpProxy.initSock();
                httpsProxy.initSock();
                serverThread.start();
                serverSSLThread.start();
                proxyStatus.setText("Proxy Server is Running...");
            }
            else {
                JOptionPane.showMessageDialog(mainWindow,
                        "Server already running",
                        "Info",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });

        JMenuItem stopProxy = new JMenuItem("Stop");
        stopProxy.addActionListener(e -> {
            /* TODO implement proxy stop logic*/
            httpProxy.closeSock();
            httpsProxy.closeSock();
            proxyStatus.setText("Proxy Server is Stopped...");
        });

        JMenuItem createReport = new JMenuItem("Report");
        createReport.addActionListener(e -> {
            do {
                String client = JOptionPane.showInputDialog("Enter Client IP address");
                if (client == null || client.isBlank()) {
                    break;
                }
                String[] logs = clientLogs.getClientLog(client);
                if (logs == null) {
                    JOptionPane.showMessageDialog(mainWindow, "Client Not Found", "Address Error", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
                int result = fileChooser.showSaveDialog(mainWindow);
                if (result == JFileChooser.APPROVE_OPTION) {
                    SwingWorker<Integer, Object> saveToText = new SwingWorker<>() {
                        @Override
                        protected Integer doInBackground() {
                            try (FileOutputStream txtOut = new FileOutputStream(fileChooser.getSelectedFile());
                                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(txtOut))) {
                                for (String s : logs) {
                                    writer.write(s);
                                    writer.newLine();
                                }
                            } catch (IOException e) {
                                return 1;
                            }
                            return 0;
                        }

                        @Override
                        protected void done() {
                            int result;
                            try {
                                result = get();
                            } catch (Exception e) {
                                JOptionPane.showMessageDialog(mainWindow, "Report saving failed!", "IO Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            if (result == 0) {
                                JOptionPane.showMessageDialog(mainWindow, "File saved", "Confirmation", JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(mainWindow, "Report saving failed!", "IO Error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
                    saveToText.execute();
                }

                break;
            } while (true);
        });

        JMenuItem filterHost = new JMenuItem("Add host to filter");
        filterHost.addActionListener(e -> {
            if (storage != null) {
                do {
                    String address = JOptionPane.showInputDialog(mainWindow, "Enter IP address or hostname to block: ");
                    if (address == null || address.isBlank()) {
                        break;
                    }
                    try {
                        storage.blockAddress(address);
                    } catch (UnknownHostException ex) {
                        JOptionPane.showMessageDialog(mainWindow, "Hostname or IP address is not valid", "Address Error", JOptionPane.ERROR_MESSAGE);
                        continue;
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(mainWindow, "IO error during writing to disk", "IO Error", JOptionPane.ERROR_MESSAGE);
                        break;
                    }
                    JOptionPane.showMessageDialog(mainWindow, "Hostname is blocked", "Confirmation", JOptionPane.INFORMATION_MESSAGE);
                    break;
                } while (true);
            } else {
                JOptionPane.showMessageDialog(mainWindow, "Cannot read config data of proxy server!", "IO Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem displayFilter = new JMenuItem("Display current filtered host");
        displayFilter.addActionListener(e -> {
            refreshBlockedList();
            blockedWindow.setVisible(!blockedWindow.isVisible());
        });

        JMenuItem exitApp = new JMenuItem("Exit");
        exitApp.addActionListener(e -> {
            /* TODO implement proxy stop logic */
            System.exit(0);
        });

        fileMenu.add(startProxy);
        fileMenu.add(stopProxy);
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

        JPanel panel = new JPanel();
        proxyStatus = new JLabel("Proxy Server is Stopped...");

        panel.add(proxyStatus);
        mainWindow.add(panel);
        mainWindow.setVisible(true);
    }

    private void refreshBlockedList() {
        blockedTableModel.setRowCount(0);
        var allBlocked = storage.getAllBlocked();
        for (var element: allBlocked) {
            Object[] row = {element.getKey(), element.getValue()};
            blockedTableModel.addRow(row);
        }
    }

    @Override
    public void showExceptionWindow(Exception e) {
        // Are we the EDT thread?
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showExceptionWindow(e));
            return;
        }
        // If we are the EDT thread show error dialog
        JOptionPane.showMessageDialog(
                mainWindow,
                e.getMessage(),
                e.toString(),
                JOptionPane.ERROR_MESSAGE
        );
    }
}
