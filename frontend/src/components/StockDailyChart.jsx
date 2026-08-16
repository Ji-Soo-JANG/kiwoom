import {useMemo, useState} from 'react';
import {
    Bar, Brush, CartesianGrid, Cell, ComposedChart, Line, LineChart,
    ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis
} from 'recharts';

import {formatDate, formatShortDate} from '../utils/stockFormat';
import {addTechnicalIndicators} from '../utils/technicalIndicators';

const periods = [
    {value: 30, label: '1개월'}, {value: 60, label: '3개월'},
    {value: 120, label: '6개월'}, {value: 0, label: '전체'}
];
const number = (value) => Number(value).toLocaleString('ko-KR', {maximumFractionDigits: 2});

function Candle({x, y, width, height, payload}) {
    const open = Number(payload.openPrice);
    const close = Number(payload.closePrice);
    const high = Number(payload.highPrice);
    const low = Number(payload.lowPrice);
    const color = close >= open ? '#d92d20' : '#1570ef';
    const center = x + width / 2;
    const range = high - low;
    const priceY = (price) => range === 0 ? y + height / 2 : y + (high - price) / range * height;
    const bodyTop = priceY(Math.max(open, close));
    const bodyBottom = priceY(Math.min(open, close));
    return <g aria-label={`${payload.date} 시가 ${number(open)} 고가 ${number(high)} 저가 ${number(low)} 종가 ${number(close)}`}>
        <line x1={center} x2={center} y1={y} y2={y + height} stroke={color}/>
        <rect x={x + width * 0.2} y={bodyTop} width={Math.max(width * 0.6, 1)}
              height={Math.max(bodyBottom - bodyTop, 1)} fill={color}/>
    </g>;
}

const PriceTooltip = ({active, payload, label}) => {
    if (!active || !payload?.length) return null;
    const item = payload[0].payload;
    return <div className="chart-tooltip">
        <strong>{formatDate(label)}</strong>
        <span>시 {number(item.openPrice)} · 고 {number(item.highPrice)}</span>
        <span>저 {number(item.lowPrice)} · 종 {number(item.closePrice)}</span>
        <span>거래량 {number(item.volume)}</span>
    </div>;
};

function StockDailyChart({stockCode, dailyPrices}) {
    const [period, setPeriod] = useState(60);
    const enriched = useMemo(() => addTechnicalIndicators(dailyPrices).map((item) => ({
        ...item, priceRange: [Number(item.lowPrice), Number(item.highPrice)]
    })), [dailyPrices]);
    const chartData = period === 0 ? enriched : enriched.slice(-period);

    if (dailyPrices.length === 0) return null;

    const latest = enriched[enriched.length - 1];
    const commonXAxis = {dataKey: 'date', minTickGap: 25, tickFormatter: formatShortDate};
    const lows = chartData.map((item) => Number(item.lowPrice));
    const highs = chartData.map((item) => Number(item.highPrice));
    const pricePadding = Math.max((Math.max(...highs) - Math.min(...lows)) * 0.05, 1);
    const priceDomain = [Math.min(...lows) - pricePadding, Math.max(...highs) + pricePadding];

    return <section className="chart-section" aria-labelledby="daily-chart-title">
        <div className="chart-heading">
            <h2 id="daily-chart-title" className="chart-title">{stockCode} 일봉 차트</h2>
            <label>조회 기간
                <select value={period} onChange={(event) => setPeriod(Number(event.target.value))}>
                    {periods.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                </select>
            </label>
        </div>
        <p className="chart-indicators">
            RSI(14): {latest.rsi == null ? '-' : latest.rsi.toFixed(2)} · MACD: {latest.macd == null ? '-' : latest.macd.toFixed(2)}
        </p>

        <h3 className="chart-panel-title">주가 · 이동평균선</h3>
        <p className="chart-legend"><span className="ma5">MA5</span><span className="ma20">MA20</span><span className="ma60">MA60</span></p>
        <div className="chart-wrapper chart-price-panel">
            <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={chartData} syncId="stock-daily">
                    <CartesianGrid strokeDasharray="3 3"/><XAxis {...commonXAxis}/>
                    <YAxis width={78} domain={priceDomain} tickFormatter={number}/>
                    <Tooltip content={<PriceTooltip/>}/>
                    <Bar dataKey="priceRange" name="캔들" shape={<Candle/>} isAnimationActive={false}/>
                    <Line dataKey="ma5" name="MA5" stroke="#7f56d9" dot={false} strokeWidth={1.5}/>
                    <Line dataKey="ma20" name="MA20" stroke="#f79009" dot={false} strokeWidth={1.5}/>
                    <Line dataKey="ma60" name="MA60" stroke="#12b76a" dot={false} strokeWidth={1.5}/>
                </ComposedChart>
            </ResponsiveContainer>
        </div>

        <h3 className="chart-panel-title">거래량</h3>
        <div className="chart-wrapper chart-volume-panel">
            <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={chartData} syncId="stock-daily">
                    <XAxis {...commonXAxis} hide/><YAxis width={78} tickFormatter={number}/>
                    <Tooltip labelFormatter={formatDate} formatter={(value) => [number(value), '거래량']}/>
                    <Bar dataKey="volume" name="거래량">
                        {chartData.map((item) => <Cell key={item.date}
                            fill={Number(item.closePrice) >= Number(item.openPrice) ? '#f97066' : '#53b1fd'}/>) }
                    </Bar>
                </ComposedChart>
            </ResponsiveContainer>
        </div>

        <h3 className="chart-panel-title">RSI(14)</h3>
        <div className="chart-wrapper chart-indicator-panel">
            <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData} syncId="stock-daily">
                    <XAxis {...commonXAxis} hide/><YAxis width={78} domain={[0, 100]} ticks={[30, 50, 70]}/>
                    <ReferenceLine y={70} stroke="#d92d20" strokeDasharray="3 3"/>
                    <ReferenceLine y={30} stroke="#1570ef" strokeDasharray="3 3"/>
                    <Tooltip labelFormatter={formatDate} formatter={(value) => [number(value), 'RSI']}/>
                    <Line dataKey="rsi" name="RSI" stroke="#7f56d9" dot={false}/>
                </LineChart>
            </ResponsiveContainer>
        </div>

        <h3 className="chart-panel-title">MACD</h3>
        <div className="chart-wrapper chart-indicator-panel">
            <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData} syncId="stock-daily">
                    <XAxis {...commonXAxis}/><YAxis width={78} domain={['auto', 'auto']} tickFormatter={number}/>
                    <ReferenceLine y={0} stroke="#98a2b3"/>
                    <Tooltip labelFormatter={formatDate} formatter={(value, name) => [number(value), name]}/>
                    <Line dataKey="macd" name="MACD" stroke="#f79009" dot={false}/>
                    <Line dataKey="signal" name="Signal" stroke="#12b76a" dot={false}/>
                    <Brush dataKey="date" height={28} tickFormatter={formatShortDate}/>
                </LineChart>
            </ResponsiveContainer>
        </div>
        <p className="chart-zoom-help">아래 범위 선택기를 드래그해 모든 패널을 확대·축소할 수 있습니다.</p>
        <p className="sr-only">최근 종가 {number(latest.closePrice)}원.</p>
    </section>;
}

export default StockDailyChart;
