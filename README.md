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
| TriangularArbitrage | 131 | 100000 | 0 | 0.00 | 763358.78 |
| StatisticalArbitrage | 158 | 100000 | 0 | 0.00 | 632911.39 |
| AIEnhanced | 95 | 100000 | 0 | 0.00 | 1052631.58 |
| AdvancedML | 1408 | 100000 | 0 | 0.00 | 71022.73 |

Notes:
- The `AdvancedML` run used a small synthetic bootstrap (200 samples) because `data/binance_BTCUSDT_1m_30d.csv` was not present when the run started. To validate ML-driven behavior, fetch the real CSV and re-run training-backed backtests.

## Evaluation Metrics (definitions & examples)
All metrics below are reproducible from backtest traces and P&L time series.

- Ticks per second (throughput):
  $${\mathrm{ticks/sec}}=\dfrac{\mathrm{ticks\ processed}}{\mathrm{duration\ (s)}}$$

- Cumulative P&L: sum of executed-trade profit/loss over the run.

- Sharpe Ratio (annualized, simplifying to sample returns):
  $$\mathrm{Sharpe} = \dfrac{\bar{r}-r_f}{\sigma_r},$$
  where $\bar{r}$ is mean periodic return, $r_f$ is the risk-free rate, and $\sigma_r$ is return standard deviation.

- Maximum Drawdown (fraction):
  $$\mathrm{MaxDrawdown}=\max_{t}\left(\dfrac{\mathrm{peak\ equity\ before\ }t - \mathrm{min\ equity\ after\ peak}}{\mathrm{peak\ equity\ before\ }t}\right).$$

Example (ticks/sec using AdvancedML row):
$$\mathrm{ticks/sec}=\dfrac{100000}{1408/1000}=71022.73\ \text{ticks/sec}$$

Example (Sharpe — conceptual): compute per-tick returns from P&L series, take mean and stddev, then apply the formula above. (This repository exports P&L traces in logs when enabled.)

## How to Interpret Results
- Trades = 0 in these synthetic test runs means the sample dataset or strategy parameters did not produce fills; this is expected for synthetic or low-liquidity datasets.
- Throughput numbers reflect single-machine offline backtesting performance, not live exchange latency.
- For ML strategies, check that models are trained from the intended dataset. If a training file is not found, the system falls back to a synthetic bootstrap (not suitable for production evaluation).

## Reproducible Example: Train AdvancedML on fetched data
1. Fetch data (example CLI):
```powershell
& java -cp "target\hft-trading-system-1.0-SNAPSHOT-shaded.jar;target\classes" com.hft.utils.RunDataFetcher
```
2. Verify `data/binance_BTCUSDT_1m_30d.csv` exists.
3. Run backtests (quotes the `-D` flags in PowerShell):
```powershell
& java -D"advml.lstm.epochs=200" -D"advml.rl.episodes=500" -D"advml.train.datafile=data\binance_BTCUSDT_1m_30d.csv" -cp "target\hft-trading-system-1.0-SNAPSHOT-shaded.jar;target\classes" com.hft.backtest.RunAllBacktests > logs\all_backtests_run_with_fetch.log 2>&1
```

## Troubleshooting & Notes
- PowerShell argument parsing: quote `-D` system properties as shown above.
- Always include the shaded JAR on the classpath to avoid missing SLF4J or other runtime deps.
- If ML results look unstable, increase `advml.lstm.epochs` and provide a larger training dataset.

## Contributing & Next Steps
- Tests live in `src/test/java` and CI should run `mvn test` and `mvn -DskipTests=false package` before merging.
- Suggested immediate improvements:
  1. Add end-to-end deterministic fixtures for ML training to avoid synthetic bootstrap in CI.
  2. Persist per-tick P&L traces to `logs/traces/` for reproducible Sharpe/drawdown calculation.

## License & Disclaimer
MIT License. This software is for research and educational use only. Do not use with real funds without professional risk management and exhaustive testing.

## Loss Plot (AdvancedML)

You can generate a CSV and PNG of reported batch losses (parsed from `logs/all_backtests_run_with_fetch.log`) with the included script:

```powershell
python docs/generate_loss_plot.py
```

Outputs:
- `docs/advml_loss.csv` — indexed CSV of reported losses
- `docs/advml_loss.png` — simple line plot of loss vs batch index

If you want the fully-detailed original README (mathematical derivations, exhaustive examples, and the prior verbose documentation), see `docs/README_FULL.md`.

## AdvancedML Training Report (latest run)

Parsed training summary (from `logs/all_backtests_run_with_fetch.log`):

- Reported batch summaries: 21
- Avg Loss statistics (reported values): mean = 0.0737, min = 0.0532, max = 0.1723, stddev ≈ 0.0254
- Context: The run logged multiple "Training Epoch 0" batch summaries. The configured training file was missing, so a synthetic bootstrap dataset was used; results reflect that bootstrap.

ASCII loss sparkline (index: bar, value):

1: ████ 0.064279
2: ████ 0.065003
3: ██████ 0.072063
4: ██ 0.058029
5: ███ 0.062501
6: ███ 0.062970
7: ██ 0.059640
8: ██ 0.059267
9: ███████ 0.073166
10: ██████ 0.070726
11: ██████████ 0.082860
12: ████ 0.063703
13: ████ 0.064533
14: ███████ 0.073921
15: █████ 0.067002
16: ██████████████ 0.104437
17:  0.053211
18: █████████████ 0.098848
19: █ 0.054368
20: ████ 0.065455
21: █████████████████████████████████████████ 0.172260

Recommendations:
- Provide a proper training dataset at `data/binance_BTCUSDT_1m_30d.csv` and re-run with increased epochs (example: `advml.lstm.epochs=1000`).
- Persist per-epoch losses to `logs/training/advml_loss.csv` for plot generation and CI validation.
- Normalize features and save scalers under `models/` so production inference uses the same preprocessing.

