package org.proyecto.analisis;

/**
 * Un dato suelto de un archivo, como lo muestra ExifTool:
 *   grupo  -> de dónde sale (Archivo, PNG, EXIF, XMP, C2PA...)
 *   clave  -> el nombre del dato (Autor, Software, Ancho...)
 *   valor  -> lo que contiene
 */
public record Metadato(String grupo, String clave, String valor) {
}
