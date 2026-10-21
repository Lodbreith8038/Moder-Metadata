package org.proyecto;
import javax.swing.*;

public class Main {
    static void main(String[] args){
        SwingUtilities.invokeLater(() ->
                new PanelArchivos().setVisible(true)
        );

    }
}
