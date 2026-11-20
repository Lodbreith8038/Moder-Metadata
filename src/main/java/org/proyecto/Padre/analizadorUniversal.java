package org.proyecto.Padre;

import org.proyecto.analisis.Analizador;
import org.proyecto.analisis.Metadato;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class analizadorUniversal implements Analizador {
    protected abstract byte[] firma();
    protected abstract void leerContenido(File archivo,List<Metadato> datos) throws
        IOException;

    @Override
    public boolean reconoce(byte[] cabecera) {
        byte[] f = firma();
        return cabecera.length >= f.length && Arrays.equals(Arrays.copyOf(cabecera,f.length), f);
    }

    @Override
    public final List<Metadato> analizar(File archivo) {
        List<Metadato> datos = new ArrayList<>();
        try {
            leerContenido(archivo, datos);
        } catch (EOFException e){
            datos.add(new Metadato("Aviso", formato(), "El archivo esta incompleto"));
        } catch (IOException | RuntimeException e){
            datos.add(new Metadato("Aviso", formato(), "Archivo corrompido , solo se muestra (" + e.getMessage()  + ")"));
        }
        return datos;
    }
    
}
