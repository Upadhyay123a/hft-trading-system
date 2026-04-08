@echo off
echo ========================================
echo HFT TRADING SYSTEM - LIVE RESULTS
echo ========================================
echo.

cd /d "d:\hft-trading-system"

echo Step 1: Compiling...
call mvn clean compile -q

echo.
echo Step 2: Running Live Test...
echo.

java -cp "target\classes;target\hft-trading-system-1.0-SNAPSHOT.jar" ^
     --add-modules java.se ^
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
     LiveTest

echo.
echo === TEST COMPLETE ===
pause
