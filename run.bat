@echo off
echo =========================================
echo   HFT Trading System - Build ^& Run
echo =========================================
echo.

REM Check Maven
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven is not installed!
    echo Please install Maven first: https://maven.apache.org/install.html
    pause
    exit /b 1
)

REM Check Java
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java is not installed!
    echo Please install Java 11 or higher
    pause
    exit /b 1
)

echo [OK] Java and Maven found
echo.

REM Build
echo Building project...
call mvn clean package -q
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo [OK] Build successful!
echo.

:menu
echo Choose an option:
echo   1) Run Live Trading (connect to Binance)
echo   2) Run Backtesting
echo   3) Generate Sample Data
echo   4) Exit
echo.
set /p choice="Enter choice (1-4): "

if "%choice%"=="1" goto live
if "%choice%"=="2" goto backtest
if "%choice%"=="3" goto generate
if "%choice%"=="4" goto end

echo.
echo Invalid choice. Please try again.
echo.
goto menu

:live
echo.
echo Starting Live Trading...
echo Press CTRL+C to stop trading
echo.
call mvn exec:java -Dexec.mainClass="com.hft.Main" -q
goto menu

:backtest
echo.
echo Starting Backtesting...
echo.
call mvn exec:java -Dexec.mainClass="com.hft.backtest.BacktestRunner" -q
goto menu

:generate
echo.
echo Generating Sample Data...
echo.
call mvn exec:java -Dexec.mainClass="com.hft.utils.SampleDataGenerator" -q
goto menu

:end
echo.
echo Goodbye!
pause