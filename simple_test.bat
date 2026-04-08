@echo off
echo === SIMPLE REAL DATA TEST ===
echo.
echo Testing HFT System with Real Binance Data
echo.

cd /d "d:\hft-trading-system"

echo Step 1: Compile the system...
call mvn compile -q

echo Step 2: Run with real data...
echo.
echo The system will:
echo 1. Connect to Binance WebSocket
echo 2. Receive real BTC/USDT and ETH/USDT trades
echo 3. Process with ML components
echo 4. Execute trading strategies
echo 5. Show live performance metrics
echo.
echo Press Ctrl+C to stop the test
echo.

java --add-modules java.se ^
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
     -XX:+UseG1GC ^
     -XX:MaxGCPauseMillis=50 ^
     -XX:+UseStringDeduplication ^
     -XX:+UseNUMA ^
     -Xms2g ^
     -Xmx4g ^
     -cp "target\classes" ^
     com.hft.Main

echo.
echo === TEST COMPLETED ===
pause
