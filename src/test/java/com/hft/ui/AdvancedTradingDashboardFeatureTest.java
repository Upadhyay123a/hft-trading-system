package com.hft.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancedTradingDashboardFeatureTest {

    @Test
    void chartPanelReportsMarketTrendAndPoints() {
        PriceChartPanel chart = new PriceChartPanel();

        assertThat(chart.getTrendLabel()).contains("Trend");
        assertThat(chart.getPricePoints()).isNotEmpty();
        assertThat(chart.getPricePoints().size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void marketTableContainsLiveTickerRows() {
        MarketDataTablePanel table = new MarketDataTablePanel();

        assertThat(table.getColumnNames()).contains("Symbol", "Price", "Change %", "Volume");
        assertThat(table.getRows()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void strategyMetricsPanelShowsKeyOperationalStats() {
        StrategyMetricsPanel panel = new StrategyMetricsPanel();

        assertThat(panel.getMetricLabels()).contains("Sharpe", "Win Rate", "Exposure");
        assertThat(panel.getStatusString()).contains("Healthy");
    }

    @Test
    void orderFormRejectsInvalidQuantityAndSide() {
        OrderTicketPanel panel = new OrderTicketPanel();

        panel.setSymbol("BTCUSDT");
        panel.setSide("BUY");
        panel.setQuantity("0");

        assertThat(panel.validateInput()).isFalse();
        assertThat(panel.getValidationMessage()).contains("positive");

        panel.setQuantity("1.5");
        panel.setSide("INVALID");
        assertThat(panel.validateInput()).isFalse();
        assertThat(panel.getValidationMessage()).contains("BUY");
    }

    @Test
    void placingOrderAddsActivityEntryAndResetsForm() {
        OrderTicketPanel panel = new OrderTicketPanel();
        panel.setSymbol("ETHUSDT");
        panel.setSide("SELL");
        panel.setQuantity("2.5");

        panel.placeOrder();

        assertThat(panel.getLastOrderMessage()).contains("ETHUSDT");
        assertThat(panel.getQuantityField().getText()).isEmpty();
        assertThat(panel.getLastOrderMessage()).contains("SELL");
    }
}
