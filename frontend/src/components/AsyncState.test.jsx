import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EmptyState, ErrorState, LoadingState } from './AsyncState';

describe('공통 비동기 상태', () => {
  it('로딩과 빈 상태를 표시한다', () => {
    const { rerender } = render(<LoadingState />);
    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중...');
    rerender(<EmptyState>결과가 없습니다.</EmptyState>);
    expect(screen.getByText('결과가 없습니다.')).toBeInTheDocument();
  });

  it('오류 재시도를 전달한다', () => {
    const onRetry = vi.fn();
    render(<ErrorState onRetry={onRetry}>실패했습니다.</ErrorState>);
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
