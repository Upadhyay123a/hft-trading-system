# HFT Trading System - Architecture Overview

This document provides a high-level overview of the components, data flows, and runtime considerations for the HFT Trading System.

## Components

- `com.hft.exchange.api` — Exchange adapters (BinanceRealApi, CoinbaseRealApi). Handles REST and WebSocket connections. Binance adapter supports public market data without API keys; private endpoints require API credentials.
- `com.hft.ml` — Machine learning components: `RealTimeMLProcessor`, `HistoricalDataTrainer`, `MarketRegimeClassifier`, `LSTMPricePredictor`, `ReinforcementLearningAgent`.
- `com.hft.strategy` — Trading strategies (MarketMaking, Momentum, ML-Enhanced strategies). Strategies subscribe to tick/feature streams and emit orders.
- `com.hft.core` — Core trading engines, order representation, event routing (Disruptor integration), and persistence hooks.
- `com.hft.monitoring` — Performance and health monitoring, throughput metrics.
- `src/main/java/com/hft/demo` — Simple demos such as `BinanceSmokeDemo` for manual verification.

## Data Flow

1. Exchange adapters ingest market data via WebSocket and publish `Tick`/depth events into the Disruptor/event pipeline.
2. `RealTimeMLProcessor` consumes ticks, computes features, runs inference, and exposes `areModelsReady()` for gating downstream systems.
3. Strategies consume events and predictions to generate signals and orders.
4. `MultiExchangeManager` routes orders to appropriate exchange adapters; private endpoints require configured API keys.
5. `MLModelPersistence` serializes trained models to `models/` and supports backup/hot-swap.

## Operational Notes

- Public market data: configured to work without API keys. For private trading operations, place `exchange-api-keys.properties` in the repo root or provide credentials through `ApiKeyManager`.
- Configuration: system properties are used for runtime overrides (e.g., `binance.ws.override`, `binance.ws.connect.retries`, `binance.ws.connect.backoffMillis`).
- Testing: the repository includes an offline mocked WebSocket test (`MockedBinanceWebSocketTest`) which runs a local WebSocket server, avoiding external network dependency in CI.

## Reliability / Hardening

- WebSocket connections include retry/backoff behavior. Configure retries via `binance.ws.connect.retries` and base backoff via `binance.ws.connect.backoffMillis`.
- Consider adding exponential jitter and a reconnection supervisor for production deployments.

## How to run

- Run unit tests:

```
mvn test
```

- Run the Binance smoke demo (live):

```
cmd /c run_demo.bat
```

## CI

CI is configured in `.github/workflows/ci.yml`. It builds the project and runs unit tests, and specifically runs the mocked WebSocket test to ensure CI reliability.
