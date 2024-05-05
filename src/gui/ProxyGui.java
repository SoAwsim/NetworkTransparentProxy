package gui;

import javax.swing.*;

public class ProxyGui {
    private JFrame mainWindow;

    public ProxyGui() {
        this.mainWindow = new JFrame("Transparent Proxy");
        this.mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.mainWindow.setSize(800, 450);
        this.mainWindow.setResizable(true);
        this.mainWindow.setVisible(true);
    }
}
