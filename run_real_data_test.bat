@echo off
echo ========================================
echo HFT TRADING SYSTEM - REAL DATA TEST
echo ========================================
echo.
echo This test will demonstrate:
echo 1. Real-time Binance data processing
echo 2. ML component integration
echo 3. Trading strategy execution
echo 4. Risk management with live data
echo 5. Performance monitoring
echo.
echo The system will connect to Binance and process
echo live BTC/USDT and ETH/USDT market data.
echo.
echo CHOICES:
echo 1. Market Making Strategy (provides liquidity)
echo 2. Momentum Strategy (follows trends)
echo 3. Triangular Arbitrage (cross-currency)
echo 4. Statistical Arbitrage (mean reversion)
echo.
echo After starting, press ENTER to stop gracefully.
echo.
pause

echo.
echo === STARTING HFT SYSTEM ===
echo.

cd /d "d:\hft-trading-system"

echo Compiling with optimizations...
call mvn clean compile package -q

echo.
echo Starting with ultra-fast JVM optimizations...
echo.

java --add-modules java.se ^
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
     -XX:+UseG1GC ^
     -XX:MaxGCPauseMillis=50 ^
     -XX:+UseStringDeduplication ^
     -XX:+UseNUMA ^
     -XX:+AggressiveOpts ^
     -XX:+UseBiasedLocking ^
     -XX:+DoEscapeAnalysis ^
     -Xms2g ^
     -Xmx4g ^
     -jar target/hft-trading-system-1.0-SNAPSHOT.jar

echo.
echo === TEST COMPLETED ===
echo.
echo Check the logs above for:
echo - Real Binance data connection
echo - ML model predictions
echo - Strategy order generation
echo - Performance metrics
echo.
pause
