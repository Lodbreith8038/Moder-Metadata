package org.proyecto;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lee la metadata de un archivo JPG / JPEG.
 *
 * Un JPG es una serie de bloques llamados "segmentos". Cada segmento empieza con:
 *   [0xFF] [tipo 1 byte] [longitud 2 bytes] [datos]
 * La longitud incluye sus propios 2 bytes.
 *
 * Segmentos que nos interesan:
 *   FFD8 = inicio del archivo (SOI)
 *   FFE0 = APP0 / JFIF  -> resolución (DPI)
 *   FFE1 = APP1 / EXIF  -> cámara, fechas, autor... (lo más importante)
 *   FFFE = COM          -> comentario de texto
 *   FFC0 / FFC2 = SOF   -> ancho y alto
 *   FFDA = SOS          -> aquí empieza la imagen comprimida, dejamos de leer
 */
public class AnalizadorJpg {

    /** Todo JPG empieza con FF D8 FF */
    public static boolean esJpg(byte[] cabecera) {
        return cabecera.length >= 3
                && (cabecera[0] & 0xFF) == 0xFF
                && (cabecera[1] & 0xFF) == 0xD8
                && (cabecera[2] & 0xFF) == 0xFF;
    }

    /** Devuelve la metadata como pares "nombre -> valor", en orden. */
    public static Map<String, String> analizar(File archivo) throws IOException {
        Map<String, String> datos = new LinkedHashMap<>();

        // 1) Datos del sistema operativo (igual que en AnalizadorPng)
        BasicFileAttributes attr = Files.readAttributes(archivo.toPath(), BasicFileAttributes.class);
        datos.put("Nombre", archivo.getName());
        datos.put("Tamaño", attr.size() + " bytes");
        datos.put("Fecha de creación (sistema)", attr.creationTime().toString());
        datos.put("Fecha de modificación (sistema)", attr.lastModifiedTime().toString());

        try (DataInputStream in = new DataInputStream(new FileInputStream(archivo))) {

            // 2) Comprobar el inicio: FF D8
            if (in.readUnsignedByte() != 0xFF || in.readUnsignedByte() != 0xD8) {
                throw new IOException("El archivo no es un JPG");
            }

            // 3) Recorrer los segmentos uno por uno
            while (true) {
                int marca = in.readUnsignedByte();
                if (marca != 0xFF) throw new IOException("JPG dañado: se esperaba 0xFF");

                int tipo = in.readUnsignedByte();
                while (tipo == 0xFF) tipo = in.readUnsignedByte();   // a veces hay FF de relleno

                if (tipo == 0xDA || tipo == 0xD9) break;             // empieza la imagen o fin: paramos

                int longitud = in.readUnsignedShort();               // 2 bytes
                byte[] contenido = in.readNBytes(longitud - 2);      // la longitud incluye sus 2 bytes

                switch (tipo) {
                    case 0xE0 -> leerJFIF(contenido, datos);
                    case 0xE1 -> leerEXIF(contenido, datos);
                    case 0xFE -> datos.put("Comentario", new String(contenido, StandardCharsets.UTF_8).trim());
                    case 0xC0, 0xC1, 0xC2 -> leerTamano(tipo, contenido, datos);
                }
            }
        } catch (EOFException e) {
            // El archivo se acabó antes de tiempo
            throw new IOException("El JPG está incompleto o dañado");
        }
        return datos;
    }

    /** SOF: alto, ancho y número de canales de color. */
    private static void leerTamano(int tipo, byte[] c, Map<String, String> datos) {
        int alto = ((c[1] & 0xFF) << 8) | (c[2] & 0xFF);
        int ancho = ((c[3] & 0xFF) << 8) | (c[4] & 0xFF);
        int canales = c[5] & 0xFF;

        datos.put("Ancho", ancho + " px");
        datos.put("Alto", alto + " px");
        datos.put("Tipo de color", canales == 1 ? "Escala de grises" : canales == 3 ? "Color (YCbCr)" : canales + " canales");
        datos.put("Codificación", tipo == 0xC2 ? "Progresiva" : "Básica (baseline)");
    }

    /** APP0 / JFIF: resolución. */
    private static void leerJFIF(byte[] c, Map<String, String> datos) {
        // Empieza con el texto "JFIF\0"
        if (c.length < 12 || c[0] != 'J' || c[1] != 'F' || c[2] != 'I' || c[3] != 'F') return;
        int unidad = c[7];                                    // 1 = puntos por pulgada, 2 = por cm
        int x = ((c[8] & 0xFF) << 8) | (c[9] & 0xFF);
        if (unidad == 1) datos.put("Resolución", x + " DPI");
        if (unidad == 2) datos.put("Resolución", Math.round(x * 2.54) + " DPI");
    }

    // ------------------------------------------------------------------ EXIF

