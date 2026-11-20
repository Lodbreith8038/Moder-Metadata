package org.proyecto.analisis;

import org.proyecto.Padre.analizadorUniversal;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

public class AnalizadorDOC extends analizadorUniversal {

    @Override
    public String formato(){
        return "DOCX";
    }

    @Override
    protected  byte[] firma(){
        //Codioo de un documento doc
        return new byte[]{0x50, 0x4B, 0x03, 0x04};
    }

    @Override
    protected void leerContenido(File archivo, List<Metadato> datos) throws IOException {
        try (ZipFile zip = new ZipFile(archivo)) {

            if (zip.getEntry("word/document.xml") == null) {
                datos.add(new Metadato("Aviso", formato(), "Es un ZIP, pero no es un documento de Word"));
                return;
            }
//Agrega los datos del archivo en cuestion
            datos.add(new Metadato("Word", "Tipo de documento", "Word (.docx)"));
            datos.add(new Metadato("Word", "Archivos internos", String.valueOf(zip.size())));
        }
    }
}

