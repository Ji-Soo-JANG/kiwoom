import { useDeferredValue, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { searchStocks } from '../api/kiwoomApi';

function StockSearchForm({ loading, onSingleSearch, onMultipleSearch }) {
  const [validationError, setValidationError] = useState('');
  const [singleCode, setSingleCode] = useState('');
  const [market, setMarket] = useState('ALL');
  const [recent, setRecent] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('kiwoom-recent-stocks') || '[]');
    } catch {
      return [];
    }
  });
  const deferredSearch = useDeferredValue(singleCode.trim());
  const suggestions = useQuery({
    queryKey: ['stock-search', deferredSearch, market],
    queryFn: () => searchStocks(deferredSearch, market),
    enabled: deferredSearch.length > 0 && !/^\d{6}$/.test(deferredSearch),
    staleTime: 12 * 60 * 60 * 1000
  });

  const [multipleCodes, setMultipleCodes] = useState('');

  const submitSingle = (event) => {
    event?.preventDefault();
    const keyword = singleCode.trim();

    if (/^\d{6}$/.test(keyword)) {
      setValidationError('');
      onSingleSearch(keyword);
      return;
    }
    const stock = suggestions.data?.[0];
    if (!stock) {
      setValidationError(
        suggestions.isFetching
          ? '종목을 검색하고 있습니다. 잠시 후 다시 시도하세요.'
          : '일치하는 종목을 찾지 못했습니다.'
      );
      return;
    }
    selectStock(stock);
    setValidationError('');
    onSingleSearch(stock.code);
  };

  const submitMultiple = (event) => {
    event?.preventDefault();
    const codes = multipleCodes
      .split(',')
      .map((code) => code.trim())
      .filter(Boolean);

    if (codes.length === 0 || codes.length > 20 || codes.some((code) => !/^\d{6}$/.test(code))) {
      setValidationError('6자리 숫자 종목 코드를 최대 20개까지 입력하세요.');
      return;
    }
    setValidationError('');
    onMultipleSearch(codes);
  };

  const selectStock = (stock) => {
    setSingleCode(stock.code);
    const next = [stock, ...recent.filter((item) => item.code !== stock.code)].slice(0, 5);
    setRecent(next);
    localStorage.setItem('kiwoom-recent-stocks', JSON.stringify(next));
  };

  return (
    <>
      {validationError && (
        <p id="search-validation-error" className="error" role="alert">
          {validationError}
        </p>
      )}
      <form onSubmit={submitSingle} noValidate>
        <div className="input-group">
          <label htmlFor="singleCode">종목 검색</label>

          <input
            id="singleCode"
            type="text"
            maxLength={40}
            aria-describedby={`stock-code-help${validationError ? ' search-validation-error' : ''}`}
            aria-invalid={Boolean(validationError)}
            value={singleCode}
            disabled={loading}
            placeholder="예: 삼성전자, 삼성 전자, ㅅㅅㅈㅈ, 005930"
            onChange={(event) => setSingleCode(event.target.value)}
          />
          <small id="stock-code-help">종목명·초성·6자리 코드로 검색하고 Enter를 누르세요.</small>
          <label htmlFor="stock-market" className="stock-market-label">
            시장
          </label>
          <select
            id="stock-market"
            value={market}
            onChange={(event) => setMarket(event.target.value)}
          >
            <option value="ALL">전체</option>
            <option value="KOSPI">코스피</option>
            <option value="KOSDAQ">코스닥</option>
          </select>
          {suggestions.data?.length > 0 && (
            <ul className="stock-suggestions" aria-label="종목 검색 결과">
              {suggestions.data.map((stock) => (
                <li key={`${stock.market}-${stock.code}`}>
                  <button type="button" onClick={() => selectStock(stock)}>
                    <strong>{stock.name}</strong>{' '}
                    <span>
                      {stock.code} · {stock.market}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {suggestions.isFetching && <small role="status">종목을 찾는 중...</small>}
          {recent.length > 0 && (
            <div className="recent-stocks" aria-label="최근 검색 종목">
              <span>최근 검색</span>
              {recent.map((stock) => (
                <button type="button" key={stock.code} onClick={() => selectStock(stock)}>
                  {stock.name}
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="button-group">
          <button type="submit" className="btn-single" disabled={loading} onClick={submitSingle}>
            단일 조회
          </button>
        </div>
      </form>

      <form onSubmit={submitMultiple} noValidate>
        <div className="input-group">
          <label htmlFor="multipleCodes">종목 코드 (다중 조회)</label>

          <input
            id="multipleCodes"
            type="text"
            value={multipleCodes}
            disabled={loading}
            placeholder="예: 005930,000660,035420"
            aria-describedby={`multiple-code-help${validationError ? ' search-validation-error' : ''}`}
            aria-invalid={Boolean(validationError)}
            onChange={(event) => setMultipleCodes(event.target.value)}
          />
          <small id="multiple-code-help">쉼표로 구분해 최대 20개까지 입력하세요.</small>
        </div>

        <div className="button-group">
          <button
            type="submit"
            className="btn-multiple"
            disabled={loading}
            onClick={submitMultiple}
          >
            다중 조회
          </button>
        </div>
      </form>
    </>
  );
}

export default StockSearchForm;
