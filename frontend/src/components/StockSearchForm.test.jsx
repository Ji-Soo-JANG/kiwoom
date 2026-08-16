import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import StockSearchForm from './StockSearchForm';

describe('StockSearchForm', () => {
  it('유효한 단일 종목 코드를 전달한다', () => {
    const onSingleSearch = vi.fn();
    render(<StockSearchForm loading={false} onSingleSearch={onSingleSearch} onMultipleSearch={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('종목 코드 (단일 조회)'), { target: { value: '005930' } });
    fireEvent.click(screen.getByRole('button', { name: '단일 조회' }));
    expect(onSingleSearch).toHaveBeenCalledWith('005930');
  });

  it('잘못된 코드는 접근 가능한 오류로 표시한다', () => {
    render(<StockSearchForm loading={false} onSingleSearch={vi.fn()} onMultipleSearch={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('종목 코드 (단일 조회)'), { target: { value: '123' } });
    fireEvent.click(screen.getByRole('button', { name: '단일 조회' }));
    expect(screen.getByRole('alert')).toHaveTextContent('6자리 숫자');
    expect(screen.getByLabelText('종목 코드 (단일 조회)')).toHaveAttribute('aria-invalid', 'true');
  });

  it('폼 제출로 키보드 검색을 지원한다', () => {
    const onSingleSearch = vi.fn();
    render(<StockSearchForm loading={false} onSingleSearch={onSingleSearch} onMultipleSearch={vi.fn()} />);
    const input = screen.getByLabelText('종목 코드 (단일 조회)');
    fireEvent.change(input, { target: { value: '005930' } });
    fireEvent.submit(input.closest('form'));
    expect(onSingleSearch).toHaveBeenCalledWith('005930');
  });
});
