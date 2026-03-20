@echo off
echo === HFT Trading System - Java 11 Optimized ===
echo.
echo Using Java 11 for optimal HFT performance:
echo - Better GC behavior
echo - Lower latency
echo - More predictable performance
echo.
echo Compiling and running system...
echo.

REM Compile with Java 11
call mvn clean compile package -q

REM Run with proper JVM arguments for Aeron
echo Starting HFT System with JVM optimizations...
echo.
java --add-modules java.se ^
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
     -XX:+UseG1GC ^
     -XX:MaxGCPauseMillis=200 ^
     -XX:+UseStringDeduplication ^
     -Xms512m -Xmx2g ^
     -jar target/hft-trading-system-1.0-SNAPSHOT.jar

echo.
echo === System Shutdown ===
pause
