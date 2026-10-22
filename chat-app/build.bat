@echo off
rem Builds both projects, runs the tests, and collects the executable JARs + config files in dist\
cd /d "%~dp0"

call mvn -B clean package || exit /b 1

if exist dist rmdir /s /q dist
mkdir dist
copy TCPServer\target\TCPServer.jar dist\ >nul
copy TCPClient\target\TCPClient.jar dist\ >nul
copy TCPServer\src\main\resources\server.properties dist\ >nul
copy TCPClient\src\main\resources\client.properties dist\ >nul

echo.
echo Done. Executables are in dist\
dir /b dist
echo.
echo Run:  cd dist ^&^& java -jar TCPServer.jar
echo       cd dist ^&^& java -jar TCPClient.jar localhost 3000
