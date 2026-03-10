@echo off
REM Run script for Windows

echo ========================================
echo Running Java Swing Chat Application
echo ========================================
echo.

if not exist "bin\main\ChatApplication.class" (
    echo Error: Application not compiled yet!
    echo Please run compile.bat first.
    echo.
    pause
    exit /b 1
)

echo Starting application...
echo.
java -cp bin main.ChatApplication

pause
