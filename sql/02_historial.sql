-- Historial de archivos analizados (base compartida en MonsterASP).
-- Ejecutar conectado a la base del proyecto (en MonsterASP ya existe: no se crea aquí).
-- Solo crea las tablas si todavía no existen; no borra nada.
-- NO se guardan los archivos ni sus rutas: solo el hash y los metadatos leídos.

IF OBJECT_ID(N'dbo.Historial', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Historial (
        IdHistorial   INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Historial PRIMARY KEY,
        HashSHA256    CHAR(64)       NOT NULL,   -- huella del contenido: identifica el archivo sin guardar su ruta
        NombreArchivo NVARCHAR(255)  NOT NULL,
        TipoFormato   VARCHAR(20)    NOT NULL,   -- PNG, JPG, DOCX, PDF...
        TamanoBytes   BIGINT         NOT NULL,
        SubidoPor     NVARCHAR(100)  NULL,       -- nombre del integrante, no datos personales
        Fecha         DATETIME2(0)   NOT NULL CONSTRAINT DF_Historial_Fecha DEFAULT SYSUTCDATETIME(),
        VeredictoIA   NVARCHAR(400)  NULL
    );
    CREATE INDEX IX_Historial_Hash ON dbo.Historial (HashSHA256);
END;
GO

IF OBJECT_ID(N'dbo.Metadatos', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Metadatos (
        IdMetadato  INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Metadatos PRIMARY KEY,
        IdHistorial INT            NOT NULL
            CONSTRAINT FK_Metadatos_Historial REFERENCES dbo.Historial (IdHistorial) ON DELETE CASCADE,
        Grupo       NVARCHAR(50)   NOT NULL,     -- Archivo, EXIF, XMP, PDF...
        Clave       NVARCHAR(200)  NOT NULL,
        Valor       NVARCHAR(4000) NULL          -- se recorta en Java: la base solo tiene 1 GB
    );
    CREATE INDEX IX_Metadatos_Historial ON dbo.Metadatos (IdHistorial);
END;
GO
