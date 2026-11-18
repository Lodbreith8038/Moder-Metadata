package org.proyecto.analisis;

import org.proyecto.ia.DetectorIA;
import org.proyecto.ia.VeredictoIA;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Punto de entrada del análisis: recibe cualquier archivo, decide qué
 * analizador usar mirando su firma (no la extensión, porque un .jpg puede
 * estar renombrado a .png) y junta el resultado con el veredicto de IA.
 */
public class RegistroAnalizadores {

    private static final int BYTES_FIRMA = 16;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<Analizador> analizadores = new ArrayList<>();
    private final DetectorIA detector = new DetectorIA();

    public RegistroAnalizadores() {
        registrar(new AnalizadorPng());
        registrar(new AnalizadorJpg());
    }

    /** Añade un formato nuevo al programa. */
    public void registrar(Analizador analizador) {
        analizadores.add(analizador);
    }

    /** Analiza un archivo. Nunca lanza excepción: si algo falla, el error va dentro del resultado. */
    public ResultadoAnalisis analizar(File archivo) {
        List<Metadato> datos = new ArrayList<>();
        try {
            datos.addAll(datosDelSistema(archivo));

            Analizador analizador = buscar(leerPrimerosBytes(archivo));
            if (analizador == null) {
                return new ResultadoAnalisis(archivo, "Desconocido", datos, VeredictoIA.sinEvidencia(),
                        "Formato no soportado todavía");
            }

            datos.add(new Metadato("Archivo", "Formato detectado", analizador.formato()));
            datos.addAll(analizador.analizar(archivo));
            return new ResultadoAnalisis(archivo, analizador.formato(), datos, detector.evaluar(datos), null);

        } catch (IOException | RuntimeException e) {
            // RuntimeException: un archivo muy dañado no debe tumbar el análisis del lote entero
            String motivo = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new ResultadoAnalisis(archivo, "?", datos, VeredictoIA.sinEvidencia(), motivo);
        }
    }

    private Analizador buscar(byte[] cabecera) {
        for (Analizador a : analizadores) {
            if (a.reconoce(cabecera)) return a;
        }
        return null;
    }

    /** Datos que da el sistema operativo; no están dentro del archivo. */
    private static List<Metadato> datosDelSistema(File archivo) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(archivo.toPath(), BasicFileAttributes.class);
        return List.of(
                new Metadato("Archivo", "Nombre", archivo.getName()),
                new Metadato("Archivo", "Carpeta", archivo.getParent()),
                new Metadato("Archivo", "Tamaño", tamanoLegible(attr.size())),
                new Metadato("Archivo", "Fecha de creación", fecha(attr.creationTime())),
                new Metadato("Archivo", "Fecha de modificación", fecha(attr.lastModifiedTime()))
        );
    }

    private static String fecha(FileTime t) {
        return LocalDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault()).format(FORMATO_FECHA);
    }

    private static String tamanoLegible(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        if (bytes < 1024 * 1024) return String.format("%.1f KB (%,d bytes)", bytes / 1024.0, bytes);
        return String.format("%.1f MB (%,d bytes)", bytes / (1024.0 * 1024), bytes);
    }

    private static byte[] leerPrimerosBytes(File archivo) throws IOException {
        try (InputStream in = new FileInputStream(archivo)) {
            return in.readNBytes(BYTES_FIRMA);
        }
    }
}
