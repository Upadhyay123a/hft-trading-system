# Java 11 Setup for HFT Trading System

## Why Java 11?
- Better memory management for HFT
- No module restrictions (Aeron works immediately)
- Lower GC pauses
- Proven HFT production performance

## Installation Steps

### Windows:
1. Download Java 11 LTS from: https://adoptium.net/temurin/releases/?version=11
2. Install to: `C:\Program Files\Eclipse Adoptium\jdk-11.0.x-hotspot`
3. Set JAVA_HOME:
   ```cmd
   set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-11.0.x-hotspot
   set PATH=%JAVA_HOME%\bin;%PATH%
   ```

### Linux/Mac:
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-11-jdk

# macOS (with Homebrew)
brew install openjdk@11
export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
```

## Verify Installation:
```bash
java -version
# Should show: java version "11.0.x"
```

## Run HFT System:
```bash
cd hft-trading-system
mvn clean compile
java -jar target/hft-trading-system-1.0-SNAPSHOT.jar
```

## Expected Results:
✅ Aeron starts without module errors
✅ Binary encoding works (33-49 byte messages)
✅ LMAX Disruptor processes 25M+ msg/sec
✅ FIX protocol integration works
✅ All strategies run successfully
