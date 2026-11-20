package org.proyecto.analisis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class extraacionTexto {
    private static final int MAX_XML = 1024 * 1024;   // 1 MB: core.xml pesa normalmente 1 o 2 KB

    private static String leerTextoInterno(ZipFile zip, String ruta) throws IOException {
        ZipEntry entrada = zip.getEntry(ruta);
        if (entrada == null) return null;                     // no existe: no pasa nada

        try (InputStream in = zip.getInputStream(entrada)) {
            byte[] bytes = in.readNBytes(MAX_XML + 1);
            if (bytes.length > MAX_XML) {
                throw new IOException(ruta + " es demasiado grande (¿archivo trampa?)");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
