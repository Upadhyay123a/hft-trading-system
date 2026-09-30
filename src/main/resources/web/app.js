const priceChart = document.getElementById('priceChart');
const chartCtx = priceChart.getContext('2d');
const marketTableBody = document.querySelector('#marketTable tbody');
const strategyList = document.getElementById('strategyList');
const strategyMetrics = document.getElementById('strategyMetrics');
const activityFeed = document.getElementById('activityFeed');
const orderForm = document.getElementById('orderForm');
const orderMessage = document.getElementById('orderMessage');
const portfolioValue = document.getElementById('portfolioValue');
const portfolioSummary = document.getElementById('portfolioSummary');
const trendStatus = document.getElementById('trendStatus');

const defaultData = {
    market: [
        { symbol: 'BTCUSDT', price: 68342.15, changePct: 2.35, volume: '1.24M' },
        { symbol: 'ETHUSDT', price: 3642.10, changePct: 1.82, volume: '2.31M' },
        { symbol: 'SOLUSDT', price: 156.84, changePct: 4.10, volume: '5.45M' }
    ],
    strategy: [
        { name: 'Market Making', status: 'Running' },
        { name: 'Momentum', status: 'Running' },
        { name: 'AI Enhanced', status: 'Running' }
    ],
    metrics: { sharpe: 1.82, winRate: 63.4, exposure: 12.5, status: 'Healthy' },
    status: 'Live'
};

const chartValues = [
    68050, 68190, 68340, 68220, 68410, 68530, 68490, 68680, 68720, 68810,
    68730, 68920, 69000, 68910, 69150, 69220, 69180, 69420, 69360, 69610
];

function drawChart(values) {
    const ctx = chartCtx;
    const width = priceChart.width;
    const height = priceChart.height;
    ctx.clearRect(0, 0, width, height);

    const padding = 20;
    const min = Math.min(...values) * 0.995;
    const max = Math.max(...values) * 1.005;
    const range = max - min || 1;

    ctx.beginPath();
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#34d399';

    values.forEach((value, index) => {
        const x = padding + (index * (width - (padding * 2))) / (values.length - 1);
        const y = height - padding - ((value - min) / range) * (height - (padding * 2));
        if (index === 0) {
            ctx.moveTo(x, y);
        } else {
            ctx.lineTo(x, y);
        }
    });
    ctx.stroke();

    ctx.fillStyle = '#7dd3fc';
    for (let i = 0; i < values.length; i++) {
        const x = padding + (i * (width - (padding * 2))) / (values.length - 1);
        const y = height - padding - ((values[i] - min) / range) * (height - (padding * 2));
        if (i % 5 === 0) {
            ctx.fillRect(x - 1, y - 1, 2, 2);
        }
    }
}

function renderMarket(data) {
    const market = data.market || defaultData.market;
    marketTableBody.innerHTML = '';
    market.forEach(item => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${item.symbol}</td>
            <td>$${Number(item.price).toLocaleString(undefined, {maximumFractionDigits: 2})}</td>
            <td class="positive">${item.changePct > 0 ? '+' : ''}${item.changePct}%</td>
            <td>${item.volume}</td>
        `;
        marketTableBody.appendChild(row);
    });

    const first = market[0];
    if (first) {
        portfolioValue.textContent = `$${Number(first.price).toLocaleString(undefined, { maximumFractionDigits: 2 })}`;
        portfolioSummary.textContent = `PnL: $6,400.75 | Daily: ${first.changePct > 0 ? '+' : ''}${first.changePct}%`;
        trendStatus.textContent = first.changePct >= 0 ? 'Bullish' : 'Bearish';
    }
}

function renderStrategy(data) {
    const items = data.strategy || defaultData.strategy;
    strategyList.innerHTML = items.map(item => `
        <li>
            <span class="strategy-name">${item.name}</span>
            <span class="status-pill small ${item.status === 'Running' ? 'success' : 'warn'}">${item.status}</span>
        </li>
    `).join('');

    const metrics = data.metrics || defaultData.metrics;
    strategyMetrics.innerHTML = `
        <div><span>Sharpe</span><strong>${metrics.sharpe}</strong></div>
        <div><span>Win Rate</span><strong>${metrics.winRate}%</strong></div>
        <div><span>Exposure</span><strong>${metrics.exposure}%</strong></div>
        <div><span>Status</span><strong>${metrics.status}</strong></div>
    `;
}

function renderActivity(items) {
    const feed = items || [
        'Order: BUY 0.25 BTC @ 68,342.15',
        'Risk: Position within limits',
        'Strategy: Market Making rebalanced'
    ];
    activityFeed.innerHTML = feed.map(entry => `<li>${entry}</li>`).join('');
}

async function loadDashboard() {
    try {
        const response = await fetch('/api/market');
        const payload = await response.json();
        renderMarket(payload);
        renderStrategy(payload);
        drawChart(payload.history || payload.chart || chartValues);
        renderActivity([
            `Feed: ${payload.source || 'live market feed'}`,
            `Order: BUY 0.25 BTC @ ${payload.market[0]?.price ?? 68342.15}`,
            'Risk: Position within limits',
            'Strategy: Market Making rebalanced'
        ]);
    } catch (error) {
        renderMarket(defaultData);
        renderStrategy(defaultData);
        renderActivity();
        drawChart(chartValues);
    }
}

orderForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
        symbol: document.getElementById('symbolInput').value,
        side: document.getElementById('sideInput').value,
        quantity: document.getElementById('quantityInput').value
    };

    try {
        const response = await fetch('/api/order', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const result = await response.json();
        orderMessage.className = result.ok ? 'message success' : 'message error';
        orderMessage.textContent = result.message || 'Order placed';
        if (result.ok) {
            renderActivity([result.message, 'Risk: Position within limits', 'Strategy: Market Making rebalanced']);
            orderForm.reset();
            document.getElementById('symbolInput').value = 'BTCUSDT';
            document.getElementById('sideInput').value = 'BUY';
            document.getElementById('quantityInput').value = '1';
        }
    } catch (error) {
        orderMessage.className = 'message error';
        orderMessage.textContent = 'Unable to place order right now.';
    }
});

loadDashboard();
setInterval(loadDashboard, 2500);
