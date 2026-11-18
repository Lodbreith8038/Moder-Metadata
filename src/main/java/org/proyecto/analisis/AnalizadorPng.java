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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Lee la metadata de un archivo PNG.
 *
 * Un PNG es una firma de 8 bytes seguida de bloques ("chunks").
 * Cada chunk tiene:  [longitud 4 bytes] [tipo 4 letras] [datos] [CRC 4 bytes]
 *
 * Chunks que leemos:
 *   IHDR             -> ancho, alto, color
 *   tEXt/zTXt/iTXt   -> textos (Author, Description... y los prompts de Stable Diffusion / ComfyUI)
 *   iTXt "XML:com.adobe.xmp" -> bloque XMP
 *   eXIf             -> bloque EXIF
 *   caBX             -> manifiesto C2PA (Content Credentials)
 *   pHYs, tIME       -> resolución y fecha
 */
public class AnalizadorPng implements Analizador {

    // Todo PNG empieza con estos 8 bytes
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final String GRUPO = "PNG";
    private static final String GRUPO_TEXTO = "Texto PNG";
    private static final int MAX_TEXTO_DESCOMPRIMIDO = 10 * 1024 * 1024;   // protege contra archivos trampa

    @Override
    public String formato() {
        return "PNG";
    }

    @Override
    public boolean reconoce(byte[] cabecera) {
        return cabecera.length >= 8 && Arrays.equals(Arrays.copyOf(cabecera, 8), FIRMA_PNG);
    }

