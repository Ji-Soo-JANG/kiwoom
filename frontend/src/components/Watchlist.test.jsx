import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Watchlist from './Watchlist';

describe('Watchlist', () => {
  it('빈 목록을 안내한다', () => {
    render(<Watchlist codes={[]} onSearch={vi.fn()} onRemove={vi.fn()} onUpdate={vi.fn()} />);
    expect(screen.getByText('등록된 관심종목이 없습니다.')).toBeInTheDocument();
  });
  it('조회와 삭제 동작을 전달한다', () => {
    const onSearch = vi.fn();
    const onRemove = vi.fn();
    render(
      <Watchlist
        codes={[{ code: '005930', groupName: '반도체', note: '장기 관찰' }]}
        onSearch={onSearch}
        onRemove={onRemove}
        onUpdate={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: '005930 조회' }));
    fireEvent.click(screen.getByRole('button', { name: '005930 관심종목 삭제' }));
    expect(onSearch).toHaveBeenCalledWith('005930');
    expect(onRemove).toHaveBeenCalledWith('005930');
  });
  it('그룹과 메모 수정 동작을 전달한다', () => {
    const onUpdate = vi.fn();
    vi.spyOn(window, 'prompt').mockReturnValueOnce('장기').mockReturnValueOnce('분할 매수');
    render(
      <Watchlist
        codes={[{ code: '005930', groupName: '반도체', note: '' }]}
        onSearch={vi.fn()}
        onRemove={vi.fn()}
        onUpdate={onUpdate}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: '005930 관심종목 메모 수정' }));

    expect(onUpdate).toHaveBeenCalledWith('005930', '장기', '분할 매수');
    vi.restoreAllMocks();
  });
});
