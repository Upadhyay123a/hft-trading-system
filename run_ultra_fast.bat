@echo off
echo === ULTRA-FAST HFT TRADING SYSTEM ===
echo.
echo MAXIMUM SPEED OPTIMIZATIONS:
echo - BusySpinWaitStrategy (no thread blocking)
echo - MAX thread priority 
echo - 256K ring buffer (4x larger)
echo - CPU affinity to trading threads
echo - Lock-free data structures
echo - Sub-microsecond latency target
echo.

REM Compile with optimizations
call mvn clean compile package -q

echo Starting ULTRA-FAST HFT System...
echo.

REM Ultra-fast JVM configuration
java --add-modules java.se ^
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
     -XX:+UseG1GC ^
     -XX:MaxGCPauseMillis=50 ^
     -XX:+UnlockExperimentalVMOptions ^
     -XX:+UseStringDeduplication ^
     -XX:+UseCompressedOops ^
     -XX:+UseCompressedClassPointers ^
     -XX:+AggressiveOpts ^
     -XX:+UseNUMA ^
     -XX:+UseParallelGC ^
     -XX:ParallelGCThreads=4 ^
     -XX:ConcGCThreads=2 ^
     -XX:+AlwaysPreTouch ^
     -XX:+UseLargePages ^
     -XX:NewSize=512m ^
     -XX:MaxNewSize=512m ^
     -XX:OldSize=1g ^
     -Xms2g ^
     -Xmx4g ^
     -XX:+UseBiasedLocking ^
     -XX:+UseFastUnorderedTimeStamps ^
     -XX:+OptimizeStringConcat ^
     -XX:+DoEscapeAnalysis ^
     -XX:+UseCompressedOops ^
     -XX:+UseCompressedClassPointers ^
     -XX:+UseG1GC ^
     -XX:MaxGCPauseMillis=50 ^
     -XX:G1HeapRegionSize=16m ^
     -XX:+UseStringDeduplication ^
     -XX:+OptimizeStringConcat ^
     -XX:+DoEscapeAnalysis ^
     -jar target/hft-trading-system-1.0-SNAPSHOT.jar

echo.
echo === Ultra-Fast System Shutdown ===
pause
