package org.proyecto;
import javax.swing.*;

public class Main extends JFrame {
    static void main(String[] args){
        SwingUtilities.invokeLater(() ->
                new PanelArchivos().setVisible(true)
        );

    }
}
