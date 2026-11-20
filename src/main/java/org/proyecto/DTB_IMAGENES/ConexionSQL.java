package org.proyecto.DTB_IMAGENES;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Abre la conexión con SQL Server (MonsterASP o una instancia local).
 *
 * Los datos salen del archivo LOCAL config/db.properties, que git ignora:
 *   db.url        -> dirección del servidor y nombre de la base
 *   db.usuario    -> login (vacío = seguridad integrada de Windows, solo en local)
 *   db.contrasena -> contraseña
 * Así la contraseña nunca queda escrita en el código ni se sube al repositorio.
 *
 * Quien use esta clase debe estar preparado para que falle (sin internet,
 * servidor caído...): el programa sigue funcionando y solo avisa.
 */
public final class ConexionSQL {

    private static final Path RUTA_CONFIG = Path.of("config", "db.properties");

    private ConexionSQL() {
    }

    /** Abre una conexión nueva. Hay que cerrarla con try-with-resources. */
    public static Connection conectar() throws SQLException {
        Properties p = leerConfiguracion();
        String url = p.getProperty("db.url", "").trim();
        String usuario = p.getProperty("db.usuario", "").trim();
        String contrasena = p.getProperty("db.contrasena", "");

        if (url.isEmpty()) {
            throw new SQLException("Falta db.url en " + RUTA_CONFIG);
        }
        // Sin usuario: seguridad integrada (SQL Server local). Con usuario: login de SQL Server (MonsterASP).
        return usuario.isEmpty()
                ? DriverManager.getConnection(url)
                : DriverManager.getConnection(url, usuario, contrasena);
    }

    private static Properties leerConfiguracion() throws SQLException {
        if (!Files.isRegularFile(RUTA_CONFIG)) {
            throw new SQLException("No existe " + RUTA_CONFIG.toAbsolutePath()
                    + ". Copia config/db.properties.example a config/db.properties y complétalo.");
        }
        Properties p = new Properties();
        try (Reader lector = Files.newBufferedReader(RUTA_CONFIG, StandardCharsets.UTF_8)) {
            p.load(lector);
        } catch (IOException e) {
            throw new SQLException("No se pudo leer " + RUTA_CONFIG + ": " + e.getMessage(), e);
        }
        return p;
    }

    /** Prueba rápida: ejecutar este main para saber si la conexión funciona. */
    public static void main(String[] args) {
        try (Connection conexion = conectar()) {
            DatabaseMetaData info = conexion.getMetaData();
            System.out.println("Conectado a " + info.getDatabaseProductName() + " " + info.getDatabaseProductVersion());
            System.out.println("Base de datos: " + conexion.getCatalog());
            System.out.println("Tabla Historial: " + (existeTabla(info, "Historial") ? "existe" : "FALTA (ejecuta sql/02_historial.sql)"));
            System.out.println("Tabla Metadatos: " + (existeTabla(info, "Metadatos") ? "existe" : "FALTA (ejecuta sql/02_historial.sql)"));
        } catch (SQLException e) {
            System.err.println("No se pudo conectar a SQL Server: " + e.getMessage());
        }
    }

    private static boolean existeTabla(DatabaseMetaData info, String nombre) throws SQLException {
        try (ResultSet rs = info.getTables(null, "dbo", nombre, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
