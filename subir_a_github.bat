@echo off
title Subir a GitHub para Compilar APK
cls
echo ======================================================================
echo       SUBIR PROYECTO ANDROID A GITHUB PARA COMPILACION DE APK
echo ======================================================================
echo.
echo 1. Crea un repositorio nuevo en: https://github.com/new
echo    (Puedes llamarlo: comedero-ratita y ponerlo Publico o Privado)
echo.
echo 2. Pega aqui la URL de tu repositorio de GitHub:
echo    Ejemplo: https://github.com/tu-usuario/comedero-ratita.git
echo.
set /p REPO_URL="URL del Repositorio: "

if "%REPO_URL%"=="" (
    echo.
    echo No introdujiste ninguna URL. Cancelado.
    pause
    exit /b
)

echo.
echo Vinculando con %REPO_URL% y subiendo...
git remote remove origin 2>nul
git remote add origin %REPO_URL%
git branch -M main
git push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ======================================================================
    echo  *** SUBIDO CON EXITO! ***
    echo.
    echo  GitHub Actions ha comenzado a compilar tu APK en la nube.
    echo  Ve a la pestana "Actions" en tu repositorio de GitHub para ver
    echo  el progreso y descargar el archivo: Comedero-Ratita-APK.
    echo ======================================================================
) else (
    echo.
    echo ----------------------------------------------------------------------
    echo  Hubo un problema al subir (verifica tu usuario/token de GitHub).
    echo ----------------------------------------------------------------------
)
echo.
pause