    @Override
    public List<Metadato> analizar(File archivo) throws IOException {
        List<Metadato> datos = new ArrayList<>();

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(archivo)))) {
            if (!Arrays.equals(in.readNBytes(8), FIRMA_PNG)) {
                throw new IOException("El archivo no es un PNG");
            }

            // Recorrer los chunks uno por uno
            while (true) {
                int longitud = in.readInt();
                String tipo = new String(in.readNBytes(4), StandardCharsets.US_ASCII);
                if (longitud < 0) throw new IOException("PNG dañado: chunk " + tipo + " con longitud inválida");

                if (tipo.equals("IDAT")) {          // son los píxeles: los saltamos sin cargarlos en memoria
                    in.skipNBytes(longitud + 4L);
                    continue;
                }
                byte[] contenido = in.readNBytes(longitud);
                in.readInt();                       // CRC (por ahora no lo revisamos)

                switch (tipo) {
                    case "IHDR" -> leerIHDR(contenido, datos);
                    case "tEXt" -> leerTexto(contenido, datos);
                    case "zTXt" -> leerTextoComprimido(contenido, datos);
                    case "iTXt" -> leerTextoInternacional(contenido, datos);
                    case "eXIf" -> LectorExif.leer(contenido, 0, datos);
                    case "caBX" -> LectorC2pa.leer(contenido, datos);
                    case "pHYs" -> leerResolucion(contenido, datos);
                    case "tIME" -> leerFecha(contenido, datos);
                }
                if (tipo.equals("IEND")) break;     // fin del PNG
            }
        } catch (EOFException e) {
            datos.add(new Metadato("Aviso", GRUPO, "El archivo termina antes de tiempo (está incompleto)"));
        }
        return datos;
    }

    /** IHDR: el primer chunk. Tiene ancho, alto y tipo de color. */
    private static void leerIHDR(byte[] c, List<Metadato> datos) {
        if (c.length < 10) return;
        datos.add(new Metadato(GRUPO, "Ancho", leerEntero(c, 0) + " px"));
        datos.add(new Metadato(GRUPO, "Alto", leerEntero(c, 4) + " px"));
        datos.add(new Metadato(GRUPO, "Profundidad de bits", String.valueOf(c[8])));
        datos.add(new Metadato(GRUPO, "Tipo de color", switch (c[9]) {
            case 0 -> "Escala de grises";
            case 2 -> "RGB";
            case 3 -> "Paleta de colores";
            case 4 -> "Grises con transparencia";
            case 6 -> "RGB con transparencia (RGBA)";
            default -> "Desconocido";
        }));
    }

    /** tEXt: "clave\0valor" en Latin-1. Aquí van Author, Description... y "parameters" de Stable Diffusion. */
    private static void leerTexto(byte[] c, List<Metadato> datos) {
        int cero = buscarCero(c, 0);
        if (cero < 0) return;
        String clave = new String(c, 0, cero, StandardCharsets.ISO_8859_1);
        String valor = new String(c, cero + 1, c.length - cero - 1, StandardCharsets.ISO_8859_1);
        datos.add(new Metadato(GRUPO_TEXTO, clave, valor));
    }

    /** zTXt: "clave\0" + método (1 byte) + texto comprimido con zlib. */
    private static void leerTextoComprimido(byte[] c, List<Metadato> datos) throws IOException {
        int cero = buscarCero(c, 0);
        if (cero < 0 || cero + 2 > c.length) return;
        String clave = new String(c, 0, cero, StandardCharsets.ISO_8859_1);
        byte[] texto = descomprimir(Arrays.copyOfRange(c, cero + 2, c.length));
        datos.add(new Metadato(GRUPO_TEXTO, clave, new String(texto, StandardCharsets.ISO_8859_1)));
    }

    /** iTXt: "clave\0" + ¿comprimido? + método + "idioma\0" + "clave traducida\0" + texto UTF-8. */
    private static void leerTextoInternacional(byte[] c, List<Metadato> datos) throws IOException {
        int cero = buscarCero(c, 0);
        if (cero < 0 || cero + 3 > c.length) return;
        String clave = new String(c, 0, cero, StandardCharsets.ISO_8859_1);
        boolean comprimido = c[cero + 1] == 1;
        int finIdioma = buscarCero(c, cero + 3);
        int finTraduccion = finIdioma < 0 ? -1 : buscarCero(c, finIdioma + 1);
        if (finTraduccion < 0) return;

        byte[] texto = Arrays.copyOfRange(c, finTraduccion + 1, c.length);
        if (comprimido) texto = descomprimir(texto);
        String valor = LectorExif.texto(texto, 0, texto.length);   // UTF-8; si viene mal escrito, Latin-1

        if (clave.equals("XML:com.adobe.xmp")) {
            LectorXmp.leer(valor, datos);
        } else {
            datos.add(new Metadato(GRUPO_TEXTO, clave, valor));
        }
    }

    /** pHYs: resolución en píxeles por metro. La pasamos a DPI. */
    private static void leerResolucion(byte[] c, List<Metadato> datos) {
        if (c.length < 9 || c[8] != 1) return;              // 1 = la unidad es el metro
        long dpi = Math.round(leerEntero(c, 0) * 0.0254);    // 1 pulgada = 0.0254 m
        datos.add(new Metadato(GRUPO, "Resolución", dpi + " DPI"));
    }

    /** tIME: fecha de última modificación guardada dentro del PNG. */
    private static void leerFecha(byte[] c, List<Metadato> datos) {
        if (c.length < 7) return;
        int anio = ((c[0] & 0xFF) << 8) | (c[1] & 0xFF);
        datos.add(new Metadato(GRUPO, "Fecha de modificación",
                String.format("%04d-%02d-%02d %02d:%02d:%02d", anio, c[2], c[3], c[4], c[5], c[6])));
    }

    private static byte[] descomprimir(byte[] comprimido) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(comprimido);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                salida.write(buffer, 0, n);
                if (salida.size() > MAX_TEXTO_DESCOMPRIMIDO) throw new IOException("Texto comprimido demasiado grande");
            }
        } catch (DataFormatException e) {
            throw new IOException("Texto comprimido dañado");
        } finally {
            inflater.end();
        }
        return salida.toByteArray();
    }

    private static int buscarCero(byte[] c, int desde) {
        for (int i = desde; i < c.length; i++) if (c[i] == 0) return i;
        return -1;
    }

    /** Junta 4 bytes en un número entero (los PNG guardan los números "big-endian"). */
    private static int leerEntero(byte[] c, int pos) {
        return ((c[pos] & 0xFF) << 24) | ((c[pos + 1] & 0xFF) << 16)
             | ((c[pos + 2] & 0xFF) << 8) | (c[pos + 3] & 0xFF);
    }
}
