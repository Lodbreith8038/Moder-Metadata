package org.proyecto.analisis;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee un bloque XMP (XML de Adobe). Aparece en JPG (APP1) y en PNG (iTXt).
 *
 * El XMP puede guardar un dato de tres formas y aquí leemos las tres:
 *   atributo:    <rdf:Description xmp:CreatorTool="Photoshop">
 *   elemento:    <xmp:CreatorTool>Photoshop</xmp:CreatorTool>
 *   lista:       <dc:creator><rdf:Seq><rdf:li>Ana</rdf:li></rdf:Seq></dc:creator>
 * Aquí está, por ejemplo, la marca oficial de IA: Iptc4xmpExt:DigitalSourceType.
 */
final class LectorXmp {

    private static final String GRUPO = "XMP";

    private static final Pattern ATRIBUTO =
            Pattern.compile("\\s([A-Za-z][\\w.-]*):([\\w.-]+)=(?:\"([^\"]*)\"|'([^']*)')");   // comillas dobles o simples
    private static final Pattern LISTA = Pattern.compile(
            "<([A-Za-z][\\w.-]*):([\\w.-]+)>\\s*<rdf:(?:Seq|Bag|Alt)>(.*?)</rdf:(?:Seq|Bag|Alt)>\\s*</\\1:\\2>",
            Pattern.DOTALL);
    private static final Pattern ELEMENTO_LISTA = Pattern.compile("<rdf:li[^>]*>([^<]*)</rdf:li>");
    private static final Pattern ELEMENTO =
            Pattern.compile("<([A-Za-z][\\w.-]*):([\\w.-]+)(?:\\s[^>]*)?>([^<]+)</\\1:\\2>");

    private LectorXmp() {
    }

    static void leer(String xml, List<Metadato> datos) {
        Matcher m = ATRIBUTO.matcher(xml);
        while (m.find()) {
            String prefijo = m.group(1);
            if (prefijo.equals("xmlns") || prefijo.equals("rdf") || prefijo.equals("xml")) continue;
            agregar(datos, m.group(2), m.group(3) != null ? m.group(3) : m.group(4));
        }

        m = LISTA.matcher(xml);
        while (m.find()) {
            StringBuilder valores = new StringBuilder();
            Matcher li = ELEMENTO_LISTA.matcher(m.group(3));
            while (li.find()) {
                if (!valores.isEmpty()) valores.append(", ");
                valores.append(li.group(1).trim());
            }
            agregar(datos, m.group(2), valores.toString());
        }

        m = ELEMENTO.matcher(xml);
        while (m.find()) {
            if (m.group(1).equals("rdf")) continue;
            agregar(datos, m.group(2), m.group(3));
        }
    }

    private static void agregar(List<Metadato> datos, String clave, String valor) {
        valor = desescapar(valor).trim();
        if (!valor.isEmpty()) datos.add(new Metadato(GRUPO, clave, valor));
    }

    private static String desescapar(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#xA;", "\n").replace("&amp;", "&");
    }
}
