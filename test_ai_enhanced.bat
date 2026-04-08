@echo off
echo ========================================
echo AI-ENHANCED HFT TRADING SYSTEM TEST
echo ========================================
echo.
echo This test demonstrates:
echo - Google Gemini AI integration
echo - Perplexity AI integration  
echo - AI-powered market intelligence
echo - Sentiment-based trading decisions
echo - Real-time news analysis
echo - AI risk assessment
echo.
echo AI Features:
echo - News sentiment analysis (BULLISH/BEARISH/NEUTRAL)
echo - Market trend prediction (UPTREND/DOWNTREND/SIDEWAYS)
echo - Risk assessment (LOW/MEDIUM/HIGH)
echo - Event monitoring (News/Regulation/Whale movements)
echo - Dynamic position sizing based on AI confidence
echo.
echo WITHOUT API KEYS: System uses mock AI responses
echo WITH API KEYS: System uses real AI analysis
echo.
echo To configure API keys:
echo set GEMINI_API_KEY=your-gemini-key
echo set PERPLEXITY_API_KEY=your-perplexity-key
echo.
pause

echo.
echo === STARTING AI-ENHANCED HFT SYSTEM ===
echo.

cd /d "d:\hft-trading-system"

echo Step 1: Compiling with AI dependencies...
call mvn clean compile package -q

echo.
echo Step 2: Starting AI-Enhanced Trading System...
echo.
echo Choose option 5 for AI-Enhanced Strategy when prompted
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
     -DGEMINI_API_KEY=%GEMINI_API_KEY% ^
     -DPERPLEXITY_API_KEY=%PERPLEXITY_API_KEY% ^
     -jar target/hft-trading-system-1.0-SNAPSHOT.jar

echo.
echo === AI-ENHANCED TEST COMPLETED ===
echo.
echo Check the logs above for:
echo - AI sentiment analysis results
echo - Market trend predictions
echo - Risk assessment scores
echo - AI-driven trading signals
echo - Performance improvements
echo.
pause
