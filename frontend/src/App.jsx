import { lazy, Suspense, useState } from 'react';

import {
  getCurrentPrice,
  getDailyPrices,
  getMultiplePrices
} from './api/kiwoomApi';

import StockSearchForm
  from './components/StockSearchForm';

import StockResultList
  from './components/StockResultList';

const StockDailyChart = lazy(() =>
    import('./components/StockDailyChart')
);

import './App.css';

function App() {
  const [stocks, setStocks] = useState([]);
  const [dailyPrices, setDailyPrices] =
      useState([]);

  const [loading, setLoading] =
      useState(false);

  const [error, setError] =
      useState('');

  const initializeSearch = () => {
    setLoading(true);
    setError('');
    setStocks([]);
    setDailyPrices([]);
  };

  const handleError = (err) => {
    setError(
        '주가 조회 중 오류가 발생했습니다: '
        + err.message
    );
  };

  const handleSingleSearch = async (code) => {
    initializeSearch();

    try {
      const [stock, daily] =
          await Promise.all([
            getCurrentPrice(code),
            getDailyPrices(code)
          ]);

      setStocks([stock]);
      setDailyPrices(daily);
    } catch (err) {
      handleError(err);
    } finally {
      setLoading(false);
    }
  };

  const handleMultipleSearch =
      async (codes) => {
        initializeSearch();

        try {
          const data =
              await getMultiplePrices(codes);

          setStocks(data);
        } catch (err) {
          handleError(err);
        } finally {
          setLoading(false);
        }
      };

  return (
      <div className="container">
        <h1>📈 Kiwoom 주가 조회</h1>

        <p className="subtitle">
          실시간 주식 종목 정보를 조회하세요
        </p>

        <StockSearchForm
            loading={loading}
            onSingleSearch={handleSingleSearch}
            onMultipleSearch={handleMultipleSearch}
        />

        {loading && (
            <div className="loading">
              <div className="spinner" />

              <div className="loading-text">
                조회 중입니다...
              </div>
            </div>
        )}

        <StockResultList stocks={stocks} />

        <Suspense fallback={<div className="loading-text">차트를 불러오는 중...</div>}>
          <StockDailyChart
              stockCode={stocks[0]?.code}
              dailyPrices={dailyPrices}
          />
        </Suspense>

        {error && (
            <div className="error">
              {error}
            </div>
        )}
      </div>
  );
}

export default App;
