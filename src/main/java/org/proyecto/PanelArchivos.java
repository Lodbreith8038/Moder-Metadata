package org.proyecto;

import javax.swing.*;
import java.awt.*;

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





    }


}
