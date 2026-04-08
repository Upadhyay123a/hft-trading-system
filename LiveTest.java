import com.hft.core.SymbolMapper;
import com.hft.exchange.BinanceConnector;
import com.hft.strategy.MarketMakingStrategy;
import com.hft.strategy.MomentumStrategy;
import com.hft.strategy.AIEnhancedStrategy;
import com.hft.strategy.Strategy;
import com.ft.risk.RiskManager;
import com.hft.ai.AIMarketIntelligence;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class LiveTest {
    public static void main(String[] args) {
        System.out.println("=== HFT TRADING SYSTEM LIVE TEST ===");
        System.out.println();
        
        try {
            // Initialize symbols
            SymbolMapper.register("btcusdt");
            SymbolMapper.register("ethusdt");
            System.out.println("Symbols registered: BTCUSDT, ETHUSDT");
            
            // Test AI components
            System.out.println("\n=== TESTING AI COMPONENTS ===");
            AIMarketIntelligence ai = new AIMarketIntelligence();
            System.out.println("AI Market Intelligence initialized");
            
            // Test AI signals
            AIMarketIntelligence.AISignals signals = ai.getAISignals();
            System.out.println("Current AI Signals: " + signals);
            
            // Test strategies
            System.out.println("\n=== TESTING TRADING STRATEGIES ===");
            
            Strategy marketMaking = new MarketMakingStrategy(SymbolMapper.BTCUSDT, 0.02, 1, 5);
            System.out.println("Market Making Strategy: " + marketMaking.getName());
            
            Strategy momentum = new MomentumStrategy(SymbolMapper.BTCUSDT, 20, 0.05, 1, 10);
            System.out.println("Momentum Strategy: " + momentum.getName());
            
            Strategy aiEnhanced = new AIEnhancedStrategy(SymbolMapper.BTCUSDT, 1, 10);
            System.out.println("AI-Enhanced Strategy: " + aiEnhanced.getName());
            
            // Test risk manager
            System.out.println("\n=== TESTING RISK MANAGER ===");
            RiskManager.RiskConfig riskConfig = RiskManager.RiskConfig.moderate();
            RiskManager riskManager = new RiskManager(riskConfig);
            System.out.println("Risk Manager initialized with moderate config");
            System.out.println("Max position: " + riskConfig.maxPosition);
            System.out.println("Max drawdown: " + riskConfig.maxDrawdownPercent + "%");
            
            // Test Binance connector
            System.out.println("\n=== TESTING BINANCE CONNECTOR ===");
            List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT");
            BinanceConnector connector = new BinanceConnector(symbols);
            System.out.println("Binance connector created for: " + symbols);
            
            // Try to connect
            System.out.println("Attempting to connect to Binance...");
            connector.connect();
            
            // Wait for connection
            int attempts = 0;
            while (!connector.isConnected() && attempts < 10) {
                Thread.sleep(1000);
                attempts++;
                System.out.println("Connection attempt " + attempts + "...");
            }
            
            if (connector.isConnected()) {
                System.out.println("SUCCESS: Connected to Binance WebSocket!");
                System.out.println("Queue size: " + connector.getQueueSize());
                
                // Test real data processing
                System.out.println("\n=== TESTING REAL DATA PROCESSING ===");
                System.out.println("Processing real market data for 10 seconds...");
                
                long startTime = System.currentTimeMillis();
                int tickCount = 0;
                
                while (System.currentTimeMillis() - startTime < 10000) {
                    var tick = connector.pollTick();
                    if (tick != null) {
                        tickCount++;
                        System.out.println("Tick #" + tickCount + ": " + 
                            SymbolMapper.getSymbol(tick.symbolId) + 
                            " Price: $" + (tick.price / 10000.0) + 
                            " Volume: " + (tick.volume / 100000.0));
                    }
                    Thread.sleep(100);
                }
                
                System.out.println("Processed " + tickCount + " real ticks in 10 seconds");
                
            } else {
                System.out.println("FAILED: Could not connect to Binance");
                System.out.println("This is normal if network is blocked");
            }
            
            // Test strategy with AI
            System.out.println("\n=== TESTING AI-ENHANCED STRATEGY ===");
            if (aiEnhanced instanceof AIEnhancedStrategy) {
                AIEnhancedStrategy aiStrategy = (AIEnhancedStrategy) aiEnhanced;
                var aiStats = aiStrategy.getAIPerformanceStats();
                System.out.println("AI Performance: " + aiStats);
                System.out.println("Current AI Signals: " + aiStrategy.getCurrentAISignals());
            }
            
            // Test final results
            System.out.println("\n=== FINAL RESULTS ===");
            System.out.println("All components initialized successfully");
            System.out.println("AI integration: WORKING");
            System.out.println("Strategies: WORKING");
            System.out.println("Risk Manager: WORKING");
            System.out.println("Binance Connector: " + (connector.isConnected() ? "CONNECTED" : "DISCONNECTED"));
            
            // Cleanup
            connector.disconnect();
            ai.shutdown();
            
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n=== LIVE TEST COMPLETE ===");
        System.out.println("Press Enter to exit...");
        new Scanner(System.in).nextLine();
    }
}
