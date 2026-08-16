function Watchlist({ codes, onSearch, onRemove, onUpdate }) {
  return (
    <section className="watchlist" aria-labelledby="watchlist-title">
      <h2 id="watchlist-title">관심종목</h2>
      {codes.length === 0 ? (
        <p>등록된 관심종목이 없습니다.</p>
      ) : (
        <ul>
          {codes.map((item) => (
            <li key={item.code}>
              <button type="button" onClick={() => onSearch(item.code)}>
                {item.code} 조회
              </button>
              <span>{item.groupName}</span>
              {item.note && <span>{item.note}</span>}
              <button
                type="button"
                onClick={() => {
                  const groupName = window.prompt('그룹명', item.groupName);
                  if (groupName == null) return;
                  const note = window.prompt('메모', item.note);
                  if (note != null) onUpdate(item.code, groupName, note);
                }}
                aria-label={`${item.code} 관심종목 메모 수정`}
              >
                메모
              </button>
              <button
                type="button"
                onClick={() => onRemove(item.code)}
                aria-label={`${item.code} 관심종목 삭제`}
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
