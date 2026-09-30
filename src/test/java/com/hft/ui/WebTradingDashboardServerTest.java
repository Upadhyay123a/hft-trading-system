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
        } finally {
            server.stop();
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
