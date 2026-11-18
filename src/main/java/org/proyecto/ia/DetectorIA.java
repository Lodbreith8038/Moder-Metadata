package org.proyecto.ia;

import org.proyecto.analisis.Metadato;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Revisa los metadatos ya leídos y busca pistas de que el archivo se generó con IA.
 *
 * Pistas que conoce:
 *   1. Marca oficial IPTC "DigitalSourceType" (en XMP o en C2PA).
 *   2. Prompts guardados por Stable Diffusion (AUTOMATIC1111 / Forge), ComfyUI, InvokeAI o NovelAI.
 *   3. Nombre de un generador de IA en campos como Software, CreatorTool o el generador del C2PA.
 *
 * Para añadir una regla nueva basta con añadir un "if" en revisar().
 */
public class DetectorIA {

    // Generadores conocidos. \b evita falsos positivos como "influx" -> "flux".
    private static final Pattern GENERADORES = Pattern.compile(
            "\\b(midjourney|dall[-·]?e|openai|chatgpt|stable diffusion|stability ai|novelai|firefly|"
                    + "imagen|gemini|google ai|leonardo|comfyui|automatic1111|invokeai|flux|ideogram|"
                    + "runway|sora|bing image creator|copilot designer|grok)\\b",
            Pattern.CASE_INSENSITIVE);

    // Campos donde un programa firma su nombre
    private static final Set<String> CAMPOS_DE_SOFTWARE = Set.of(
            "software", "creatortool", "generador", "credit", "author", "artist", "autor");

    public VeredictoIA evaluar(List<Metadato> datos) {
        Set<String> pistas = new LinkedHashSet<>();   // sin repetidos y en orden
        for (Metadato m : datos) revisar(m, pistas);
        return new VeredictoIA(new ArrayList<>(pistas));
    }

    private void revisar(Metadato m, Set<String> pistas) {
        String clave = m.clave().toLowerCase(Locale.ROOT);
        String valor = m.valor() == null ? "" : m.valor();
        String valorMin = valor.toLowerCase(Locale.ROOT);

        // 1) Marca oficial IPTC (la ponen Adobe, Google, OpenAI, Midjourney...)
        if (valorMin.contains("compositewithtrainedalgorithmicmedia")) {
            pistas.add("Declarada como editada con IA (IPTC DigitalSourceType, " + m.grupo() + ")");
        } else if (valorMin.contains("trainedalgorithmicmedia")) {
            pistas.add("Declarada como generada por IA (IPTC DigitalSourceType, " + m.grupo() + ")");
        }

        // 2) Prompts guardados por herramientas de generación local
        if (m.grupo().equals("Texto PNG")) {
            if (clave.equals("parameters") && valorMin.contains("steps:")) {
                pistas.add("Prompt de Stable Diffusion (AUTOMATIC1111 / Forge) en el bloque \"parameters\"");
            }
            if ((clave.equals("prompt") || clave.equals("workflow")) && valor.trim().startsWith("{")) {
                pistas.add("Flujo de trabajo de ComfyUI en el bloque \"" + m.clave() + "\"");
            }
            if (clave.equals("invokeai_metadata") || clave.equals("sd-metadata") || clave.equals("dream")) {
                pistas.add("Metadatos de InvokeAI en el bloque \"" + m.clave() + "\"");
            }
            if (clave.equals("comment") && valorMin.contains("\"steps\"") && valorMin.contains("\"sampler\"")) {
                pistas.add("Parámetros de generación de NovelAI en el bloque \"Comment\"");
            }
        }
        if (clave.equals("comentario de usuario") && valorMin.contains("steps:") && valorMin.contains("sampler")) {
            pistas.add("Prompt de Stable Diffusion en el comentario EXIF");
        }

        // 3) Nombre de un generador de IA en un campo de software
        if (CAMPOS_DE_SOFTWARE.contains(clave)) {
            Matcher g = GENERADORES.matcher(valor);
            if (g.find()) {
                pistas.add("Generador de IA \"" + g.group(1) + "\" en " + m.grupo() + " / " + m.clave());
            }
        }
    }
}
