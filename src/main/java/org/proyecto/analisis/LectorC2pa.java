package org.proyecto.analisis;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lee lo básico de un manifiesto C2PA ("Content Credentials").
 *
 * Es el "certificado de origen" que ya ponen OpenAI, Adobe Firefly, Google y
 * algunas cámaras. Va en JPG (segmentos APP11) y en PNG (chunk caBX), dentro
 * de cajas JUMBF con datos en formato CBOR. No validamos la firma digital:
 * solo buscamos quién generó el archivo y si se declara hecho con IA.
 */
final class LectorC2pa {

    private static final String GRUPO = "C2PA";

    private LectorC2pa() {
    }

    static void leer(byte[] manifiesto, List<Metadato> datos) {
        datos.add(new Metadato(GRUPO, "Manifiesto", "Presente (" + manifiesto.length + " bytes, firma no verificada)"));

        Set<String> generadores = new LinkedHashSet<>();
        generadores.addAll(textosDespuesDe(manifiesto, "claim_generator"));
        generadores.addAll(textosDespuesDe(manifiesto, "softwareAgent"));
        for (String g : generadores) datos.add(new Metadato(GRUPO, "Generador", g));

        Set<String> origenes = new LinkedHashSet<>(textosDespuesDe(manifiesto, "digitalSourceType"));
        for (String o : origenes) {
            datos.add(new Metadato(GRUPO, "DigitalSourceType", o.substring(o.lastIndexOf('/') + 1)));
        }
    }

    /**
     * Busca todas las veces que aparece la clave y lee el texto CBOR que va justo detrás.
     * Si detrás hay un mapa (por ejemplo softwareAgent: {name: "..."}), lee el primer "name".
     */
    private static List<String> textosDespuesDe(byte[] b, String clave) {
        byte[] buscado = clave.getBytes(StandardCharsets.US_ASCII);
        List<String> encontrados = new java.util.ArrayList<>();
        for (int i = indice(b, buscado, 0); i >= 0; i = indice(b, buscado, i + 1)) {
            int pos = i + buscado.length;
            if (pos >= b.length) break;
            String texto = textoCbor(b, pos);
            if (texto == null && (b[pos] & 0xE0) == 0xA0) {        // es un mapa: buscar "name" dentro
                int nombre = indice(b, "name".getBytes(StandardCharsets.US_ASCII), pos);
                if (nombre > 0 && nombre - pos < 64) texto = textoCbor(b, nombre + 4);
            }
            if (texto != null && !texto.isBlank() && !texto.startsWith("_")) encontrados.add(texto);
        }
        return encontrados;
    }

    /** En CBOR un texto empieza con un byte 0x60-0x77 (largo incluido) o 0x78/0x79 (largo en 1 o 2 bytes). */
    private static String textoCbor(byte[] b, int pos) {
        if (pos >= b.length) return null;
        int cabecera = b[pos] & 0xFF;
        if ((cabecera & 0xE0) != 0x60) return null;
        int largo = cabecera & 0x1F, inicio = pos + 1;
        if (largo == 24) {
            largo = b[pos + 1] & 0xFF;
            inicio = pos + 2;
        } else if (largo == 25) {
            largo = ((b[pos + 1] & 0xFF) << 8) | (b[pos + 2] & 0xFF);
            inicio = pos + 3;
        } else if (largo > 25) {
            return null;
        }
        if (inicio + largo > b.length) return null;
        return new String(b, inicio, largo, StandardCharsets.UTF_8);
    }

    private static int indice(byte[] b, byte[] buscado, int desde) {
        fuera:
        for (int i = desde; i <= b.length - buscado.length; i++) {
            for (int j = 0; j < buscado.length; j++) {
                if (b[i + j] != buscado[j]) continue fuera;
            }
            return i;
        }
        return -1;
    }
}
