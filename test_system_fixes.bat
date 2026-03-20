@echo off
echo === TESTING SYSTEM FIXES ===
echo.
echo 1. Fixed ENTER key to work properly
echo 2. Reduced log spam (warnings every 5 seconds only)
echo 3. Added better user control
echo.
echo Starting HFT system with fixes...
echo.
java -jar target/hft-trading-system-1.0-SNAPSHOT.jar
echo.
echo === TEST COMPLETE ===
pause
