package bundle;
import bundle.installer.BundleInstaller;

import javax.swing.*;

public class App {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
            JFrame.setDefaultLookAndFeelDecorated(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        BundleInstaller INSTALLER = new BundleInstaller();
        INSTALLER.openUI();
    }
}

