package com.hft.ui;

import javax.swing.JPanel;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class TradingDashboardComponentTest {

    @Test
    void overviewCardDisplaysPortfolioMetrics() {
        TradingSnapshot snapshot = new TradingSnapshot(
                "BTCUSDT",
                68342.15,
                2.35,
                125000.0,
                6400.75
        );

        OverviewCard card = new OverviewCard(snapshot);

        assertThat(card.getTitle()).isEqualTo("Portfolio Overview");
        assertThat(card.getPrimaryValue()).contains("BTCUSDT");
        assertThat(card.getSecondaryValue()).contains("PnL");
    }

    @Test
    void marketWatchPanelContainsSymbolsAndPrices() {
        MarketWatchPanel panel = new MarketWatchPanel();

        assertThat(panel.getSymbols()).contains("BTCUSDT", "ETHUSDT", "SOLUSDT");
        assertThat(panel.getRows()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void strategyPanelShowsAllStrategies() {
        StrategyStatusPanel panel = new StrategyStatusPanel();

        assertThat(panel.getStrategyNames()).contains("Market Making", "Momentum", "AI Enhanced");
        assertThat(panel.getStatusSummary()).contains("Running");
    }

    @Test
    void orderTicketPanelBuildsOrderForm() {
        OrderTicketPanel panel = new OrderTicketPanel();

        assertThat(panel.getSymbolField().getText()).isEqualTo("BTCUSDT");
        assertThat(panel.getSideOptions()).contains("BUY", "SELL");
        assertThat(panel.getSubmitButton().getText()).isEqualTo("Place Order");
    }

    @Test
    void riskPanelListsKeyControls() {
        RiskControlPanel panel = new RiskControlPanel();

        assertThat(panel.getRiskLabels()).contains("Daily Loss", "Max Drawdown", "Position Limit");
        assertThat(panel.getStatusLabel().getText()).contains("OK");
    }

    @Test
    void activityPanelShowsRecentEventItems() {
        ActivityPanel panel = new ActivityPanel();

        assertThat(panel.getActivities()).isNotEmpty();
        assertThat(panel.getActivities().get(0)).contains("Order");
    }

    @Test
    void dashboardContainsAllMajorSections() {
        TradingDashboard dashboard = new TradingDashboard();

        assertThat(dashboard.getSectionTitles()).contains(
                "Overview",
                "Market Watch",
                "Strategies",
                "Orders",
                "Risk"
        );
        assertThat(dashboard.getMainPanel()).isInstanceOf(JPanel.class);
    }
}
