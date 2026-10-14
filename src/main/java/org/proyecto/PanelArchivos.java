package org.proyecto;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.Map;

public class PanelArchivos extends JFrame {
    public PanelArchivos(){
        setTitle("Ajuste Metadata");
        setSize(500,500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        //colores del panel
        panel.setBackground(new Color(240,247,250));
        //Creacion del panel dentro de la ventada
        setContentPane(panel);
        //division de paneles
        panel.setLayout(new BorderLayout());


        //panel contenedor
        JPanel contenedor = new JPanel();
        contenedor.setLayout(new BorderLayout(10,10));
        contenedor.setBackground(new Color(245, 247, 250));
        contenedor.setBorder(
                BorderFactory.createEmptyBorder(55,70,55,70)
        );

        panel.add(contenedor,BorderLayout.CENTER);

        //paneld e zona de arrastre
        JPanel ZNarrastre = new JPanel();
        ZNarrastre.setLayout(new BorderLayout());
        ZNarrastre.setBackground(Color.WHITE);
        ZNarrastre.setBorder(
                BorderFactory.createDashedBorder(
                        Color.RED,
                        2f,
                        8f,
                        6f,
                        true
                )
        );

        contenedor.add(ZNarrastre,BorderLayout.CENTER);

        //mensaje del panel
        JLabel mensaje = new JLabel("Arrastra tus imagenes aqui");
        mensaje.setHorizontalAlignment(JLabel.CENTER);
        mensaje.setFont(new Font("Arial", Font.BOLD, 20));
        ZNarrastre.add(mensaje, BorderLayout.CENTER);
        //boton del panel
        JButton boton = new JButton("Seleccione su archivo");
        JPanel botonPanel = new JPanel();
        botonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        botonPanel.setBorder(
                BorderFactory.createEmptyBorder(0,10,30,10)
        );
        botonPanel.setOpaque(false);
        botonPanel.add(boton);
        ZNarrastre.add(botonPanel,BorderLayout.SOUTH);
        
        // ===== el botón abre el selector de archivos =====
        boton.addActionListener(e -> {
            JFileChooser selector = new JFileChooser();
            selector.setMultiSelectionEnabled(true);
            if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                for (File f : selector.getSelectedFiles()) mostrarMetadata(f);
            }
        });

        // ===== permitir arrastrar archivos a la ventana =====
        TransferHandler recibirArchivos = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport soporte) {
                // Solo acepta si lo que se arrastra son archivos
                return soporte.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport soporte) {
                if (!canImport(soporte)) return false;
                try {
                    List<File> archivos = (List<File>) soporte.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : archivos) mostrarMetadata(f);
                    return true;
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PanelArchivos.this,
                            "No se pudo leer el archivo: " + ex.getMessage());
                    return false;
                }
            }
        };
        // Se pone en la zona, en el texto y en toda la ventana,
        // para que funcione suelte donde suelte el usuario.
        ZNarrastre.setTransferHandler(recibirArchivos);
        mensaje.setTransferHandler(recibirArchivos);
        panel.setTransferHandler(recibirArchivos);
    }

    // analiza el archivo y muestra el resultado =====
    private void mostrarMetadata(File archivo) {
        try {
            Map<String, String> datos = AnalizadorPng.analizar(archivo);

            // Convertimos los datos en texto, una línea por dato
            StringBuilder texto = new StringBuilder();
            for (Map.Entry<String, String> d : datos.entrySet()) {
                texto.append(d.getKey()).append(": ").append(d.getValue()).append("\n");
            }

            JTextArea area = new JTextArea(texto.toString(), 15, 45);
            area.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(area),
                    "Metadata de " + archivo.getName(), JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo analizar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
