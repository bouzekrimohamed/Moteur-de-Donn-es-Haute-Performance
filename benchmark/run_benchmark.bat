@echo off
:: ============================================================
:: Benchmark automatise - Moteur de Donnees Haute Performance
::
:: Ce script :
::   1. Appelle GET /benchmark/run (100k a 4M lignes)
::   2. Sauvegarde les resultats dans avant.json
::   3. Genere les graphiques avec plot_results.py
::
:: Prerequis :
::   - Serveur demarre dans un autre terminal :
::       cd .. && $env:JAVA_OPTS="-Xmx3g"; .\mvnw.cmd quarkus:dev
::   - Python + matplotlib :
::       pip install matplotlib
:: ============================================================

set PORT=8080
set RESULTAT=avant.json

echo.
echo ===================================================
echo  Benchmark Moteur de Donnees Haute Performance
echo ===================================================
echo.

curl -s -o nul -w "%%{http_code}" http://localhost:%PORT%/tables > tmp_code.txt 2>nul
set /p CODE=<tmp_code.txt
del tmp_code.txt 2>nul

if not "%CODE%"=="200" (
    echo ERREUR : le serveur ne repond pas sur le port %PORT%.
    echo.
    echo Demarre-le avec :
    echo   $env:JAVA_OPTS="-Xmx3g"; .\mvnw.cmd quarkus:dev
    echo.
    pause
    exit /b 1
)

echo [1/2] Lancement du benchmark ^(100k a 4M lignes^)...
echo       Cela peut prendre 2 a 5 minutes.
echo.

curl -s -o %RESULTAT% http://localhost:%PORT%/benchmark/run

if %ERRORLEVEL% NEQ 0 (
    echo ERREUR lors de l'appel au serveur.
    pause
    exit /b 1
)

echo Resultats sauvegardes dans : %RESULTAT%
echo.

echo [2/2] Generation des graphiques...
python plot_results.py %RESULTAT%

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERREUR : matplotlib manquant ? Installez-le : pip install matplotlib
    pause
    exit /b 1
)

echo.
echo Termine ! Graphique disponible dans le dossier "graphiques\".
pause
