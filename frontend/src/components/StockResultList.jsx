import { formatPrice, formatSignedNumber, getChangeClass } from '../utils/stockFormat';

function getMarketStatus() {
  const now = new Date();
  const hour = now.getHours();
  const min = now.getMinutes();
  const time = hour * 60 + min;
  const day = now.getDay();
  if (day === 0 || day === 6) return { label: '장마감', color: '#98a2b3' };
  if (time < 540) return { label: '장 시작 전', color: '#98a2b3' };
  if (time < 570) return { label: '장중', color: '#10b981' };
  if (time < 900) return { label: '장중', color: '#10b981' };
  if (time < 930) return { label: '장마감', color: '#98a2b3' };
  return { label: '장마감', color: '#98a2b3' };
}

function formatFetchedAt(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
}

function StockResultList({ stocks, searched = false, loading = false, onCompare }) {
  if (stocks.length === 0) {
    return searched && !loading ? (
      <p className="empty-state" role="status">
        조회된 종목이 없습니다.
      </p>
    ) : null;
  }

  const market = getMarketStatus();

  return (
    <section className="results" aria-label="주가 조회 결과" aria-live="polite">
      {stocks.map((stock, index) => (
        <div key={stock.code || index} className="result-item">
          <div className="stock-code">
            {stock.code}
            <span
              style={{
                marginLeft: 8,
                padding: '2px 8px',
                borderRadius: 10,
                fontSize: 11,
                fontWeight: 600,
                color: '#fff',
                background: market.color
              }}
            >
              {market.label}
            </span>
            {stock.fetchedAt && (
              <span style={{ marginLeft: 6, fontSize: 11, color: '#98a2b3' }}>
                조회: {formatFetchedAt(stock.fetchedAt)}
              </span>
            )}
          </div>

          <div className="stock-info">
            <div className="info-row">
              <span className="info-label">현재가</span>
              <span className="info-value">{formatPrice(stock.currentPrice)}원</span>
            </div>
            <div className="info-row">
              <span className="info-label">변동가</span>
              <span className={`info-value ${getChangeClass(stock.changeAmount)}`}>
                {formatSignedNumber(stock.changeAmount)}원
              </span>
            </div>
            <div className="info-row">
              <span className="info-label">변동률</span>
              <span className={`info-value ${getChangeClass(stock.changeRate)}`}>
                {formatSignedNumber(stock.changeRate)}%
              </span>
            </div>
          </div>
          {onCompare && (
            <button type="button" className="compare-add-button" onClick={() => onCompare(stock)}>
              비교에 추가
            </button>
          )}
        </div>
      ))}
    </section>
  );
}

export default StockResultList;
