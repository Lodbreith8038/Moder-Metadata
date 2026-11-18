package org.proyecto.analisis;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Lee un bloque EXIF. Lo usan el JPG (segmento APP1) y el PNG (chunk eXIf).
 *
 * EXIF por dentro es una cabecera TIFF + tablas de etiquetas (IFD):
 *   "II" = Intel (little-endian, el byte menor primero)
 *   "MM" = Motorola (big-endian, el byte mayor primero)
 * Cada etiqueta ocupa 12 bytes: [número 2] [tipo 2] [cantidad 4] [valor o posición 4]
 */
final class LectorExif {

    private static final String GRUPO = "EXIF";

    // Etiquetas EXIF que mostramos (número -> nombre)
    private static final Map<Integer, String> ETIQUETAS = Map.ofEntries(
            Map.entry(0x010E, "Descripción"),
            Map.entry(0x010F, "Marca de cámara"),
            Map.entry(0x0110, "Modelo de cámara"),
            Map.entry(0x0112, "Orientación"),
            Map.entry(0x0131, "Software"),
            Map.entry(0x0132, "Fecha de modificación"),
            Map.entry(0x013B, "Autor"),
            Map.entry(0x8298, "Copyright"),
            Map.entry(0x829A, "Tiempo de exposición"),
            Map.entry(0x829D, "Apertura"),
            Map.entry(0x8827, "ISO"),
            Map.entry(0x9003, "Fecha original"),
            Map.entry(0x9004, "Fecha de digitalización"),
            Map.entry(0x920A, "Distancia focal"),
            Map.entry(0x9286, "Comentario de usuario"),
            Map.entry(0xA434, "Objetivo")
    );

    private static final int PUNTERO_EXIF = 0x8769;  // apunta a la tabla con los datos de la foto
    private static final int PUNTERO_GPS = 0x8825;   // apunta a la tabla del GPS

    // Tipos de dato de TIFF que sabemos leer
    private static final int TEXTO = 2, CORTO = 3, LARGO = 4, RACIONAL = 5, SIN_TIPO = 7;

    private LectorExif() {
    }

    /**
     * @param c     bytes que contienen el EXIF
     * @param tiff  posición donde empieza la cabecera TIFF ("II" o "MM")
     */
    static void leer(byte[] c, int tiff, List<Metadato> datos) {
        if (c.length < tiff + 8) return;
        boolean intel = c[tiff] == 'I';
        try {
            int primeraTabla = leerEntero(c, tiff + 4, intel);
            Punteros p = leerTabla(c, tiff, tiff + primeraTabla, intel, datos);
            if (p.exif > 0) leerTabla(c, tiff, tiff + p.exif, intel, datos);
            if (p.gps > 0) leerGps(c, tiff, tiff + p.gps, intel, datos);
        } catch (IndexOutOfBoundsException e) {
            // EXIF mal formado: mostramos lo que se pudo leer y seguimos
            datos.add(new Metadato("Aviso", "EXIF", "El bloque EXIF está dañado; se leyó solo una parte"));
        }
    }

    private record Punteros(int exif, int gps) {
    }

    private static Punteros leerTabla(byte[] c, int tiff, int pos, boolean intel, List<Metadato> datos) {
        int punteroExif = 0, punteroGps = 0;
        int cantidad = leerCorto(c, pos, intel);

        for (int i = 0; i < cantidad; i++) {
            int e = pos + 2 + i * 12;
            int numero = leerCorto(c, e, intel);
            int tipo = leerCorto(c, e + 2, intel);
            int cuantos = leerEntero(c, e + 4, intel);

            if (numero == PUNTERO_EXIF) {
                punteroExif = leerEntero(c, e + 8, intel);
            } else if (numero == PUNTERO_GPS) {
                punteroGps = leerEntero(c, e + 8, intel);
            } else if (ETIQUETAS.containsKey(numero)) {
                String valor = leerValor(c, tiff, e, numero, tipo, cuantos, intel);
                if (valor != null && !valor.isBlank()) {
                    datos.add(new Metadato(GRUPO, ETIQUETAS.get(numero), valor));
                }
            }
        }
        return new Punteros(punteroExif, punteroGps);
    }

