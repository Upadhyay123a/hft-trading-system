package com.hft.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TradingDashboard extends JFrame {
    private final JPanel mainPanel;

    public TradingDashboard() {
        super("HFT Trading Dashboard");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(1500, 950));
        setSize(1500, 950);
        setLocationRelativeTo(null);

        mainPanel = buildDashboardPanel();
        setContentPane(mainPanel);
        pack();
    }

    public List<String> getSectionTitles() {
        return Arrays.asList("Overview", "Market Watch", "Strategies", "Orders", "Risk");
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            TradingDashboard dashboard = new TradingDashboard();
            dashboard.setVisible(true);
        });
    }

    private JPanel buildDashboardPanel() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(245, 247, 250));
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel content = new JPanel(new GridLayout(2, 2, 12, 12));
        content.setOpaque(false);

        OverviewCard overview = new OverviewCard(new TradingSnapshot("BTCUSDT", 68342.15, 2.35, 125000.0, 6400.75));
        PriceChartPanel chart = new PriceChartPanel();
        MarketDataTablePanel marketTable = new MarketDataTablePanel();
        StrategyStatusPanel strategy = new StrategyStatusPanel();
        StrategyMetricsPanel metrics = new StrategyMetricsPanel();
        OrderTicketPanel orderTicket = new OrderTicketPanel();
        RiskControlPanel risk = new RiskControlPanel();
        ActivityPanel activity = new ActivityPanel();

        JPanel overviewPanel = new JPanel(new BorderLayout(12, 12));
        overviewPanel.setOpaque(false);
        overviewPanel.add(overview, BorderLayout.NORTH);
        overviewPanel.add(chart, BorderLayout.CENTER);

        JPanel marketPanel = new JPanel(new BorderLayout(12, 12));
        marketPanel.setOpaque(false);
        marketPanel.add(marketTable, BorderLayout.CENTER);

        JPanel strategyPanel = new JPanel(new BorderLayout(12, 12));
        strategyPanel.setOpaque(false);
        strategyPanel.add(strategy, BorderLayout.NORTH);
        strategyPanel.add(metrics, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(12, 12));
        rightPanel.setOpaque(false);
        rightPanel.add(orderTicket, BorderLayout.NORTH);
        rightPanel.add(risk, BorderLayout.CENTER);
        rightPanel.add(activity, BorderLayout.SOUTH);

        content.add(overviewPanel);
        content.add(strategyPanel);
        content.add(marketPanel);
        content.add(rightPanel);

        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(15, 23, 42));
        header.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("Institutional Trading Console");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statusPanel.setOpaque(false);

        JLabel badge = new JLabel(" LIVE ");
        badge.setOpaque(true);
        badge.setBackground(new Color(34, 197, 94));
        badge.setForeground(Color.WHITE);
        badge.setFont(new Font("SansSerif", Font.BOLD, 12));
        badge.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel market = new JLabel("BTCUSD 68,342.15");
        market.setForeground(Color.WHITE);
        market.setFont(new Font("SansSerif", Font.BOLD, 14));

        statusPanel.add(badge);
        statusPanel.add(market);
        header.add(statusPanel, BorderLayout.EAST);
        return header;
    }
}

class TradingSnapshot {
    private final String symbol;
    private final double price;
    private final double dailyMove;
    private final double portfolioValue;
    private final double pnl;

    public TradingSnapshot(String symbol, double price, double dailyMove, double portfolioValue, double pnl) {
        this.symbol = symbol;
        this.price = price;
        this.dailyMove = dailyMove;
        this.portfolioValue = portfolioValue;
        this.pnl = pnl;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public double getDailyMove() {
        return dailyMove;
    }

    public double getPortfolioValue() {
        return portfolioValue;
    }

    public double getPnl() {
        return pnl;
    }
}

class OverviewCard extends JPanel {
    private final JLabel titleLabel = new JLabel();
    private final JLabel primaryValue = new JLabel();
    private final JLabel secondaryValue = new JLabel();

    public OverviewCard(TradingSnapshot snapshot) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        titleLabel.setText("Portfolio Overview");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));

        primaryValue.setText(String.format("%s · $%,.2f", snapshot.getSymbol(), snapshot.getPrice()));
        primaryValue.setFont(new Font("SansSerif", Font.BOLD, 24));
        primaryValue.setForeground(new Color(15, 118, 110));

        secondaryValue.setText(String.format("PnL: $%,.2f | Portfolio: $%,.2f | Daily: %.2f%%",
                snapshot.getPnl(), snapshot.getPortfolioValue(), snapshot.getDailyMove()));
        secondaryValue.setFont(new Font("SansSerif", Font.PLAIN, 13));
        secondaryValue.setForeground(new Color(51, 65, 85));

        add(titleLabel, BorderLayout.NORTH);
        add(primaryValue, BorderLayout.CENTER);
        add(secondaryValue, BorderLayout.SOUTH);
    }

    public String getTitle() {
        return titleLabel.getText();
    }

    public String getPrimaryValue() {
        return primaryValue.getText();
    }

    public String getSecondaryValue() {
        return secondaryValue.getText();
    }
}

