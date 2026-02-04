#!/bin/bash

echo "========================================="
echo "  HFT Trading System - Build & Run"
echo "========================================="
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed!"
    echo "Please install Maven first: https://maven.apache.org/install.html"
    exit 1
fi

# Check Java version
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed!"
    echo "Please install Java 11 or higher"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo "ERROR: Java 11 or higher is required!"
    echo "Current version: $JAVA_VERSION"
    exit 1
fi

echo "✓ Java version: $JAVA_VERSION"
echo "✓ Maven found"
echo ""

# Build project
echo "Building project..."
mvn clean package -q

if [ $? -ne 0 ]; then
    echo "ERROR: Build failed!"
    exit 1
fi

echo "✓ Build successful!"
echo ""

# Menu
while true; do
    echo "Choose an option:"
    echo "  1) Run Live Trading (connect to Binance)"
    echo "  2) Run Backtesting"
    echo "  3) Generate Sample Data"
    echo "  4) Exit"
    echo ""
    read -p "Enter choice (1-4): " choice

    case $choice in
        1)
            echo ""
            echo "Starting Live Trading..."
            echo "Press ENTER anytime to stop trading"
            echo ""
            mvn exec:java -Dexec.mainClass="com.hft.Main" -q
            ;;
        2)
            echo ""
            echo "Starting Backtesting..."
            echo ""
            mvn exec:java -Dexec.mainClass="com.hft.backtest.BacktestRunner" -q
            ;;
        3)
            echo ""
            echo "Generating Sample Data..."
            echo ""
            mvn exec:java -Dexec.mainClass="com.hft.utils.SampleDataGenerator" -q
            ;;
        4)
            echo ""
            echo "Goodbye!"
            exit 0
            ;;
        *)
            echo ""
            echo "Invalid choice. Please try again."
            echo ""
            ;;
    esac
    
    echo ""
    echo "========================================="
    echo ""
done