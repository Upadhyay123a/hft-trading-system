@echo off
echo Running HFT System with Java 21+ Module Workaround
echo.

REM Set JVM flags to allow module access for Aeron
set JVM_FLAGS=--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED

echo Starting Ultra-High Performance HFT Trading System...
echo Binary Encoding + LMAX Disruptor + Aeron + FIX Protocol
echo.

java %JVM_FLAGS% -jar target/hft-trading-system-1.0-SNAPSHOT.jar

pause