    private static String leerValor(byte[] c, int tiff, int e, int numero, int tipo, int cuantos, boolean intel) {
        // Si el valor ocupa más de 4 bytes, en su lugar va la posición donde está
        int tamano = switch (tipo) {
            case CORTO -> 2 * cuantos;
            case LARGO -> 4 * cuantos;
            case RACIONAL -> 8 * cuantos;
            default -> cuantos;
        };
        int inicio = tamano <= 4 ? e + 8 : tiff + leerEntero(c, e + 8, intel);

        return switch (tipo) {
            case TEXTO -> texto(c, inicio, cuantos);
            case CORTO -> numero == 0x0112 ? nombreOrientacion(leerCorto(c, inicio, intel))
                    : String.valueOf(leerCorto(c, inicio, intel));
            case LARGO -> String.valueOf(leerEntero(c, inicio, intel) & 0xFFFFFFFFL);
            case RACIONAL -> formatearRacional(numero, leerEntero(c, inicio, intel), leerEntero(c, inicio + 4, intel));
            case SIN_TIPO -> numero == 0x9286 ? comentarioDeUsuario(c, inicio, cuantos, intel) : null;
            default -> null;
        };
    }

    private static String formatearRacional(int numero, long arriba, long abajo) {
        if (abajo == 0) return null;
        double v = (double) arriba / abajo;
        return switch (numero) {
            case 0x829A -> v < 1 ? "1/" + Math.round(1 / v) + " s" : v + " s";
            case 0x829D -> String.format("f/%.1f", v);
            case 0x920A -> String.format("%.0f mm", v);
            default -> String.format("%.2f", v);
        };
    }

    /** UserComment: 8 bytes que dicen la codificación + el texto. Stable Diffusion a veces guarda aquí el prompt. */
    private static String comentarioDeUsuario(byte[] c, int inicio, int cuantos, boolean intel) {
        if (cuantos <= 8) return null;
        String codificacion = new String(c, inicio, 8, StandardCharsets.US_ASCII);
        int largo = cuantos - 8;
        if (codificacion.startsWith("UNICODE")) {
            return new String(c, inicio + 8, largo, intel ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE)
                    .replace("\0", "").trim();
        }
        return texto(c, inicio + 8, largo);
    }

    /** Tabla GPS: latitud y longitud en grados, minutos y segundos. */
    private static void leerGps(byte[] c, int tiff, int pos, boolean intel, List<Metadato> datos) {
        String refLat = null, refLon = null;
        Double lat = null, lon = null;
        int cantidad = leerCorto(c, pos, intel);

        for (int i = 0; i < cantidad; i++) {
            int e = pos + 2 + i * 12;
            int numero = leerCorto(c, e, intel);
            switch (numero) {
                case 1 -> refLat = texto(c, e + 8, 1);
                case 2 -> lat = grados(c, tiff + leerEntero(c, e + 8, intel), intel);
                case 3 -> refLon = texto(c, e + 8, 1);
                case 4 -> lon = grados(c, tiff + leerEntero(c, e + 8, intel), intel);
            }
        }
        if (lat != null && lon != null) {
            if ("S".equals(refLat)) lat = -lat;
            if ("W".equals(refLon)) lon = -lon;
            datos.add(new Metadato("GPS", "Ubicación", String.format("%.6f, %.6f", lat, lon)));
        } else {
            datos.add(new Metadato("GPS", "Ubicación", "Hay datos GPS pero están incompletos"));
        }
    }

    private static double grados(byte[] c, int p, boolean intel) {
        double[] partes = new double[3];
        for (int i = 0; i < 3; i++) {
            long arriba = leerEntero(c, p + i * 8, intel) & 0xFFFFFFFFL;
            long abajo = leerEntero(c, p + i * 8 + 4, intel) & 0xFFFFFFFFL;
            partes[i] = abajo == 0 ? 0 : (double) arriba / abajo;
        }
        return partes[0] + partes[1] / 60 + partes[2] / 3600;
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

    /** El estándar dice ASCII, pero muchos móviles guardan UTF-8: probamos UTF-8 y si falla, Latin-1. */
    static String texto(byte[] c, int inicio, int largo) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(c, inicio, largo)).toString().replace("\0", "").trim();
        } catch (CharacterCodingException e) {
            return new String(c, inicio, largo, StandardCharsets.ISO_8859_1).replace("\0", "").trim();
        }
    }

    private static int leerCorto(byte[] c, int p, boolean intel) {
        int a = c[p] & 0xFF, b = c[p + 1] & 0xFF;
        return intel ? (b << 8) | a : (a << 8) | b;
    }

    private static int leerEntero(byte[] c, int p, boolean intel) {
        int a = c[p] & 0xFF, b = c[p + 1] & 0xFF, d = c[p + 2] & 0xFF, e = c[p + 3] & 0xFF;
        return intel ? (e << 24) | (d << 16) | (b << 8) | a
                     : (a << 24) | (b << 16) | (d << 8) | e;
    }
}
