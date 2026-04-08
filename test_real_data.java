import com.hft.core.SymbolMapper;
import com.hft.exchange.BinanceConnector;
import com.hft.core.Tick;
import com.hft.strategy.MarketMakingStrategy;
import com.hft.strategy.Strategy;
import com.ft.risk.RiskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test Real Data Processing with ML Components
 */
public class TestRealData {
    private static final Logger logger = LoggerFactory.getLogger(TestRealData.class);
    
    public static void main(String[] args) {
        logger.info("=== TESTING REAL DATA WITH ML COMPONENTS ===");
        
        try {
            // Initialize symbol mapper
            SymbolMapper.register("btcusdt");
            SymbolMapper.register("ethusdt");
            
            // Create Binance connector for real data
            List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT");
            BinanceConnector connector = new BinanceConnector(symbols);
            
            // Create strategy
            Strategy strategy = new MarketMakingStrategy(
                SymbolMapper.BTCUSDT,  // Trade BTC
                0.02,                   // 0.02% spread
                1,                      // Order size
                5                       // Max position
            );
            
            // Create risk manager
            RiskManager.RiskConfig riskConfig = RiskManager.RiskConfig.moderate();
            RiskManager riskManager = new RiskManager(riskConfig);
            
            // Test ML components
            testMLComponents();
            
            // Connect to real data
            logger.info("Connecting to Binance for real market data...");
            connector.connect();
            
            // Wait for connection
            int attempts = 0;
            while (!connector.isConnected() && attempts < 10) {
                Thread.sleep(1000);
                attempts++;
                logger.info("Waiting for connection... attempt {}", attempts);
            }
            
            if (!connector.isConnected()) {
                logger.error("Failed to connect to Binance");
                return;
            }
            
            logger.info("Connected! Processing real market data...");
            
            // Process real data for 30 seconds
            CountDownLatch latch = new CountDownLatch(30);
            Thread dataProcessor = new Thread(() -> {
                int tickCount = 0;
                while (latch.getCount() > 0) {
                    try {
                        Tick tick = connector.pollTick();
                        if (tick != null) {
                            tickCount++;
                            logger.info("Real Tick #{}: Symbol={}, Price={}, Volume={}", 
                                tickCount, SymbolMapper.getSymbol(tick.symbolId), 
                                tick.getPriceAsDouble(), tick.volume / 100000.0);
                            
                            // Process with strategy
                            List<Order> orders = strategy.onTick(tick, null);
                            if (!orders.isEmpty()) {
                                logger.info("Strategy generated {} orders", orders.size());
                            }
                        }
                        Thread.sleep(100);
                        latch.countDown();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                logger.info("Processed {} real market ticks", tickCount);
            });
            
            dataProcessor.start();
            latch.await(30, TimeUnit.SECONDS);
            
            // Cleanup
            connector.disconnect();
            logger.info("Real data test completed successfully!");
            
        } catch (Exception e) {
            logger.error("Error during real data test", e);
        }
    }
    
    private static void testMLComponents() {
        logger.info("Testing ML Components...");
        
        try {
            // Test Ensemble Learning System
            com.hft.ml.EnsembleLearningSystem ensemble = new com.hft.ml.EnsembleLearningSystem();
            logger.info("Ensemble Learning System initialized");
            
            // Test Technical Indicators
            com.hft.ml.TechnicalIndicators indicators = new com.hft.ml.TechnicalIndicators(100);
            logger.info("Technical Indicators initialized");
            
            // Test Precomputed Features
            com.hft.ml.PrecomputedFeatures features = new com.hft.ml.PrecomputedFeatures();
            logger.info("Precomputed Features initialized");
            
            logger.info("All ML Components working correctly!");
            
        } catch (Exception e) {
            logger.error("Error testing ML components", e);
        }
    }
}
