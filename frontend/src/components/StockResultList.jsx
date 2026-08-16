import {formatPrice, formatSignedNumber, getChangeClass} from '../utils/stockFormat';

function StockResultList({stocks, searched = false, loading = false}) {
    if (stocks.length === 0) {
        return searched && !loading
            ? <p className="empty-state" role="status">조회된 종목이 없습니다.</p>
            : null;
    }

    return (
        <section className="results" aria-label="주가 조회 결과" aria-live="polite">
            {stocks.map((stock, index) => (
                <div
                    key={stock.code || index}
                    className="result-item"
                >
                    <div className="stock-code">
                        {stock.code}
                    </div>

                    <div className="stock-info">
                        <div className="info-row">
              <span className="info-label">
                현재가
              </span>

                            <span className="info-value">
                {formatPrice(stock.currentPrice)}원
              </span>
                        </div>

                        <div className="info-row">
              <span className="info-label">
                변동가
              </span>

                            <span
                                className={
                                    `info-value ${
                                        getChangeClass(
                                            stock.changeAmount
                                        )
                                    }`
                                }
                            >
                {formatSignedNumber(
                    stock.changeAmount
                )}원
              </span>
                        </div>

                        <div className="info-row">
              <span className="info-label">
                변동률
              </span>

                            <span
                                className={
                                    `info-value ${
                                        getChangeClass(
                                            stock.changeRate
                                        )
                                    }`
                                }
                            >
                {formatSignedNumber(
                    stock.changeRate
                )}%
              </span>
                        </div>
                    </div>
                </div>
            ))}
        </section>
    );
}

export default StockResultList;
