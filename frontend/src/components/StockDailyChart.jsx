import {
    CartesianGrid,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis
} from 'recharts';

import {
    formatDate,
    formatShortDate
} from '../utils/stockFormat';

function StockDailyChart({
                             stockCode,
                             dailyPrices
                         }) {
    if (dailyPrices.length === 0) {
        return null;
    }

    const chartData = dailyPrices;
    const latest = chartData[chartData.length - 1];

    return (
        <section className="chart-section" aria-labelledby="daily-chart-title">
            <h2 id="daily-chart-title" className="chart-title">
                {stockCode} 일봉 차트
            </h2>

            <p className="chart-indicators">
                RSI(14): {latest.rsi == null ? '-' : latest.rsi.toFixed(2)} · MACD: {latest.macd == null ? '-' : latest.macd.toFixed(2)}
            </p>

            <div className="chart-wrapper">
                <ResponsiveContainer
                    width="100%"
                    height="100%"
                >
                    <LineChart data={chartData}>
                        <CartesianGrid
                            strokeDasharray="3 3"
                        />

                        <XAxis
                            dataKey="date"
                            minTickGap={25}
                            tickFormatter={formatShortDate}
                        />

                        <YAxis
                            width={85}
                            domain={['auto', 'auto']}
                            tickFormatter={(value) =>
                                Number(value).toLocaleString()
                            }
                        />

                        <Tooltip
                            labelFormatter={formatDate}
                            formatter={(value) => [
                                `${Number(value).toLocaleString()}원`,
                                '종가'
                            ]}
                        />

                        <Line
                            type="monotone"
                            dataKey="closePrice"
                            name="종가"
                            stroke="#667eea"
                            strokeWidth={2}
                            dot={false}
                            activeDot={{ r: 5 }}
                        />
                        <Line type="monotone" dataKey="macd" name="MACD" stroke="#f59e0b" dot={false} />
                        <Line type="monotone" dataKey="signal" name="Signal" stroke="#10b981" dot={false} />
                    </LineChart>
                </ResponsiveContainer>
            </div>
            <p className="sr-only">최근 종가 {Number(latest.closePrice).toLocaleString()}원.</p>
        </section>
    );
}

export default StockDailyChart;
