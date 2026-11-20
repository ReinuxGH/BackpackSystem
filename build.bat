@echo off
setlocal
java -version
if errorlevel 1 (
  echo No se encontro Java. Instala JDK 25.
  pause
  exit /b 1
)
echo.
echo Comprueba que la version mostrada sea Java 25.
echo Ejecutando Gradle...
gradle clean build
if errorlevel 1 (
  echo La compilacion fallo. Revisa el error anterior y confirma JDK 25.
  pause
  exit /b 1
)
echo.
echo JAR creado en build\libs\BackpackSystem-1.0.0.jar
pause
