package org.proyecto.analisis;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Contrato que cumple cada lector de formato (PNG, JPG...).
 *
 * Así el programa se adapta al archivo que entra: mira su firma, busca el
 * analizador que la reconoce y le pide los datos. Para soportar un formato
 * nuevo basta con crear una clase que implemente esta interfaz y registrarla
 * en {@link RegistroAnalizadores}; la ventana no hay que tocarla.
 */
public interface Analizador {

    /** Nombre corto del formato, por ejemplo "PNG". */
    String formato();

    /** Devuelve true si los primeros bytes del archivo son la firma de este formato. */
    boolean reconoce(byte[] cabecera);

    /** Lee los metadatos que hay dentro del archivo. */
    List<Metadato> analizar(File archivo) throws IOException;
}
