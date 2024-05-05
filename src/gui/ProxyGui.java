package gui;

import HTTPProxy.ProxyServer;

import javax.swing.*;
import java.awt.*;

public class ProxyGui {
    private JLabel proxyStatus;
    private Thread serverThread;

    public ProxyGui() {
        JFrame mainWindow = new JFrame("Transparent Proxy");
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
                serverThread = new ProxyServer();
            }
            if (!serverThread.isAlive()) {
                serverThread.start();
            }
            proxyStatus.setText("Proxy Server is Running...");
        });

        JMenuItem stopProxy = new JMenuItem("Stop");
        stopProxy.addActionListener(e -> {
            /* TODO implement proxy stop logic*/
            serverThread.interrupt();
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
}
