import { useState } from 'react';

function Watchlist({ codes, onSearch, onRemove, onUpdate }) {
  const [editingCode, setEditingCode] = useState(null);
  const [editGroupName, setEditGroupName] = useState('');
  const [editNote, setEditNote] = useState('');

  const startEdit = (item) => {
    setEditingCode(item.code);
    setEditGroupName(item.groupName || '');
    setEditNote(item.note || '');
  };

  const cancelEdit = () => {
    setEditingCode(null);
    setEditGroupName('');
    setEditNote('');
  };

  const saveEdit = (code) => {
    onUpdate(code, editGroupName, editNote);
    cancelEdit();
  };

  // 그룹별로 분류
  const grouped = {};
  codes.forEach((item) => {
    const group = item.groupName || '미분류';
    if (!grouped[group]) grouped[group] = [];
    grouped[group].push(item);
  });

  return (
    <section className="watchlist" aria-labelledby="watchlist-title">
      <div className="market-discovery-heading">
        <div>
          <h2 id="watchlist-title">관심종목</h2>
          <p>
            {codes.length > 0
              ? `${codes.length}개 종목 등록됨 · 종목을 클릭하면 차트로 이동합니다.`
              : '등록된 관심종목이 없습니다.'}
          </p>
        </div>
      </div>

      {codes.length === 0 ? (
        <p className="empty-state">종목 검색 또는 차트 화면에서 관심종목을 추가하세요.</p>
      ) : (
        Object.entries(grouped).map(([group, items]) => (
          <div key={group} className="watchlist-group">
            <h3 className="watchlist-group-title">
              {group}
              <span className="watchlist-group-count">{items.length}</span>
            </h3>
            <div className="watchlist-card-list">
              {items.map((item) => (
                <article
                  key={item.code}
                  className="watchlist-card"
                  onClick={() => onSearch(item.code)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') onSearch(item.code);
                  }}
                >
                  <div className="watchlist-card-header">
                    <div className="watchlist-card-identity">
                      <span className="watchlist-card-name">{item.name || item.code}</span>
                      <span className="watchlist-card-code">{item.code}</span>
                    </div>
                    <div className="watchlist-card-actions">
                      {editingCode === item.code ? (
                        <>
                          <button
                            type="button"
                            className="watchlist-btn-save"
                            onClick={(e) => {
                              e.stopPropagation();
                              saveEdit(item.code);
                            }}
                          >
                            저장
                          </button>
                          <button
                            type="button"
                            className="watchlist-btn-cancel"
                            onClick={(e) => {
                              e.stopPropagation();
                              cancelEdit();
                            }}
                          >
                            취소
                          </button>
                        </>
                      ) : (
                        <>
                          <button
                            type="button"
                            className="watchlist-btn-edit"
                            onClick={(e) => {
                              e.stopPropagation();
                              startEdit(item);
                            }}
                            aria-label={`${item.name || item.code} 메모 수정`}
                          >
                            수정
                          </button>
                          <button
                            type="button"
                            className="watchlist-btn-remove"
                            onClick={(e) => {
                              e.stopPropagation();
                              onRemove(item.code);
                            }}
                            aria-label={`${item.name || item.code} 관심종목 삭제`}
                          >
                            삭제
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                  {editingCode === item.code ? (
                    <div className="watchlist-edit-form" onClick={(e) => e.stopPropagation()}>
                      <label htmlFor={`wl-group-${item.code}`}>그룹명</label>
                      <input
                        id={`wl-group-${item.code}`}
                        value={editGroupName}
                        onChange={(e) => setEditGroupName(e.target.value)}
                        placeholder="그룹명"
                      />
                      <label htmlFor={`wl-note-${item.code}`}>메모</label>
                      <input
                        id={`wl-note-${item.code}`}
                        value={editNote}
                        onChange={(e) => setEditNote(e.target.value)}
                        placeholder="메모를 입력하세요"
                      />
                    </div>
                  ) : (
                    item.note && <p className="watchlist-card-note">{item.note}</p>
                  )}
                </article>
              ))}
            </div>
          </div>
        ))
      )}
    </section>
  );
}

export default Watchlist;
