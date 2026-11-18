package org.proyecto;

import org.proyecto.ui.VentanaLote;

import javax.swing.*;

public class Main {
    static void main(String[] args) {
        // Que la ventana use el aspecto del sistema (Windows) en vez del de Java
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) {
            // si falla, se queda el aspecto por defecto
        }
        SwingUtilities.invokeLater(() -> new VentanaLote().setVisible(true));
    }
}
