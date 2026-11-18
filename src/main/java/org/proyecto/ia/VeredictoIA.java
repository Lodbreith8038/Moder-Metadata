package org.proyecto.ia;

import java.util.List;

/**
 * Resultado del detector: la lista de pistas que indican que el archivo se hizo con IA.
 *
 * Ojo: si no hay pistas NO significa que la imagen sea "real", solo que sus
 * metadatos no dicen nada. Basta con borrar los metadatos para esconder la IA.
 */
public record VeredictoIA(List<String> pistas) {

    public static VeredictoIA sinEvidencia() {
        return new VeredictoIA(List.of());
    }

    public boolean hayEvidencia() {
        return !pistas.isEmpty();
    }
}
