package org.proyecto.analisis;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lee la metadata de un archivo JPG / JPEG.
 *
 * Un JPG es una serie de bloques llamados "segmentos". Cada segmento empieza con:
 *   [0xFF] [tipo 1 byte] [longitud 2 bytes] [datos]
 * La longitud incluye sus propios 2 bytes.
 *
 * Segmentos que nos interesan:
 *   FFE0 = APP0 / JFIF  -> resolución (DPI)
 *   FFE1 = APP1         -> EXIF (cámara, fechas, autor, GPS) o XMP
 *   FFEB = APP11        -> manifiesto C2PA (Content Credentials)
 *   FFFE = COM          -> comentario de texto
 *   FFC0 / FFC2 = SOF   -> ancho y alto
 *   FFDA = SOS          -> aquí empieza la imagen comprimida, dejamos de leer
 */
public class AnalizadorJpg implements Analizador {

    private static final String GRUPO = "JPG";
    private static final byte[] CABECERA_XMP = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);

    @Override
    public String formato() {
        return "JPG";
    }

    /** Todo JPG empieza con FF D8 FF */
    @Override
    public boolean reconoce(byte[] cabecera) {
        return cabecera.length >= 3
                && (cabecera[0] & 0xFF) == 0xFF
                && (cabecera[1] & 0xFF) == 0xD8
                && (cabecera[2] & 0xFF) == 0xFF;
    }

    @Override
    public List<Metadato> analizar(File archivo) throws IOException {
        List<Metadato> datos = new ArrayList<>();
        ByteArrayOutputStream c2pa = new ByteArrayOutputStream();   // el C2PA puede venir partido en varios APP11

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(archivo)))) {
            if (in.readUnsignedByte() != 0xFF || in.readUnsignedByte() != 0xD8) {
                throw new IOException("El archivo no es un JPG");
            }

            while (true) {
                if (in.readUnsignedByte() != 0xFF) throw new IOException("JPG dañado: se esperaba 0xFF");

                int tipo = in.readUnsignedByte();
                while (tipo == 0xFF) tipo = in.readUnsignedByte();   // a veces hay FF de relleno

                if (tipo == 0xDA || tipo == 0xD9) break;             // empieza la imagen o fin: paramos

                int longitud = in.readUnsignedShort();
                if (longitud < 2) throw new IOException("JPG dañado: segmento con longitud inválida");
                byte[] contenido = in.readNBytes(longitud - 2);

                switch (tipo) {
                    case 0xE0 -> leerJFIF(contenido, datos);
                    case 0xE1 -> leerAPP1(contenido, datos);
                    case 0xEB -> juntarC2pa(contenido, c2pa);
                    case 0xFE -> datos.add(new Metadato(GRUPO, "Comentario",
                            new String(contenido, StandardCharsets.UTF_8).trim()));
                    case 0xC0, 0xC1, 0xC2 -> leerTamano(tipo, contenido, datos);
                }
            }
        } catch (EOFException e) {
            datos.add(new Metadato("Aviso", GRUPO, "El archivo termina antes de tiempo (está incompleto)"));
        }

        if (c2pa.size() > 0) LectorC2pa.leer(c2pa.toByteArray(), datos);
        return datos;
    }

    /** APP1 puede ser EXIF ("Exif\0\0" + TIFF) o XMP ("http://ns.adobe.com/xap/1.0/\0" + XML). */
    private static void leerAPP1(byte[] c, List<Metadato> datos) {
        if (c.length > 6 && c[0] == 'E' && c[1] == 'x' && c[2] == 'i' && c[3] == 'f') {
            LectorExif.leer(c, 6, datos);
        } else if (c.length > CABECERA_XMP.length
                && Arrays.equals(Arrays.copyOf(c, CABECERA_XMP.length), CABECERA_XMP)) {
            String xml = new String(c, CABECERA_XMP.length, c.length - CABECERA_XMP.length, StandardCharsets.UTF_8);
            LectorXmp.leer(xml, datos);
        }
    }

    /** APP11 con JUMBF: "JP" + instancia (2) + número de secuencia (4) + caja JUMBF. */
    private static void juntarC2pa(byte[] c, ByteArrayOutputStream c2pa) {
        if (c.length < 8 || c[0] != 'J' || c[1] != 'P') return;
        c2pa.write(c, 8, c.length - 8);
    }

    /** SOF: alto, ancho y número de canales de color. */
    private static void leerTamano(int tipo, byte[] c, List<Metadato> datos) {
        if (c.length < 6) return;
        int alto = ((c[1] & 0xFF) << 8) | (c[2] & 0xFF);
        int ancho = ((c[3] & 0xFF) << 8) | (c[4] & 0xFF);
        int canales = c[5] & 0xFF;

        datos.add(new Metadato(GRUPO, "Ancho", ancho + " px"));
        datos.add(new Metadato(GRUPO, "Alto", alto + " px"));
        datos.add(new Metadato(GRUPO, "Tipo de color",
                canales == 1 ? "Escala de grises" : canales == 3 ? "Color (YCbCr)" : canales + " canales"));
        datos.add(new Metadato(GRUPO, "Codificación", tipo == 0xC2 ? "Progresiva" : "Básica (baseline)"));
    }

    /** APP0 / JFIF: resolución. */
    private static void leerJFIF(byte[] c, List<Metadato> datos) {
        if (c.length < 12 || c[0] != 'J' || c[1] != 'F' || c[2] != 'I' || c[3] != 'F') return;
        int unidad = c[7];                                    // 1 = puntos por pulgada, 2 = por cm
        int x = ((c[8] & 0xFF) << 8) | (c[9] & 0xFF);
        if (unidad == 1) datos.add(new Metadato(GRUPO, "Resolución", x + " DPI"));
        if (unidad == 2) datos.add(new Metadato(GRUPO, "Resolución", Math.round(x * 2.54) + " DPI"));
    }
}
