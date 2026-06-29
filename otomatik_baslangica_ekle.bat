@echo off
echo Portfolio Tracker uygulamasini Windows Baslangicina ekliyoruz...
echo.

set SCRIPT="%TEMP%\create_shortcut.ps1"

echo $WshShell = New-Object -comObject WScript.Shell > %SCRIPT%
echo $Shortcut = $WshShell.CreateShortcut("$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup\PortfolioTracker.lnk") >> %SCRIPT%
echo $Shortcut.TargetPath = "%~dp0arka_planda_baslat.vbs" >> %SCRIPT%
echo $Shortcut.WorkingDirectory = "%~dp0" >> %SCRIPT%
echo $Shortcut.WindowStyle = 1 >> %SCRIPT%
echo $Shortcut.Save() >> %SCRIPT%

powershell -ExecutionPolicy Bypass -File %SCRIPT%
del %SCRIPT%

echo Islem tamamlandi! 
echo Artik bilgisayariniz acildiginda sistem siyah pencere OLMADAN, arka planda gizlice calisacaktir.
echo.
echo Sistemi test etmek icin bilgisayari yeniden baslatabilir
echo Veya hemen baslatmak icin 'arka_planda_baslat.vbs' dosyasina cift tiklayabilirsiniz.
echo.
pause
