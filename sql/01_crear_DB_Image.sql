-- Script portable de SQL Server: ejecutar desde SSMS.
-- Crea DB_Image y dbo.Images solo si todavia no existen.
-- No modifica registros existentes ni usa rutas especificas de otro PC.
USE [master];
GO

IF DB_ID(N'DB_Image') IS NULL
BEGIN
    CREATE DATABASE [DB_Image];
END;
GO

USE [DB_Image];
GO

IF OBJECT_ID(N'dbo.Images', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Images] (
        [IdImagen] INT IDENTITY(1,1) NOT NULL
            CONSTRAINT [PK_Images] PRIMARY KEY,
        [NombreArchivo] VARCHAR(255) NOT NULL,
        [RutaArchivo] VARCHAR(1024) NOT NULL
    );
END;
GO
