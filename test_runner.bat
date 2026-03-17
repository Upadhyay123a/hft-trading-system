@echo off
echo === RUNNING STRATEGY TESTS WITH SIMULATED DATA ===
echo.
echo Test 1: All Strategies with Real Data Simulation
java -cp "target/classes;target/hft-trading-system-1.0-SNAPSHOT.jar" com.hft.test.AllStrategiesRealDataTest
echo.
echo Test 2: Real Data Comprehensive Test
java -cp "target/classes;target/hft-trading-system-1.0-SNAPSHOT.jar" com.hft.test.RealDataComprehensiveTest
echo.
echo Test 3: Priority 2 Advanced Features Test
java -cp "target/classes;target/hft-trading-system-1.0-SNAPSHOT.jar" com.hft.test.Priority2AdvancedTest
echo.
echo Test 4: Real Data ML Integration Test
java -cp "target/classes;target/hft-trading-system-1.0-SNAPSHOT.jar" com.hft.test.RealDataMLIntegrationTest
echo.
echo === ALL TESTS COMPLETED ===
pause
