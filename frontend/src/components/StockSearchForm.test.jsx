import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StockSearchForm from './StockSearchForm';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as api from '../api/kiwoomApi';

vi.mock('../api/kiwoomApi');

const renderForm = (props = {}) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const defaults = { loading: false, onSingleSearch: vi.fn(), onMultipleSearch: vi.fn() };
  return render(
    <QueryClientProvider client={client}>
      <StockSearchForm {...defaults} {...props} />
    </QueryClientProvider>
  );
};

describe('StockSearchForm', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    api.searchStocks.mockResolvedValue([]);
  });

  it('유효한 단일 종목 코드를 전달한다', () => {
    const onSingleSearch = vi.fn();
    renderForm({ onSingleSearch });
    fireEvent.change(screen.getByLabelText('종목 검색'), {
      target: { value: '005930' }
    });
    fireEvent.click(screen.getByRole('button', { name: '단일 조회' }));
    expect(onSingleSearch).toHaveBeenCalledWith('005930');
  });

  it('검색 결과가 없으면 접근 가능한 오류로 표시한다', async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText('종목 검색'), { target: { value: '없는종목' } });
    await waitFor(() => expect(api.searchStocks).toHaveBeenCalledWith('없는종목', 'ALL'));
    await waitFor(() => expect(screen.queryByText('종목을 찾는 중...')).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '단일 조회' }));
    expect(screen.getByRole('alert')).toHaveTextContent('일치하는 종목');
    expect(screen.getByLabelText('종목 검색')).toHaveAttribute('aria-invalid', 'true');
  });

  it('폼 제출로 키보드 검색을 지원한다', () => {
    const onSingleSearch = vi.fn();
    renderForm({ onSingleSearch });
    const input = screen.getByLabelText('종목 검색');
    fireEvent.change(input, { target: { value: '005930' } });
    fireEvent.submit(input.closest('form'));
    expect(onSingleSearch).toHaveBeenCalledWith('005930');
  });

  it('종목명 자동완성 선택을 코드 검색과 최근 기록으로 연결한다', async () => {
    api.searchStocks.mockResolvedValue([{ code: '005930', name: '삼성전자', market: 'KOSPI' }]);
    const onSingleSearch = vi.fn();
    renderForm({ onSingleSearch });

    fireEvent.change(screen.getByLabelText('종목 검색'), { target: { value: '삼성' } });
    fireEvent.click(await screen.findByRole('button', { name: /삼성전자 005930/ }));
    fireEvent.click(screen.getByRole('button', { name: '단일 조회' }));

    expect(onSingleSearch).toHaveBeenCalledWith('005930');
    expect(screen.getByLabelText('최근 검색 종목')).toHaveTextContent('삼성전자');
  });

  it('종목명이나 초성 검색 후 Enter로 첫 결과를 바로 조회한다', async () => {
    api.searchStocks.mockResolvedValue([{ code: '005930', name: '삼성전자', market: 'KOSPI' }]);
    const onSingleSearch = vi.fn();
    renderForm({ onSingleSearch });

    const input = screen.getByLabelText('종목 검색');
    fireEvent.change(input, { target: { value: 'ㅅㅅㅈㅈ' } });
    await screen.findByRole('button', { name: /삼성전자 005930/ });
    fireEvent.submit(input.closest('form'));

    expect(onSingleSearch).toHaveBeenCalledWith('005930');
  });
});
