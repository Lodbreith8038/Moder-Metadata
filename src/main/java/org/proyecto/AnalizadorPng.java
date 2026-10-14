package org.proyecto;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lee la metadata de un archivo PNG.
 *
 * Un PNG es una firma de 8 bytes seguida de bloques ("chunks").
 * Cada chunk tiene:  [longitud 4 bytes] [tipo 4 letras] [datos] [CRC 4 bytes]
 */
public class AnalizadorPng {

    // Todo PNG empieza con estos 8 bytes
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Devuelve la metadata como pares "nombre -> valor", en orden. */
    public static Map<String, String> analizar(File archivo) throws IOException {
        Map<String, String> datos = new LinkedHashMap<>();

        // 1) Datos del sistema operativo (no están dentro del PNG)
        BasicFileAttributes attr = Files.readAttributes(archivo.toPath(), BasicFileAttributes.class);
        datos.put("Nombre", archivo.getName());
        datos.put("Tamaño", attr.size() + " bytes");
        datos.put("Fecha de creación (sistema)", attr.creationTime().toString());
        datos.put("Fecha de modificación (sistema)", attr.lastModifiedTime().toString());

        try (DataInputStream in = new DataInputStream(new FileInputStream(archivo))) {

            // 2) Comprobar la firma
            byte[] firma = in.readNBytes(8);
            if (!Arrays.equals(firma, FIRMA_PNG)) {
                throw new IOException("El archivo no es un PNG");
            }

            // 3) Recorrer los chunks uno por uno
            while (true) {
                int longitud = in.readInt();                       // 4 bytes
                String tipo = new String(in.readNBytes(4), StandardCharsets.US_ASCII); // 4 letras
                byte[] contenido = in.readNBytes(longitud);        // los datos
                in.readInt();                                      // CRC (por ahora no lo revisamos)

                switch (tipo) {
                    case "IHDR" -> leerIHDR(contenido, datos);
                    case "tEXt" -> leerTexto(contenido, datos);
                    case "pHYs" -> leerResolucion(contenido, datos);
                    case "tIME" -> leerFecha(contenido, datos);
                }

                if (tipo.equals("IEND")) break;                    // fin del PNG
            }
        }
        return datos;
    }

    /** IHDR: el primer chunk. Tiene ancho, alto y tipo de color. */
    private static void leerIHDR(byte[] c, Map<String, String> datos) {
        int ancho = leerEntero(c, 0);
        int alto = leerEntero(c, 4);
        int bits = c[8];
        int tipoColor = c[9];

        datos.put("Ancho", ancho + " px");
        datos.put("Alto", alto + " px");
        datos.put("Profundidad de bits", String.valueOf(bits));
        datos.put("Tipo de color", switch (tipoColor) {
            case 0 -> "Escala de grises";
            case 2 -> "RGB";
            case 3 -> "Paleta de colores";
            case 4 -> "Grises con transparencia";
            case 6 -> "RGB con transparencia (RGBA)";
            default -> "Desconocido";
        });
    }

    /** tEXt: texto libre "clave\0valor". Aquí van Autor, Descripción, Copyright, Creation Time... */
    private static void leerTexto(byte[] c, Map<String, String> datos) {
        int separador = 0;
        while (c[separador] != 0) separador++;             // buscar el byte 0

        String clave = new String(c, 0, separador, StandardCharsets.ISO_8859_1);
        String valor = new String(c, separador + 1, c.length - separador - 1, StandardCharsets.ISO_8859_1);
        datos.put(clave, valor);
    }

    /** pHYs: resolución en píxeles por metro. La pasamos a DPI. */
    private static void leerResolucion(byte[] c, Map<String, String> datos) {
        int pxPorMetro = leerEntero(c, 0);
        if (c[8] == 1) {                                   // 1 = la unidad es el metro
            long dpi = Math.round(pxPorMetro * 0.0254);    // 1 pulgada = 0.0254 m
            datos.put("Resolución", dpi + " DPI");
        }
    }

    /** tIME: fecha de última modificación guardada dentro del PNG. */
    private static void leerFecha(byte[] c, Map<String, String> datos) {
        int anio = ((c[0] & 0xFF) << 8) | (c[1] & 0xFF);
        datos.put("Fecha de modificación (PNG)",
                String.format("%04d-%02d-%02d %02d:%02d:%02d", anio, c[2], c[3], c[4], c[5], c[6]));
    }

    /** Junta 4 bytes en un número entero (los PNG guardan los números "big-endian"). */
    private static int leerEntero(byte[] c, int pos) {
        return ((c[pos] & 0xFF) << 24) | ((c[pos + 1] & 0xFF) << 16)
             | ((c[pos + 2] & 0xFF) << 8) | (c[pos + 3] & 0xFF);
    }
}
