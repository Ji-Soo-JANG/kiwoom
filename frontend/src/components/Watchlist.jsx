function Watchlist({ codes, onSearch, onRemove }) {
  return (
    <section className="watchlist" aria-labelledby="watchlist-title">
      <h2 id="watchlist-title">관심종목</h2>
      {codes.length === 0 ? (
        <p>등록된 관심종목이 없습니다.</p>
      ) : (
        <ul>
          {codes.map((code) => (
            <li key={code}>
              <button type="button" onClick={() => onSearch(code)}>
                {code} 조회
              </button>
              <button
                type="button"
                onClick={() => onRemove(code)}
                aria-label={`${code} 관심종목 삭제`}
              >
                삭제
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
export default Watchlist;
