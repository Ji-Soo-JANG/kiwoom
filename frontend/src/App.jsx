import { lazy, Suspense, useEffect, useState } from 'react';

import {
  getCurrentPrice,
  getDailyPrices,
  getMultiplePrices,
  getWatchlist,
  addToWatchlist,
  removeFromWatchlist
} from './api/kiwoomApi';

import StockSearchForm
  from './components/StockSearchForm';

import StockResultList
  from './components/StockResultList';
import Watchlist from './components/Watchlist';

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
  const [watchlist, setWatchlist] = useState([]);

  useEffect(() => {
    getWatchlist().then(setWatchlist)
        .catch(() => setError('관심종목을 불러오지 못했습니다.'));
  }, []);

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

  const addCurrentToWatchlist = async () => {
    if (!stocks[0]) return;
    try {
      await addToWatchlist(stocks[0].code);
      setWatchlist(await getWatchlist());
    } catch (err) { handleError(err); }
  };

  const deleteWatchlist = async (code) => {
    try {
      await removeFromWatchlist(code);
      setWatchlist((items) => items.filter((item) => item !== code));
    } catch (err) { handleError(err); }
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

        {stocks.length === 1 && (
            <button type="button" onClick={addCurrentToWatchlist}>관심종목 추가</button>
        )}

        <Watchlist codes={watchlist} onSearch={handleSingleSearch} onRemove={deleteWatchlist} />

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
