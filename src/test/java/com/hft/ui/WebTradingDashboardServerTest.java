package com.hft.ui;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class WebTradingDashboardServerTest {

    @Test
    void servesDashboardHtmlAndRealtimeMarketJson() throws Exception {
        WebTradingDashboardServer server = new WebTradingDashboardServer(0);
        server.start();

        try {
            String html = readUrl("http://localhost:" + server.getPort() + "/");
            assertThat(html).contains("HFT Trading Console");
            assertThat(html).contains("chart");

            String marketJson = readUrl("http://localhost:" + server.getPort() + "/api/market");
            assertThat(marketJson).contains("BTCUSDT");
            assertThat(marketJson).contains("ETHUSDT");
            assertThat(marketJson).contains("strategy");
            assertThat(marketJson).contains("source");
            assertThat(marketJson).contains("history");
            assertThat(marketJson).contains("binance");
        } finally {
            server.stop();
        }
    }

    @Test
    void prefersStreamMetadataWhenLiveFeedIsPresent() throws Exception {
        WebTradingDashboardServer.MarketDataFeed.setCurrentSnapshot(
                new WebTradingDashboardServer.MarketSnapshot(
                        "binance-stream",
                        "Market Making",
                        java.util.List.of(100.0, 101.0, 100.5),
                        java.util.List.of(java.util.Map.of(
                                "symbol", "BTCUSDT",
                                "price", 100.5,
                                "changePct", 0.4,
                                "volume", "1.2M"
                        )),
                        1.9,
                        64.3,
                        11.2,
                        "Healthy",
                        "binance-stream"
                )
        );

        WebTradingDashboardServer server = new WebTradingDashboardServer(0);
        server.start();

        try {
            String marketJson = readUrl("http://localhost:" + server.getPort() + "/api/market");
            assertThat(marketJson).contains("\"feed\":\"binance-stream\"");
            assertThat(marketJson).contains("\"source\":\"binance-stream\"");
        } finally {
            server.stop();
            WebTradingDashboardServer.MarketDataFeed.clearCurrentSnapshot();
        }
    }

    private String readUrl(String url) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }
}
