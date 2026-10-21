# Moder-Metadata

Aplicacion Java Swing para seleccionar o arrastrar imagenes PNG y consultar sus metadatos.

## Requisitos

- IntelliJ IDEA y JDK 25 (version indicada en pom.xml).
- Maven: IntelliJ descarga la dependencia `com.microsoft.sqlserver:mssql-jdbc`.
- SQL Server y SQL Server Management Studio (SSMS), solo para probar la base de datos.

## Ejecutar la interfaz

Abre el proyecto como proyecto Maven desde IntelliJ y ejecuta `org.proyecto.Main`. El lector de PNG no requiere SQL Server.

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

**Estado:** `ConexionSQL` solo prueba la conexion; `PanelArchivos` todavia no inserta los metadatos en la base. El script SQL crea estructura, no almacena los archivos PNG.

## Archivos que no se comparten

`config/db.properties`, `.idea/`, `target/`, archivos de SQL Server `.mdf`, `.ldf` o respaldos `.bak`. Nunca subas contrasenas, tokens o datos privados.

## Trabajar en equipo

Cada integrante clona el repositorio, importa Maven y ejecuta el script SQL en su propia instancia. Cada uno crea su `config/db.properties` privado. Usa `git pull` antes de empezar a trabajar y `git push` despues de confirmar los cambios.
