package com.hft.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TradingDashboard extends JFrame {
    private final JPanel mainPanel;

    public TradingDashboard() {
        super("HFT Trading Dashboard");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(1400, 900));
        setSize(1400, 900);
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

        JPanel header = buildHeader();
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(2, 2, 12, 12));
        center.setOpaque(false);

        OverviewCard overview = new OverviewCard(new TradingSnapshot("BTCUSDT", 68342.15, 2.35, 125000.0, 6400.75));
        MarketWatchPanel watch = new MarketWatchPanel();
        StrategyStatusPanel strategy = new StrategyStatusPanel();
        OrderTicketPanel orderTicket = new OrderTicketPanel();
        RiskControlPanel risk = new RiskControlPanel();
        ActivityPanel activity = new ActivityPanel();

        JPanel left = new JPanel(new BorderLayout(12, 12));
        left.setOpaque(false);
        left.add(overview, BorderLayout.NORTH);
        left.add(watch, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(12, 12));
        right.setOpaque(false);
        right.add(strategy, BorderLayout.NORTH);
        right.add(orderTicket, BorderLayout.CENTER);

        JPanel bottomLeft = new JPanel(new BorderLayout(12, 12));
        bottomLeft.setOpaque(false);
        bottomLeft.add(risk, BorderLayout.CENTER);

        JPanel bottomRight = new JPanel(new BorderLayout(12, 12));
        bottomRight.setOpaque(false);
        bottomRight.add(activity, BorderLayout.CENTER);

        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.setOpaque(false);
        grid.add(left);
        grid.add(right);
        grid.add(bottomLeft);
        grid.add(bottomRight);

        root.add(grid, BorderLayout.CENTER);
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
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(148, 163, 184)),
                "Overview",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("SansSerif", Font.BOLD, 14),
                new Color(30, 41, 59)
        ));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

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

class MarketWatchPanel extends JPanel {
    private final java.util.List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT", "SOLUSDT");

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

    public java.util.List<String> getSymbols() {
        return symbols;
    }

    public int getRows() {
        return symbols.size();
    }
}

class StrategyStatusPanel extends JPanel {
    private final java.util.List<String> strategyNames = Arrays.asList("Market Making", "Momentum", "AI Enhanced");

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

    public java.util.List<String> getStrategyNames() {
        return strategyNames;
    }

    public String getStatusSummary() {
        return "Running";
    }
}

class OrderTicketPanel extends JPanel {
    private final JTextField symbolField = new JTextField("BTCUSDT");
    private final JComboBox<String> sideBox = new JComboBox<>(new String[]{"BUY", "SELL"});
    private final JButton submitButton = new JButton("Place Order");

    public OrderTicketPanel() {
        setLayout(new GridLayout(4, 1, 8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Orders"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        JLabel symbolLabel = new JLabel("Symbol");
        add(symbolLabel);
        add(symbolField);

        JLabel sideLabel = new JLabel("Side");
        add(sideLabel);
        add(sideBox);
        add(submitButton);
    }

    public JTextField getSymbolField() {
        return symbolField;
    }

    public java.util.List<String> getSideOptions() {
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

    public java.util.List<String> getRiskLabels() {
        return Arrays.asList("Daily Loss", "Max Drawdown", "Position Limit");
    }

    public JLabel getStatusLabel() {
        return statusLabel;
    }
}

class ActivityPanel extends JPanel {
    private final java.util.List<String> activities = Arrays.asList(
            "Order: BUY 0.25 BTC @ 68,342.15",
            "Risk: Position within limits",
            "Strategy: Market Making rebalanced"
    );

    public ActivityPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Activity"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        DefaultListModel<String> model = new DefaultListModel<>();
        activities.forEach(model::addElement);
        JList<String> list = new JList<>(model);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    public java.util.List<String> getActivities() {
        return activities;
    }
}
