package gui;

import javax.swing.*;

public class ProxyGui {
    private final JFrame mainWindow;

    public ProxyGui() {
        this.mainWindow = new JFrame("Transparent Proxy");
        this.mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.mainWindow.setSize(800, 450);
        this.mainWindow.setResizable(true);

        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenu helpMenu = new JMenu("Help");

        JMenuItem startProxy = new JMenuItem("Start");
        startProxy.addActionListener(e -> {/* TODO */});

        JMenuItem stopProxy = new JMenuItem("Stop");
        stopProxy.addActionListener(e -> {/* TODO */});

        JMenuItem createReport = new JMenuItem("Report");
        createReport.addActionListener(e -> {/* TODO */});

        JMenuItem filterHost = new JMenuItem("Add host to filter");
        filterHost.addActionListener(e -> {/* TODO */});

        JMenuItem displayFilter = new JMenuItem("Display current filtered host");
        displayFilter.addActionListener(e -> {/* TODO */});

        JMenuItem exitApp = new JMenuItem("Exit");
        exitApp.addActionListener(e -> {/* TODO */});

        fileMenu.add(stopProxy);
        fileMenu.add(stopProxy);
        fileMenu.add(createReport);
        fileMenu.add(filterHost);
        fileMenu.add(displayFilter);
        fileMenu.add(exitApp);

        JMenuItem aboutMenuItem = new JMenuItem("About");
        aboutMenuItem.addActionListener(e -> JOptionPane.showMessageDialog(
                this.mainWindow,
                "Name Surname: Oğuzhan İçelliler\nSchool Number: 20200702042\nEmail: oguzhan.icelliler@std.yeditepe.edu.tr",
                "Developer Information",
                JOptionPane.INFORMATION_MESSAGE)
        );

        helpMenu.add(aboutMenuItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);

        this.mainWindow.setJMenuBar(menuBar);
        this.mainWindow.setVisible(true);
    }
}
