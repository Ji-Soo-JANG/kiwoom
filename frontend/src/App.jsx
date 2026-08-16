import { lazy, Suspense, useEffect, useState } from 'react';
import {Navigate, NavLink, Route, Routes, useNavigate} from 'react-router-dom';

import {
  getCurrentPrice,
  getDailyPrices,
  getMultiplePrices,
  getWatchlist,
  addToWatchlist,
  removeFromWatchlist,
  getPortfolio,
  savePortfolioPosition,
  removePortfolioPosition,
  getPortfolioValuation
} from './api/kiwoomApi';

import StockSearchForm
  from './components/StockSearchForm';

import StockResultList
  from './components/StockResultList';
import Watchlist from './components/Watchlist';
import Portfolio from './components/Portfolio';
import AlertCenter from './components/AlertCenter';

const StockDailyChart = lazy(() =>
    import('./components/StockDailyChart')
);

import './App.css';

function App() {
  const navigate = useNavigate();
  const [stocks, setStocks] = useState([]);
  const [dailyPrices, setDailyPrices] =
      useState([]);

  const [loading, setLoading] =
      useState(false);

  const [error, setError] =
      useState('');
  const [watchlist, setWatchlist] = useState([]);
  const [portfolio, setPortfolio] = useState([]);
  const [valuations, setValuations] = useState([]);
  const [portfolioLoading, setPortfolioLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  useEffect(() => {
    getWatchlist().then(setWatchlist)
        .catch(() => setError('관심종목을 불러오지 못했습니다.'));
    getPortfolio().then(setPortfolio)
        .catch(() => setError('포트폴리오를 불러오지 못했습니다.'));
  }, []);

  const savePosition = async (code, quantity, averagePrice) => {
    setPortfolioLoading(true);
    setError('');
    try {
      await savePortfolioPosition(code, quantity, averagePrice);
      setPortfolio(await getPortfolio());
      setValuations([]);
    } catch (err) { handleError(err); throw err; }
    finally { setPortfolioLoading(false); }
  };

  const deletePosition = async (code) => {
    setError('');
    try {
      await removePortfolioPosition(code);
      setPortfolio((items) => items.filter((item) => item.code !== code));
      setValuations((items) => items.filter((item) => item.code !== code));
    } catch (err) { handleError(err); }
  };

  const valuatePortfolio = async () => {
    setPortfolioLoading(true);
    setError('');
    try { setValuations(await getPortfolioValuation()); }
    catch (err) { handleError(err); }
    finally { setPortfolioLoading(false); }
  };

  const initializeSearch = () => {
    setLoading(true);
    setError('');
    setStocks([]);
    setDailyPrices([]);
    setSearched(true);
  };

  const handleError = (err) => {
    setError(
        '요청 처리 중 오류가 발생했습니다: '
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

  const searchFromWatchlist = (code) => {
    navigate('/');
    handleSingleSearch(code);
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

        <nav className="main-nav" aria-label="주요 화면">
          <NavLink to="/" end>종목 검색</NavLink>
          <NavLink to="/watchlist">관심 종목</NavLink>
          <NavLink to="/portfolio">포트폴리오</NavLink>
          <NavLink to="/alerts">알림</NavLink>
        </nav>

        <Routes>
          <Route path="/" element={<>
            <StockSearchForm
                loading={loading}
                onSingleSearch={handleSingleSearch}
                onMultipleSearch={handleMultipleSearch}
            />

            {loading && (
                <div className="loading" role="status" aria-live="polite">
                  <div className="spinner" aria-hidden="true" />

                  <div className="loading-text">조회 중입니다...</div>
                </div>
            )}

            <StockResultList stocks={stocks} searched={searched} loading={loading} />

            {stocks.length === 1 && (
                <button type="button" onClick={addCurrentToWatchlist}>관심종목 추가</button>
            )}

            <Suspense fallback={<div className="loading-text">차트를 불러오는 중...</div>}>
              <StockDailyChart stockCode={stocks[0]?.code} dailyPrices={dailyPrices}/>
            </Suspense>
          </>}/>

          <Route path="/watchlist" element={
            <Watchlist codes={watchlist} onSearch={searchFromWatchlist} onRemove={deleteWatchlist}/>
          }/>

          <Route path="/portfolio" element={
            <Portfolio positions={portfolio} valuations={valuations} loading={portfolioLoading}
                       onSave={savePosition} onRemove={deletePosition} onValuate={valuatePortfolio}/>
          }/>

          <Route path="/alerts" element={<AlertCenter onError={handleError}/>}/>
          <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>

        {error && (
            <div className="error" role="alert">
              {error}
            </div>
        )}
      </div>
  );
}

export default App;
