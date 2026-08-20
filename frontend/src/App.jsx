import { lazy, Suspense, useState } from 'react';
import { Navigate, NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  getCurrentPrice,
  getMultiplePrices,
  getWatchlist,
  addToWatchlist,
  removeFromWatchlist,
  updateWatchlistItem
} from './api/kiwoomApi';

import StockSearchForm from './components/StockSearchForm';

import StockResultList from './components/StockResultList';
import Watchlist from './components/Watchlist';
import AccountPortfolio from './components/AccountPortfolio';
import AlertCenter from './components/AlertCenter';
import MarketDiscovery from './components/MarketDiscovery';

const StockDailyChart = lazy(() => import('./components/StockDailyChart'));

import './App.css';

function App({ currentUser, onLogout }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [stocks, setStocks] = useState([]);

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState('');
  const [searched, setSearched] = useState(false);

  const watchlistQuery = useQuery({ queryKey: ['watchlist'], queryFn: getWatchlist });
  const watchlist = watchlistQuery.data ?? [];
  const addWatchlistMutation = useMutation({
    mutationFn: (code) => addToWatchlist(code),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['watchlist'] })
  });
  const removeWatchlistMutation = useMutation({
    mutationFn: removeFromWatchlist,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['watchlist'] })
  });
  const updateWatchlistMutation = useMutation({
    mutationFn: ({ code, groupName, note }) => updateWatchlistItem(code, groupName, note),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['watchlist'] })
  });

  const initializeSearch = () => {
    setLoading(true);
    setError('');
    setStocks([]);
    setSearched(true);
  };

  const handleError = (err) => {
    setError('요청 처리 중 오류가 발생했습니다: ' + err.message);
  };

  const addCurrentToWatchlist = async () => {
    if (!stocks[0]) return;
    try {
      await addWatchlistMutation.mutateAsync(stocks[0].code);
    } catch (err) {
      handleError(err);
    }
  };

  const deleteWatchlist = async (code) => {
    try {
      await removeWatchlistMutation.mutateAsync(code);
    } catch (err) {
      handleError(err);
    }
  };

  const updateWatchlist = async (code, groupName, note) => {
    try {
      await updateWatchlistMutation.mutateAsync({ code, groupName, note });
    } catch (err) {
      handleError(err);
    }
  };

  const searchFromWatchlist = (code) => {
    handleSingleSearch(code);
  };

  const handleSingleSearch = async (code) => {
    initializeSearch();
    navigate('/chart');

    try {
      const stock = await getCurrentPrice(code);

      setStocks([stock]);
    } catch (err) {
      handleError(err);
    } finally {
      setLoading(false);
    }
  };

  const handleMultipleSearch = async (codes) => {
    initializeSearch();
    navigate('/chart');

    try {
      const data = await getMultiplePrices(codes);

      setStocks(data);
    } catch (err) {
      handleError(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container">
      <header className="app-header">
        <h1>📈 Kiwoom 주가 조회</h1>
        {currentUser && (
          <div className="session-controls">
            <span>{currentUser.username}</span>
            <button type="button" onClick={onLogout}>
              로그아웃
            </button>
          </div>
        )}
      </header>

      <p className="subtitle">실시간 주식 종목 정보를 조회하세요</p>

      <nav className="main-nav" aria-label="주요 화면">
        <NavLink to="/" end>
          종목 검색
        </NavLink>
        <NavLink to="/chart">차트</NavLink>
        <NavLink to="/discover">종목 발견</NavLink>
        <NavLink to="/watchlist">관심 종목</NavLink>
        <NavLink to="/portfolio">포트폴리오</NavLink>
        <NavLink to="/alerts">알림</NavLink>
      </nav>

      <Routes>
        <Route
          path="/"
          element={
            <StockSearchForm
              loading={loading}
              onSingleSearch={handleSingleSearch}
              onMultipleSearch={handleMultipleSearch}
            />
          }
        />

        <Route
          path="/chart"
          element={
            <>
              {!searched && !loading && (
                <p className="empty-state">종목 검색 또는 종목 발견 탭에서 종목을 선택하세요.</p>
              )}
              {loading && (
                <div className="loading" role="status" aria-live="polite">
                  <div className="spinner" aria-hidden="true" />
                  <div className="loading-text">조회 중입니다...</div>
                </div>
              )}
              <StockResultList stocks={stocks} searched={searched} loading={loading} />

              {stocks.length === 1 && (
                <button type="button" onClick={addCurrentToWatchlist}>
                  관심종목 추가
                </button>
              )}

              <Suspense fallback={<div className="loading-text">차트를 불러오는 중...</div>}>
                <StockDailyChart stockCode={stocks[0]?.code} />
              </Suspense>
            </>
          }
        />

        <Route
          path="/discover"
          element={
            <MarketDiscovery
              onSelectStock={(code) => {
                handleSingleSearch(code);
              }}
            />
          }
        />

        <Route
          path="/watchlist"
          element={
            <Watchlist
              codes={watchlist}
              onSearch={searchFromWatchlist}
              onRemove={deleteWatchlist}
              onUpdate={updateWatchlist}
            />
          }
        />

        <Route
          path="/portfolio"
          element={
            <AccountPortfolio
              onSelectStock={(code) => {
                handleSingleSearch(code);
              }}
            />
          }
        />

        <Route path="/alerts" element={<AlertCenter onError={handleError} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      {(error || watchlistQuery.error) && (
        <div className="error" role="alert">
          {error || `데이터를 불러오지 못했습니다: ${watchlistQuery.error.message}`}
        </div>
      )}
    </div>
  );
}

export default App;
