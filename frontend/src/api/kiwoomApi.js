const errorMessages = {
  KIWOOM_AUTHENTICATION_FAILED: '키움 인증을 갱신하지 못했습니다. API 키 설정을 확인해 주세요.',
  KIWOOM_RATE_LIMITED: '키움 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.',
  KIWOOM_STOCK_NOT_FOUND: '키움에서 해당 종목을 찾지 못했습니다. 종목 코드를 확인해 주세요.',
  KIWOOM_MARKET_CLOSED: '현재 장 운영시간이 아닙니다. 장 시작 후 다시 시도해 주세요.',
  KIWOOM_UPSTREAM_UNAVAILABLE:
    '키움 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.',
  KIWOOM_INVALID_RESPONSE: '키움 응답을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
};

/** @typedef {import('./generated/openapi').components['schemas']['AuthUser']} AuthUser */
/** @typedef {import('./generated/openapi').components['schemas']['StockPriceResponse']} StockPriceResponse */
/** @typedef {import('./generated/openapi').components['schemas']['DailyPriceResponse']} DailyPriceResponse */

export class ApiError extends Error {
  constructor(status, code, message) {
    super(errorMessages[code] || message || `API 요청 실패 (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

const requestJson = async (url, options) => {
  const response = await fetch(url, { credentials: 'include', ...options });

  if (!response.ok) {
    const body = await response.text();
    let errorBody;
    try {
      errorBody = JSON.parse(body);
    } catch {
      errorBody = {};
    }
    throw new ApiError(response.status, errorBody.code, errorBody.message || body);
  }

  if (response.status === 204) return null;
  return response.json();
};

/** @returns {Promise<AuthUser>} */
export const getCurrentUser = () => requestJson('/api/auth/me');
export const logout = () => requestJson('/api/auth/logout', { method: 'POST' });

/** @param {string} code @returns {Promise<StockPriceResponse>} */
export const getCurrentPrice = (code) => {
  const encodedCode = encodeURIComponent(code);

  return requestJson(`/api/kiwoom/stock-price/${encodedCode}`);
};

/**
 * @param {string} code
 * @param {string} [period] 차트 주기: day(일봉), week(주봉), month(월봉), year(년봉), 기본 day
 * @param {number} [limit] 조회 건수, 기본 500
 * @returns {Promise<DailyPriceResponse[]>}
 */
export const getDailyPrices = (code, period = 'day', limit = 500) => {
  const encodedCode = encodeURIComponent(code);

  return requestJson(
    `/api/kiwoom/stock-price/${encodedCode}/daily?period=${encodeURIComponent(period)}&limit=${limit}`
  );
};

export const getMultiplePrices = (codes) => {
  const parameter = encodeURIComponent(codes.join(','));

  return requestJson(`/api/kiwoom/stock-prices?codes=${parameter}`);
};

export const getWatchlist = () => requestJson('/api/watchlist');
export const addToWatchlist = (code, groupName = '기본', note = '') =>
  requestJson('/api/watchlist', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, groupName, note })
  });
export const updateWatchlistItem = (code, groupName, note) =>
  requestJson(`/api/watchlist/${encodeURIComponent(code)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, groupName, note })
  });
export const removeFromWatchlist = (code) =>
  requestJson(`/api/watchlist/${encodeURIComponent(code)}`, { method: 'DELETE' });

export const getPortfolio = () => requestJson('/api/portfolio');

export const savePortfolioPosition = (code, quantity, averagePrice) =>
  requestJson(`/api/portfolio/${encodeURIComponent(code)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ quantity, averagePrice })
  });

export const removePortfolioPosition = (code) =>
  requestJson(`/api/portfolio/${encodeURIComponent(code)}`, { method: 'DELETE' });

export const getPortfolioValuation = () => requestJson('/api/portfolio/valuation');
export const searchStocks = (query, market = 'ALL', productType = 'ALL') =>
  requestJson(
    `/api/kiwoom/stocks/search?q=${encodeURIComponent(query)}&market=${encodeURIComponent(market)}&productType=${encodeURIComponent(productType)}`
  );
export const getMarketRankings = (market = 'ALL') =>
  requestJson(`/api/kiwoom/market-rankings?market=${encodeURIComponent(market)}`);
/** @param {number} [boxRangeDays] 박스권 횡보 기준 기간(거래일), 기본 60일 */
export const getStrategyCandidates = (boxRangeDays = 60) =>
  requestJson(`/api/kiwoom/strategy-candidates?boxRangeDays=${encodeURIComponent(boxRangeDays)}`);
export const getLatestStrategySnapshot = () => requestJson('/api/kiwoom/strategy-scans/latest');
export const getMarketDataStatus = () => requestJson('/api/kiwoom/admin/market-data');
export const synchronizeMarketData = (limit = 20) =>
  requestJson(`/api/kiwoom/admin/market-data/sync?limit=${encodeURIComponent(limit)}`, {
    method: 'POST'
  });
export const getFullMarketDataStatus = () => requestJson('/api/kiwoom/admin/full-market-data');
export const synchronizeFullMarketData = () =>
  requestJson('/api/kiwoom/admin/full-market-data/sync', { method: 'POST' });
export const getAccountPortfolio = () => requestJson('/api/kiwoom/account/portfolio');

export const getAlertRules = () => requestJson('/api/alerts/rules');
export const createAlertRule = (code, conditionType, threshold) =>
  requestJson('/api/alerts/rules', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, conditionType, threshold })
  });
export const updateAlertRule = (id, threshold, enabled) =>
  requestJson(`/api/alerts/rules/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ threshold, enabled })
  });
export const deleteAlertRule = (id) => requestJson(`/api/alerts/rules/${id}`, { method: 'DELETE' });
export const evaluateAlerts = () => requestJson('/api/alerts/evaluate', { method: 'POST' });
export const getAlertEvents = (unreadOnly = false, page = 0, size = 20) =>
  requestJson(`/api/alerts/events?unreadOnly=${unreadOnly}&page=${page}&size=${size}`);
export const markAlertRead = (id) =>
  requestJson(`/api/alerts/events/${id}/read`, { method: 'POST' });

export const getPortfolioProfitTrend = () =>
  requestJson('/api/portfolio/transactions/profit-trend');

export const importPortfolioTrades = (csv) =>
  requestJson('/api/portfolio/transactions/import', {
    method: 'POST',
    headers: { 'Content-Type': 'text/csv' },
    body: csv
  });
