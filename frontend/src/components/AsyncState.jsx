export function LoadingState({ children = '불러오는 중...' }) {
  return (
    <div className="loading-text" role="status" aria-live="polite">
      {children}
    </div>
  );
}

export function ErrorState({ children, onRetry }) {
  return (
    <div className="error" role="alert">
      <span>{children}</span>
      {onRetry && (
        <button type="button" onClick={onRetry}>
          다시 시도
        </button>
      )}
    </div>
  );
}

export function EmptyState({ children }) {
  return <p className="empty-state">{children}</p>;
}
