import {fireEvent, render, screen} from '@testing-library/react';
import {describe, expect, it, vi} from 'vitest';
import Watchlist from './Watchlist';

describe('Watchlist', () => {
    it('빈 목록을 안내한다', () => {
        render(<Watchlist codes={[]} onSearch={vi.fn()} onRemove={vi.fn()}/>);
        expect(screen.getByText('등록된 관심종목이 없습니다.')).toBeInTheDocument();
    });
    it('조회와 삭제 동작을 전달한다', () => {
        const onSearch = vi.fn(); const onRemove = vi.fn();
        render(<Watchlist codes={['005930']} onSearch={onSearch} onRemove={onRemove}/>);
        fireEvent.click(screen.getByRole('button', {name: '005930 조회'}));
        fireEvent.click(screen.getByRole('button', {name: '005930 관심종목 삭제'}));
        expect(onSearch).toHaveBeenCalledWith('005930');
        expect(onRemove).toHaveBeenCalledWith('005930');
    });
});
