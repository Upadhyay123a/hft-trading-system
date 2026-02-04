# QUICK START GUIDE

## 🚀 Get Running in 3 Minutes

### Step 1: Build
```bash
cd hft-trading-system
mvn clean package
```

### Step 2A: Live Trading (Connects to Binance)
```bash
# Linux/Mac
./run.sh

# Windows
run.bat

# Or directly with Maven
mvn exec:java -Dexec.mainClass="com.hft.Main"
```

**What happens:**
1. Connects to Binance WebSocket
2. Streams real-time BTC and ETH prices
3. Runs your chosen strategy (Market Making or Momentum)
4. Shows live statistics every 5 seconds
5. Press ENTER to stop and see final P&L

### Step 2B: Backtesting (No Internet Needed)
```bash
mvn exec:java -Dexec.mainClass="com.hft.backtest.BacktestRunner"
```

**What happens:**
1. Generates 100,000 sample ticks (first run only)
2. Replays market data through your strategy
3. Shows detailed performance metrics
4. Takes ~2-3 seconds to process 100k ticks

## 📊 What You'll See

### Live Trading Output
```
=== Trading Engine Statistics ===
Uptime: 15s
Ticks processed: 532 (35.47 tps)
Trades executed: 12
Strategy P&L: $8.45
Queue size: 0
================================

=== Order Book: 1 ===
ASKS:
  50123.4500 | 2
  50121.2300 | 5
  50120.0100 | 3
--------
Spread: 0.0200
--------
BIDS:
  50119.9900 | 4
  50118.5000 | 2
  50117.3200 | 1
```

### Backtest Output
```
╔════════════════════════════════════════════╗
║         BACKTEST RESULTS                   ║
╚════════════════════════════════════════════╝

Strategy:           MarketMaking
Duration:           2341 ms (2.34 seconds)
────────────────────────────────────────────
Ticks Processed:    100,000
Trades Executed:    1,245
Ticks per Second:   42,717.64
────────────────────────────────────────────
Total P&L:          $245.67
Max Drawdown:       $23.45
Sharpe Ratio:       1.234
```

## 🎯 Key Files to Understand

1. **Main.java** - Entry point for live trading
2. **BinanceConnector.java** - Connects to Binance WebSocket
3. **OrderBook.java** - Order matching engine
4. **MarketMakingStrategy.java** - Strategy #1
5. **MomentumStrategy.java** - Strategy #2
6. **BacktestRunner.java** - Backtesting entry point

## 🔧 Common Modifications

### Change Trading Symbols
Edit `Main.java` line ~25:
```java
List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT", "ADAUSDT");
```

### Adjust Strategy Parameters
Edit `Main.java` in `chooseStrategy()` method:
```java
// Market Making
new MarketMakingStrategy(
    SymbolMapper.BTCUSDT,
    0.01,    // ← 0.01% spread (tighter)
    2,       // ← 2 BTC per order (larger)
    10       // ← max position 10 BTC
)

// Momentum
new MomentumStrategy(
    SymbolMapper.BTCUSDT,
    50,      // ← Look back 50 ticks (longer)
    0.1,     // ← 0.1% threshold (less sensitive)
    1,       // ← 1 BTC per trade
    10       // ← max position
)
```

### Generate More Data
Edit `SampleDataGenerator.java` line ~62:
```java
generateData(filename, 1000000); // ← 1 million ticks
```

## ❓ FAQ

**Q: Do I need a Binance account?**  
A: No! The system uses public market data streams. No API keys needed for viewing.

**Q: Is this using real money?**  
A: No, this is a simulator. It processes real market data but doesn't place actual orders.

**Q: Can I add real order execution?**  
A: Yes, but you'll need Binance API keys and proper risk management. Not recommended without thorough testing.

**Q: Why Java instead of Python/C++?**  
A: Java offers good performance (~40k ticks/sec), great libraries, and easier development than C++.

**Q: How do I know if my strategy is good?**  
A: Run backtests! Look for:
- Positive P&L
- Sharpe ratio > 1.0
- Low max drawdown
- Consistent returns

**Q: The connection keeps dropping**  
A: Binance public streams are free but can be unstable. Reconnection logic can be added.

**Q: How fast is this really?**  
A: Backtests process ~40-50k ticks/second on a modern CPU. Live trading processes ticks in microseconds.

## 🎓 Learning Path

1. **Day 1**: Run live trading, observe order book and ticks
2. **Day 2**: Run backtests with both strategies, compare results
3. **Day 3**: Modify strategy parameters, see how they affect P&L
4. **Day 4**: Read OrderBook.java - understand order matching
5. **Day 5**: Read a strategy file - understand signal generation
6. **Day 6**: Create your own simple strategy
7. **Day 7**: Add logging, metrics, visualizations

## 📚 Next Steps

- Add more strategies (arbitrage, mean reversion)
- Implement risk management (stop-loss, position limits)
- Add order execution simulation with slippage
- Integrate real exchange APIs
- Build a web dashboard
- Add machine learning signals

## ⚠️ Important Reminders

✅ This is for **education only**  
✅ Test thoroughly before any real trading  
✅ Understand the risks of algorithmic trading  
✅ Start with small position sizes  
✅ Always have proper risk management  

---

**Need Help?** Check the main README.md for detailed documentation.

**Ready to trade?** Run `./run.sh` or `run.bat` and choose option 1!