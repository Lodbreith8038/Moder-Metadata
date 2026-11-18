# Moder-Metadata

Aplicacion Java Swing inspirada en ExifTool: analiza una imagen o un lote (archivos o carpetas, con boton o arrastrando), muestra sus metadatos y avisa si hay pistas de que se hizo con IA.

## Requisitos

- IntelliJ IDEA y JDK 25 (version indicada en pom.xml).
- Maven: IntelliJ descarga la dependencia `com.microsoft.sqlserver:mssql-jdbc`.
- SQL Server y SQL Server Management Studio (SSMS), solo para probar la base de datos.

## Ejecutar la interfaz

Abre el proyecto como proyecto Maven desde IntelliJ y ejecuta `org.proyecto.Main`. El analisis no requiere SQL Server.

## Como esta organizado el codigo

| Paquete | Que hace |
| --- | --- |
| `org.proyecto.analisis` | `RegistroAnalizadores` mira la firma del archivo y elige el lector (`AnalizadorPng`, `AnalizadorJpg`). `LectorExif`, `LectorXmp` y `LectorC2pa` leen los bloques que comparten ambos formatos. |
| `org.proyecto.ia` | `DetectorIA` busca pistas de IA en los metadatos: marca IPTC `DigitalSourceType`, prompts de Stable Diffusion / ComfyUI / InvokeAI / NovelAI y nombres de generadores (Midjourney, DALL-E, Firefly...). |
| `org.proyecto.ui` | `VentanaLote`: lista del lote a la izquierda, veredicto y tabla de metadatos a la derecha. Doble clic en una fila muestra el valor completo. |

Para soportar un formato nuevo: crea una clase que implemente `Analizador` y registrala en el constructor de `RegistroAnalizadores`. La ventana no hay que tocarla.

Si no hay pistas de IA no significa que la imagen sea real: los metadatos se pueden borrar.

## Base de datos de prueba

La base temporal se llama `DB_Image` y contiene la tabla `dbo.Images` con tres columnas: `IdImagen`, `NombreArchivo` y `RutaArchivo`.

1. Desde SSMS, ejecuta `sql/01_crear_DB_Image.sql` en tu propio SQL Server.
2. Copia `config/db.properties.example` a `config/db.properties`.
3. Ajusta `db.url` en el archivo nuevo con el servidor o puerto de **tu PC**.
4. Ejecuta `org.proyecto.DTB_IMAGENES.ConexionSQL` para probar la conexion. La carpeta de trabajo debe ser la raiz del proyecto, donde se encuentra `pom.xml`.

Ejemplo de configuracion para la instancia predeterminada en el puerto 1433:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=DB_Image;integratedSecurity=true;encrypt=true;trustServerCertificate=true;
```

Para una instancia con nombre, el puerto puede ser distinto. La autenticacion integrada de Windows puede necesitar la biblioteca de autenticacion nativa de Microsoft JDBC y permisos en SQL Server. Si utilizas autenticacion SQL en vez de Windows, ajusta el codigo para leer las credenciales de forma local.

**Estado:** `ConexionSQL` solo prueba la conexion; la ventana todavia no guarda el historial en la base. El script SQL crea estructura, no almacena los archivos PNG.

## Archivos que no se comparten

`config/db.properties`, `.idea/`, `target/`, archivos de SQL Server `.mdf`, `.ldf` o respaldos `.bak`. Nunca subas contrasenas, tokens o datos privados.

## Trabajar en equipo

Cada integrante clona el repositorio, importa Maven y ejecuta el script SQL en su propia instancia. Cada uno crea su `config/db.properties` privado. Usa `git pull` antes de empezar a trabajar y `git push` despues de confirmar los cambios.