    /*
     * EXIF por dentro:
     *   "Exif\0\0"  +  cabecera TIFF  +  tablas de etiquetas (IFD)
     *
     * La cabecera TIFF dice el orden de los bytes:
     *   "II" = Intel (little-endian, el byte menor primero)
     *   "MM" = Motorola (big-endian, el byte mayor primero)
     *
     * Cada etiqueta ocupa 12 bytes: [número 2] [tipo 2] [cantidad 4] [valor o posición 4]
     */

    // Etiquetas EXIF que vamos a mostrar (número -> nombre)
    private static final Map<Integer, String> ETIQUETAS = Map.ofEntries(
            Map.entry(0x010E, "Descripción"),
            Map.entry(0x010F, "Marca de cámara"),
            Map.entry(0x0110, "Modelo de cámara"),
            Map.entry(0x0112, "Orientación"),
            Map.entry(0x0131, "Software"),
            Map.entry(0x0132, "Fecha de modificación (EXIF)"),
            Map.entry(0x013B, "Autor"),
            Map.entry(0x8298, "Copyright"),
            Map.entry(0x9003, "Fecha original (EXIF)"),
            Map.entry(0x9004, "Fecha de digitalización (EXIF)")
    );

    private static final int PUNTERO_EXIF = 0x8769;  // apunta a una 2ª tabla con las fechas de la foto
    private static final int PUNTERO_GPS = 0x8825;   // apunta a la tabla del GPS

    private static void leerEXIF(byte[] c, Map<String, String> datos) {
        // Comprobar que empieza con "Exif\0\0" (APP1 también puede ser XMP)
        if (c.length < 14 || c[0] != 'E' || c[1] != 'x' || c[2] != 'i' || c[3] != 'f') return;

        int tiff = 6;                                   // la cabecera TIFF empieza después de "Exif\0\0"
        boolean intel = c[tiff] == 'I';                 // "II" o "MM"

        try {
            int primeraTabla = leerEntero(c, tiff + 4, intel);
            int punteroExif = leerTabla(c, tiff, tiff + primeraTabla, intel, datos);

            // La tabla principal puede apuntar a la tabla EXIF (fechas de la foto)
            if (punteroExif > 0) {
                leerTabla(c, tiff, tiff + punteroExif, intel, datos);
            }
        } catch (IndexOutOfBoundsException e) {
            // Si el EXIF está mal formado, mostramos lo que se pudo leer y seguimos
            datos.put("Aviso", "El bloque EXIF está dañado; se leyó solo una parte");
        }
    }

    /** Lee una tabla de etiquetas. Devuelve la posición de la tabla EXIF si la encuentra, o 0. */
    private static int leerTabla(byte[] c, int tiff, int pos, boolean intel, Map<String, String> datos) {
        int punteroExif = 0;
        int cantidad = leerCorto(c, pos, intel);

        for (int i = 0; i < cantidad; i++) {
            int e = pos + 2 + i * 12;                   // cada etiqueta ocupa 12 bytes
            int numero = leerCorto(c, e, intel);
            int tipoDato = leerCorto(c, e + 2, intel);
            int cuantos = leerEntero(c, e + 4, intel);

            if (numero == PUNTERO_EXIF) {
                punteroExif = leerEntero(c, e + 8, intel);
            } else if (numero == PUNTERO_GPS) {
                datos.put("GPS", "Contiene ubicación GPS");
            } else if (ETIQUETAS.containsKey(numero)) {
                String nombre = ETIQUETAS.get(numero);

                if (tipoDato == 2) {                    // 2 = texto
                    // Si el texto ocupa más de 4 bytes, en el valor va su posición
                    int inicio = cuantos <= 4 ? e + 8 : tiff + leerEntero(c, e + 8, intel);
                    String texto = new String(c, inicio, cuantos, StandardCharsets.ISO_8859_1);
                    datos.put(nombre, texto.replace("\0", "").trim());
                } else if (tipoDato == 3) {             // 3 = número corto (ej. orientación)
                    datos.put(nombre, nombreOrientacion(leerCorto(c, e + 8, intel)));
                }
            }
        }
        return punteroExif;
    }

    private static String nombreOrientacion(int o) {
        return switch (o) {
            case 1 -> "Normal";
            case 3 -> "Girada 180°";
            case 6 -> "Girada 90° a la derecha";
            case 8 -> "Girada 90° a la izquierda";
            default -> "Código " + o;
        };
    }

    /** Lee 2 bytes como número, respetando el orden de bytes. */
    private static int leerCorto(byte[] c, int p, boolean intel) {
        int a = c[p] & 0xFF, b = c[p + 1] & 0xFF;
        return intel ? (b << 8) | a : (a << 8) | b;
    }

    /** Lee 4 bytes como número, respetando el orden de bytes. */
    private static int leerEntero(byte[] c, int p, boolean intel) {
        int a = c[p] & 0xFF, b = c[p + 1] & 0xFF, d = c[p + 2] & 0xFF, e = c[p + 3] & 0xFF;
        return intel ? (e << 24) | (d << 16) | (b << 8) | a
                     : (a << 24) | (b << 16) | (d << 8) | e;
    }
}
