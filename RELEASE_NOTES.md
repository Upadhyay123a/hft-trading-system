Release candidate prepared by automation.

Changes:
- Added retries and jitter + reconnect supervisor to `BinanceRealApi`.
- Added CI workflow running unit tests, mocked WS test and an integration job.
- Added MIT license and architecture documentation.
- Added mocked WebSocket test for Binance API.

Tag: v1.0.0-rc1

Notes:
- CI runs mocked WS test to avoid external network dependencies.
- Configure system properties for runtime behavior:
  - `binance.ws.autoReconnect` (true/false)
  - `binance.ws.connect.retries` (int)
  - `binance.ws.connect.backoffMillis` (ms)

To push branch and tags:

```
# push branch
 git push -u origin feature/ci-mock-ws-retries-mit-arch
# push tags
 git tag -a v1.0.0-rc1 -m "Release candidate v1.0.0-rc1"
 git push origin --tags
```
