package org.proyecto.DTB_IMAGENES;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Prueba independiente de la interfaz Swing.
 * Lee la cadena JDBC del archivo LOCAL config/db.properties (ignorado por Git).
 */
public class ConexionSQL {
    public static void main(String[] args) {
        Path ruta = Path.of("config", "db.properties");
        Properties propiedades = new Properties();

        if (!Files.isRegularFile(ruta)) {
            System.err.println("Falta " + ruta.toAbsolutePath());
            System.err.println("Copia config/db.properties.example a config/db.properties y ajusta el servidor SQL Server.");
            return;
        }

        try (Reader lector = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
            propiedades.load(lector);
            String url = propiedades.getProperty("db.url", "").trim();
            if (url.isEmpty()) {
                System.err.println("La propiedad db.url esta vacia en config/db.properties");
                return;
            }

            try (Connection conexion = DriverManager.getConnection(url)) {
                System.out.println("Conectado");
                System.out.println("Conexion a catalogo: " + conexion.getCatalog());
            }
        } catch (IOException | SQLException ex) {
            System.err.println("No se pudo conectar a SQL Server: " + ex.getMessage());
        }
    }
}