class PriceChartPanel extends JPanel {
    private final List<Double> pricePoints = new ArrayList<>();
    private final JLabel trendLabel = new JLabel("Trend: Bullish");

    public PriceChartPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Live Price Chart"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        double base = 68342.15;
        for (int i = 0; i < 20; i++) {
            pricePoints.add(base + Math.sin(i / 2.3) * 140 + i * 18.4);
        }

        trendLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        trendLabel.setForeground(new Color(22, 101, 52));
        add(trendLabel, BorderLayout.NORTH);

        JFreeChartPanel chart = new JFreeChartPanel();
        chart.setBackground(Color.WHITE);
        add(chart, BorderLayout.CENTER);
    }

    public String getTrendLabel() {
        return trendLabel.getText();
    }

    public List<Double> getPricePoints() {
        return pricePoints;
    }

    private static class JFreeChartPanel extends JPanel {
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(15, 118, 110));
            int width = getWidth();
            int height = getHeight();
            int x = 20;
            int y = height - 20;
            int lastX = x;
            int lastY = y;
            List<Double> points = new PriceChartPanel().getPricePoints();
            for (int i = 0; i < points.size(); i++) {
                int px = x + (i * (width - 40)) / (points.size() - 1);
                int py = (int) (height - 20 - ((points.get(i) - 68000) / 160.0) * (height - 60));
                g2.drawLine(lastX, lastY, px, py);
                lastX = px;
                lastY = py;
            }
            g2.dispose();
        }
    }
}

class MarketWatchPanel extends JPanel {
    private final List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT", "SOLUSDT");

    public MarketWatchPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Market Watch"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("BTCUSDT 68,342.15  +2.35%");
        model.addElement("ETHUSDT 3,642.10   +1.82%");
        model.addElement("SOLUSDT 156.84     +4.10%");

        JList<String> list = new JList<>(model);
        list.setFixedCellHeight(28);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public int getRows() {
        return symbols.size();
    }
}

class MarketDataTablePanel extends JPanel {
    private final String[] columns = {"Symbol", "Price", "Change %", "Volume"};
    private final DefaultTableModel model;

    public MarketDataTablePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Market Data"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        model.addRow(new Object[]{"BTCUSDT", "$68,342.15", "+2.35%", "1.24M"});
        model.addRow(new Object[]{"ETHUSDT", "$3,642.10", "+1.82%", "2.31M"});
        model.addRow(new Object[]{"SOLUSDT", "$156.84", "+4.10%", "5.45M"});

        JTable table = new JTable(model);
        table.setRowHeight(28);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public List<String> getColumnNames() {
        List<String> names = new ArrayList<>();
        for (String column : columns) {
            names.add(column);
        }
        return names;
    }

    public int getRows() {
        return model.getRowCount();
    }
}

class StrategyStatusPanel extends JPanel {
    private final List<String> strategyNames = Arrays.asList("Market Making", "Momentum", "AI Enhanced");

    public StrategyStatusPanel() {
        setLayout(new GridLayout(3, 1, 8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Strategies"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        for (String strategy : strategyNames) {
            JLabel label = new JLabel("● " + strategy + " — Running");
            label.setFont(new Font("SansSerif", Font.BOLD, 13));
            label.setForeground(new Color(22, 101, 52));
            add(label);
        }
    }

    public List<String> getStrategyNames() {
        return strategyNames;
    }

    public String getStatusSummary() {
        return "Running";
    }
}

class StrategyMetricsPanel extends JPanel {
    private final List<String> metricLabels = Arrays.asList("Sharpe", "Win Rate", "Exposure");
    private final JLabel statusLabel = new JLabel("Healthy");

