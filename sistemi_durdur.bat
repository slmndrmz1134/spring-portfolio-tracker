@echo off
echo Portfolio Tracker (Arka Plan) kapatiliyor...
echo.
WMIC PROCESS WHERE "CommandLine LIKE '%%tracker-0.0.1-SNAPSHOT.jar%%'" CALL Terminate >nul 2>&1
echo Sistem basariyla durduruldu!
echo.
pause
