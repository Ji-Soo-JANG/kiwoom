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

    return (
        <div className="chart-section">
            <h2 className="chart-title">
                {stockCode} 일봉 차트
            </h2>

            <div className="chart-wrapper">
                <ResponsiveContainer
                    width="100%"
                    height="100%"
                >
                    <LineChart data={dailyPrices}>
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
                    </LineChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

export default StockDailyChart;