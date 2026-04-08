# HFT Trading System — Professional Overview

This repository implements a high-frequency trading (HFT) research framework in Java. It includes: low-latency cores, multiple trading strategies, an offline backtesting engine, basic ML components, and risk controls. The README below focuses on how to reproduce results, the evaluation metrics (with concise math), and the latest per-strategy test results.

## Quick Links
- Build: `mvn clean package`
- Run all backtests (recommended; uses shaded JAR):

```powershell
& java -D"advml.lstm.epochs=200" -D"advml.rl.episodes=500" -D"advml.train.datafile=data\binance_BTCUSDT_1m_30d.csv" -cp "target\hft-trading-system-1.0-SNAPSHOT-shaded.jar;target\classes" com.hft.backtest.RunAllBacktests > logs\all_backtests_run_with_fetch.log 2>&1
```

- Backtest summary CSV: [logs/all_backtests_summary.csv](logs/all_backtests_summary.csv#L1-L6)

## Reproducing the Backtests (minimal steps)
1. Build: `mvn clean package`
2. (Optional) Fetch Binance historical klines into `data/binance_BTCUSDT_1m_30d.csv` using the provided `RunDataFetcher` utility.
3. Run the backtest command above. Ensure the shaded JAR is on the classpath to avoid missing logging dependencies.
4. Results are written to `logs/all_backtests_summary.csv` and detailed log `logs/all_backtests_run_with_fetch.log`.

## Per-strategy Test Results (latest run)
Summary file: [logs/all_backtests_summary.csv](logs/all_backtests_summary.csv#L1-L6)

| Strategy | Duration (ms) | Ticks | Trades | Total P&L | Ticks/sec |
|---|---:|---:|---:|---:|---:|
| MarketMaking | 259 | 100000 | 0 | 0.00 | 386100.39 |
| Momentum | 151 | 100000 | 0 | 0.00 | 662251.66 |
*** End Patch
| StatisticalArbitrage | 158 | 100000 | 0 | 0.00 | 632911.39 |

| AIEnhanced | 95 | 100000 | 0 | 0.00 | 1052631.58 |
