# HFT Trading System

A high-frequency trading system built in Java with real-time market data from Binance, order book management, multiple trading strategies, and backtesting capabilities.

## Features

✅ **Real-time Market Data**: Connects to Binance WebSocket for live cryptocurrency prices  
✅ **High-Performance Order Book**: O(log n) price level matching with fast lookups  
✅ **Multiple Strategies**: Market Making and Momentum strategies included  
✅ **Backtesting Engine**: Test strategies on historical data with performance metrics  
✅ **Low Latency Design**: Primitive types, object pooling, lock-free data structures  
✅ **Binary Protocol Ready**: Foundation for custom binary protocols  

## Project Structure

```
hft-trading-system/
├── src/main/java/com/hft/
│   ├── core/               # Core data models (Tick, Order, Trade, etc.)
│   ├── orderbook/          # Order book implementation
│   ├── exchange/           # Exchange connectors (Binance WebSocket)
│   ├── strategy/           # Trading strategies
│   ├── backtest/           # Backtesting engine
│   └── utils/              # Utilities (data generation, etc.)
├── data/                   # Market data files
└── pom.xml                 # Maven configuration
```

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Internet connection (for live trading with Binance)

## Quick Start

### 1. Build the Project

```bash
cd hft-trading-system
mvn clean package
```

### 2. Run Live Trading

Connect to Binance and trade in real-time:

```bash
mvn exec:java -Dexec.mainClass="com.hft.Main"
```

**Choose your strategy:**
- **1**: Market Making (provides liquidity, captures spread)
- **2**: Momentum (follows price trends)

Press ENTER to stop trading and see final statistics.

### 3. Run Backtesting

Test strategies on historical data:

```bash
mvn exec:java -Dexec.mainClass="com.hft.backtest.BacktestRunner"
```

First run will generate 100,000 sample ticks (~50 MB).

### 4. Generate Sample Data

Generate custom sample data:

```bash
mvn exec:java -Dexec.mainClass="com.hft.utils.SampleDataGenerator"
```

## Strategies Explained

### Market Making Strategy
- Places buy and sell orders around mid-price
- Captures the bid-ask spread
- Manages inventory position limits
- Best for: Low volatility, high volume markets

**Parameters:**
- `spreadPercent`: Distance from mid-price (e.g., 0.02 = 0.02%)
- `orderSize`: Quantity per order
- `maxPosition`: Maximum inventory position

### Momentum Strategy
- Detects price trends using recent price history
- Buys on upward momentum, sells on downward
- Rate-limited to prevent overtrading
- Best for: Trending markets

**Parameters:**
- `lookbackPeriod`: Number of ticks to analyze (e.g., 20)
- `threshold`: Price change % to trigger trade (e.g., 0.05%)
- `orderSize`: Quantity per trade
- `maxPosition`: Maximum position size

## Architecture Highlights

### Order Book (`OrderBook.java`)
- **TreeMap-based price levels** for O(log n) operations
- **Fast order lookup** via HashMap
- **Multiple order types**: Limit, Market, IOC (Immediate or Cancel), FOK (Fill or Kill)
- **Trade matching**: Price-time priority

### Trading Engine (`TradingEngine.java`)
- **Single-threaded processing** for deterministic behavior
- **Statistics tracking**: Ticks/second, trades executed, P&L
- **Strategy coordination**: Feeds ticks to strategy, executes orders

### Binance Connector (`BinanceConnector.java`)
- **WebSocket streaming** for real-time data
- **Non-blocking queue** for tick buffering
- **Multiple symbol support**
- **JSON parsing** with Gson

## Performance Considerations

This system uses several HFT optimization techniques:

1. **Primitive Types**: All prices stored as `long` (price * 10000) to avoid floating-point
2. **Pre-allocation**: Order books and data structures created upfront
3. **Lock-Free**: AtomicLong for counters, minimal synchronization
4. **Zero-Copy**: Direct ByteBuffer support (foundation for binary protocols)
5. **Object Pooling**: Reusable Tick objects (ready for extension)

## Sample Output

### Live Trading
```
=== Trading Engine Statistics ===
Uptime: 30s
Ticks processed: 1247 (41.57 tps)
Trades executed: 23
Strategy P&L: $12.34
Queue size: 0
================================
```

### Backtesting
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
────────────────────────────────────────────
```

## Extending the System

### Add a New Strategy

1. Create a class implementing `Strategy` interface
2. Implement `onTick()` to generate orders based on market data
3. Implement `onTrade()` to track executions
4. Add to strategy selection in `Main.java`

Example:
```java
public class MyStrategy implements Strategy {
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        // Your logic here
        return orders;
    }
    
    @Override
    public void onTrade(Trade trade) {
        // Track your trades
    }
}
```

### Add a New Exchange

1. Create a connector class (like `BinanceConnector`)
2. Implement WebSocket or REST connection
3. Parse messages into `Tick` objects
4. Feed into `BlockingQueue<Tick>`

## Configuration

### Change Symbols
Edit `Main.java`:
```java
List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT", "BNBUSDT");
```

### Adjust Strategy Parameters
Edit strategy creation in `Main.java`:
```java
new MarketMakingStrategy(
    SymbolMapper.BTCUSDT,
    0.02,    // 0.02% spread
    1,       // order size
    5        // max position
)
```

## Data Format

CSV format for backtesting:
```
timestamp,symbolId,price,volume,side
1707043200000000000,1,500000000,100000,0
```

- `timestamp`: Nanoseconds since epoch
- `symbolId`: Integer symbol ID (from SymbolMapper)
- `price`: Price * 10000 (4 decimal places)
- `volume`: Volume amount
- `side`: 0 = Buy, 1 = Sell

## Troubleshooting

**Connection Failed**: 
- Check internet connection
- Verify Binance API is accessible
- Check firewall settings

**OutOfMemoryError**:
- Increase JVM heap: `mvn exec:java -Dexec.args="-Xmx2g"`

**No Trades Executing**:
- Check strategy parameters (spread, threshold)
- Verify order book has liquidity
- Check position limits

## License

MIT License - Feel free to modify and use for learning/trading.

## Disclaimer

⚠️ **This is for educational purposes only.**  
⚠️ **Do not use for real trading without thorough testing.**  
⚠️ **Cryptocurrency trading carries significant risk.**

## Next Steps

- [ ] Add risk management (stop-loss, position limits)
- [ ] Implement more strategies (arbitrage, statistical)
- [ ] Add order execution simulation
- [ ] Integrate with real exchange APIs (requires API keys)
- [ ] Add performance profiling
- [ ] Implement FIX protocol support
- [ ] Add multi-threaded processing for higher throughput

## Resources

- [Binance WebSocket API](https://binance-docs.github.io/apidocs/spot/en/#websocket-market-streams)
- [HFT Best Practices](https://www.quantstart.com/articles/)
- [Order Book Algorithms](https://web.archive.org/web/20110219163448/http://howtohft.wordpress.com/2011/02/15/how-to-build-a-fast-limit-order-book/)

---

**Happy Trading! 🚀**