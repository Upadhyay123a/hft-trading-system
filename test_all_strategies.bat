@echo off
echo === TESTING ALL TRADING STRATEGIES ===
echo.
echo Testing each strategy with ultra-fast optimizations
echo.

REM Test Strategy 1: Market Making
echo.
echo === STRATEGY 1: MARKET MAKING ===
echo java --add-modules java.se --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -XX:+UseNUMA -Xms2g -Xmx4g -jar target/hft-trading-system-1.0-SNAPSHOT.jar
echo.
pause

REM Test Strategy 2: Momentum
echo.
echo === STRATEGY 2: MOMENTUM ===
echo java --add-modules java.se --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -XX:+UseNUMA -Xms2g -Xmx4g -jar target/hft-trading-system-1.0-SNAPSHOT.jar
echo.
pause

REM Test Strategy 3: Triangular Arbitrage
echo.
echo === STRATEGY 3: TRIANGULAR ARBITRAGE ===
echo java --add-modules java.se --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -XX:+UseNUMA -Xms2g -Xmx4g -jar target/hft-trading-system-1.0-SNAPSHOT.jar
echo.
pause

REM Test Strategy 4: Statistical Arbitrage
echo.
echo === STRATEGY 4: STATISTICAL ARBITRAGE ===
echo java --add-modules java.se --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -XX:+UseNUMA -Xms2g -Xmx4g -jar target/hft-trading-system-1.0-SNAPSHOT.jar
echo.
pause

echo.
echo === ALL STRATEGY TESTS COMPLETE ===
pause
