import { useMemo, useState } from 'react';
import { useQueries } from '@tanstack/react-query';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import { getDailyPrices } from '../api/kiwoomApi';
import { addTechnicalIndicators } from '../utils/technicalIndicators';
import { formatShortDate } from '../utils/stockFormat';

const COLORS = ['#4f46e5', '#ef4444', '#0ea5e9', '#10b981', '#f59e0b'];

function mergeSeries(items, period) {
  const rows = new Map();
  items.forEach(({ code, prices }) => {
    const source = period === 0 ? prices : prices.slice(-period);
    const first = Number(source[0]?.closePrice);
    addTechnicalIndicators(source).forEach((price) => {
      const row = rows.get(price.date) ?? { date: price.date };
      row[`${code}:return`] = first ? (Number(price.closePrice) / first - 1) * 100 : 0;
      row[`${code}:volume`] = Number(price.volume);
      row[`${code}:ma20`] = price.ma20 == null || !first ? null : (price.ma20 / first - 1) * 100;
      rows.set(price.date, row);
    });
  });
  return [...rows.values()].sort((a, b) => a.date.localeCompare(b.date));
}

function StockComparison({ items, onRemove, onClear }) {
  const [period, setPeriod] = useState(60);
  const queries = useQueries({
    queries: items.map((item) => ({
      queryKey: ['stock-comparison', item.code],
      queryFn: () => getDailyPrices(item.code),
      staleTime: 5 * 60 * 1000,
      retry: false
    }))
  });
  const series = useMemo(
    () =>
      mergeSeries(
        items.map((item, index) => ({ code: item.code, prices: queries[index]?.data ?? [] })),
        period
      ),
    [items, queries, period]
  );
  const pending = queries.some((query) => query.isPending);
  const error = queries.find((query) => query.error)?.error;

  return (
    <section className="comparison" aria-labelledby="comparison-title">
      <div className="market-discovery-heading">
        <div>
          <h2 id="comparison-title">종목 비교</h2>
          <p>검색 결과나 관심종목에서 최대 5개를 담아 첫날을 0%로 맞춰 비교합니다.</p>
        </div>
        {items.length > 0 && (
          <button type="button" onClick={onClear}>
            전체 비우기
          </button>
        )}
      </div>
      {items.length === 0 ? (
        <p className="empty-state">종목 검색 결과 또는 관심종목에서 ‘비교에 추가’를 누르세요.</p>
      ) : (
        <>
          <div className="comparison-toolbar">
            <div className="comparison-chips">
              {items.map((item) => (
                <button key={item.code} type="button" onClick={() => onRemove(item.code)}>
                  {item.name || item.code} ({item.code}) ×
                </button>
              ))}
            </div>
            <label>
              기간
              <select value={period} onChange={(event) => setPeriod(Number(event.target.value))}>
                <option value={30}>1개월</option>
                <option value={60}>3개월</option>
                <option value={120}>6개월</option>
                <option value={250}>1년</option>
                <option value={0}>전체</option>
              </select>
            </label>
          </div>
          {pending && (
            <p className="loading-text" role="status">
              비교 데이터를 불러오는 중...
            </p>
          )}
          {error && (
            <div className="error" role="alert">
              비교 데이터를 불러오지 못했습니다: {error.message}
            </div>
          )}
          {!pending && !error && series.length > 0 && (
            <>
              <h3 className="chart-panel-title">정규화 수익률 · 20일 이동평균</h3>
              <div className="comparison-chart">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={series}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" tickFormatter={formatShortDate} minTickGap={25} />
                    <YAxis unit="%" />
                    <Tooltip formatter={(value) => `${Number(value).toFixed(2)}%`} />
                    <Legend />
                    {items.flatMap((item, index) => [
                      <Line
                        key={`${item.code}-r`}
                        dataKey={`${item.code}:return`}
                        name={`${item.name || item.code} 수익률`}
                        stroke={COLORS[index]}
                        dot={false}
                        strokeWidth={2}
                      />,
                      <Line
                        key={`${item.code}-m`}
                        dataKey={`${item.code}:ma20`}
                        name={`${item.name || item.code} MA20`}
                        stroke={COLORS[index]}
                        dot={false}
                        strokeDasharray="5 4"
                      />
                    ])}
                  </LineChart>
                </ResponsiveContainer>
              </div>
              <h3 className="chart-panel-title">거래량</h3>
              <div className="comparison-chart comparison-volume-chart">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={series}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" tickFormatter={formatShortDate} minTickGap={25} />
                    <YAxis tickFormatter={(value) => Number(value).toLocaleString('ko-KR')} />
                    <Tooltip />
                    <Legend />
                    {items.map((item, index) => (
                      <Line
                        key={item.code}
                        dataKey={`${item.code}:volume`}
                        name={`${item.name || item.code} 거래량`}
                        stroke={COLORS[index]}
                        dot={false}
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}

export default StockComparison;
