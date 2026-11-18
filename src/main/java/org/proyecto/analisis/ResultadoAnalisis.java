package org.proyecto.analisis;

import org.proyecto.ia.VeredictoIA;

import java.io.File;
import java.util.List;

/**
 * Todo lo que se sabe de un archivo después de analizarlo.
 *
 * @param error null si todo fue bien; si no, el motivo por el que no se pudo leer
 */
public record ResultadoAnalisis(File archivo, String formato, List<Metadato> metadatos,
                                VeredictoIA veredicto, String error) {

    public boolean tieneError() {
        return error != null;
    }
}