    public StrategyMetricsPanel() {
        setLayout(new GridLayout(4, 1, 8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Strategy Metrics"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        add(new JLabel("Sharpe: 1.82"));
        add(new JLabel("Win Rate: 63.4%"));
        add(new JLabel("Exposure: 12.5%"));
        statusLabel.setForeground(new Color(22, 101, 52));
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        add(statusLabel);
    }

    public List<String> getMetricLabels() {
        return metricLabels;
    }

    public String getStatusString() {
        return statusLabel.getText();
    }
}

class OrderTicketPanel extends JPanel {
    private final JTextField symbolField = new JTextField("BTCUSDT");
    private final JTextField quantityField = new JTextField();
    private final JComboBox<String> sideBox = new JComboBox<>(new String[]{"BUY", "SELL"});
    private final JButton submitButton = new JButton("Place Order");
    private final JLabel validationLabel = new JLabel("Ready to place order");
    private String validationMessage = "Ready to place order";
    private String lastOrderMessage = "";
    private String currentSide = "BUY";

    public OrderTicketPanel() {
        setLayout(new GridLayout(6, 1, 8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Orders"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        add(new JLabel("Symbol"));
        add(symbolField);
        add(new JLabel("Side"));
        add(sideBox);
        add(new JLabel("Quantity"));
        add(quantityField);
        add(submitButton);
        validationLabel.setForeground(new Color(51, 65, 85));
        add(validationLabel);

        sideBox.addActionListener(e -> {
            Object selected = sideBox.getSelectedItem();
            if (selected != null) {
                currentSide = selected.toString();
            }
        });
        submitButton.addActionListener(e -> placeOrder());
    }

    public void setSymbol(String symbol) {
        symbolField.setText(symbol);
    }

    public void setSide(String side) {
        currentSide = side;
        if ("BUY".equalsIgnoreCase(side) || "SELL".equalsIgnoreCase(side)) {
            sideBox.setSelectedItem(side.toUpperCase());
        }
    }

    public void setQuantity(String quantity) {
        quantityField.setText(quantity);
    }

    public boolean validateInput() {
        String symbol = symbolField.getText() == null ? "" : symbolField.getText().trim();
        String side = currentSide == null || currentSide.trim().isEmpty()
                ? (sideBox.getSelectedItem() == null ? "" : sideBox.getSelectedItem().toString())
                : currentSide.trim().toUpperCase();
        String quantityText = quantityField.getText() == null ? "" : quantityField.getText().trim();

        if (symbol.isEmpty()) {
            validationMessage = "Symbol is required.";
            validationLabel.setText(validationMessage);
            return false;
        }
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            validationMessage = "Side must be BUY or SELL.";
            validationLabel.setText(validationMessage);
            return false;
        }
        if (quantityText.isEmpty()) {
            validationMessage = "Quantity must be positive.";
            validationLabel.setText(validationMessage);
            return false;
        }
        try {
            double quantity = Double.parseDouble(quantityText);
            if (quantity <= 0) {
                validationMessage = "Quantity must be positive.";
                validationLabel.setText(validationMessage);
                return false;
            }
        } catch (NumberFormatException e) {
            validationMessage = "Quantity must be a valid number.";
            validationLabel.setText(validationMessage);
            return false;
        }

        validationMessage = "Ready to place order";
        validationLabel.setText(validationMessage);
        return true;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public void placeOrder() {
        if (!validateInput()) {
            return;
        }
        String symbol = symbolField.getText().trim();
        String side = currentSide == null || currentSide.trim().isEmpty()
                ? (sideBox.getSelectedItem() == null ? "" : sideBox.getSelectedItem().toString())
                : currentSide.trim().toUpperCase();
        String quantity = quantityField.getText().trim();
        lastOrderMessage = String.format("Order placed: %s %s %s @ %.2f", side, quantity, symbol, 68342.15);
        validationMessage = lastOrderMessage;
        validationLabel.setText(lastOrderMessage);
        quantityField.setText("");
    }

    public String getLastOrderMessage() {
        return lastOrderMessage;
    }

    public JTextField getQuantityField() {
        return quantityField;
    }

    public JTextField getSymbolField() {
        return symbolField;
    }

    public List<String> getSideOptions() {
        return Arrays.asList("BUY", "SELL");
    }

    public JButton getSubmitButton() {
        return submitButton;
    }
}

class RiskControlPanel extends JPanel {
    private final JLabel statusLabel = new JLabel("Risk: OK");

    public RiskControlPanel() {
        setLayout(new GridLayout(4, 1, 8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Risk"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        for (String label : Arrays.asList("Daily Loss", "Max Drawdown", "Position Limit")) {
            add(new JLabel(label + " = 0.00"));
        }
        statusLabel.setForeground(new Color(22, 101, 52));
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        add(statusLabel);
    }

    public List<String> getRiskLabels() {
        return Arrays.asList("Daily Loss", "Max Drawdown", "Position Limit");
    }

    public JLabel getStatusLabel() {
        return statusLabel;
    }
}

class ActivityPanel extends JPanel {
    private final List<String> activities = new ArrayList<>();

    public ActivityPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Activity"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        activities.add("Order: BUY 0.25 BTC @ 68,342.15");
        activities.add("Risk: Position within limits");
        activities.add("Strategy: Market Making rebalanced");

        DefaultListModel<String> model = new DefaultListModel<>();
        activities.forEach(model::addElement);
        JList<String> list = new JList<>(model);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    public List<String> getActivities() {
        return activities;
    }
}
