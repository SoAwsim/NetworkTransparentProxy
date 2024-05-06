package gui;

import HTTPProxy.ProxyServer;

import javax.swing.*;
import java.awt.*;

public class ProxyGui implements ErrorDisplay {
    private final JFrame mainWindow;
    private JLabel proxyStatus;
    private Thread serverThread;
    private ProxyServer httpProxy;

    public ProxyGui() {
        mainWindow = new JFrame("Transparent Proxy");
        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWindow.setSize(800, 450);
        mainWindow.setResizable(true);
        mainWindow.setLayout(new GridBagLayout());

        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenu helpMenu = new JMenu("Help");

        JMenuItem startProxy = new JMenuItem("Start");
        startProxy.addActionListener(e -> {
            /* TODO implement proxy start logic*/
            if (serverThread == null) {
                httpProxy = new ProxyServer(this);
                serverThread = new Thread(httpProxy);
                serverThread.start();
                proxyStatus.setText("Proxy Server is Running...");
            }
            else if (!serverThread.isAlive()) {
                serverThread = new Thread(httpProxy);
                httpProxy.initSock();
                serverThread.start();
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
            proxyStatus.setText("Proxy Server is Stopped...");
        });

        JMenuItem createReport = new JMenuItem("Report");
        createReport.addActionListener(e -> {
            /* TODO implement logging component*/
        });

        JMenuItem filterHost = new JMenuItem("Add host to filter");
        filterHost.addActionListener(e -> {
            /* TODO implement filtering logic*/
        });

        JMenuItem displayFilter = new JMenuItem("Display current filtered host");
        displayFilter.addActionListener(e -> {
            /* TODO implement filtering logic*/
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
                JOptionPane.INFORMATION_MESSAGE)
        );

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
