@echo off
rem Starts UBC780XLT Companion. Needs a Java 21 runtime (JRE or JDK) on the PATH or in JAVA_HOME.
cd /d "%~dp0"
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
"%JAVA%" --module-path javafx\win --add-modules javafx.controls -cp "ubc780xlt-companion.jar;lib\*" io.github.f4iyy.ubc780xlt.Launcher %*
if errorlevel 1 pause
