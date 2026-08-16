import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import * as api from './api/kiwoomApi';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('./api/kiwoomApi');

describe('App routes', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.getWatchlist.mockResolvedValue([]);
    api.getAccountPortfolio.mockResolvedValue({
      accountNumber: '1234567890',
      totalPurchaseAmount: 0,
      totalEvaluationAmount: 0,
      totalProfitLoss: 0,
      totalReturnRate: 0,
      estimatedAssets: 0,
      positions: []
    });
    api.getAlertRules.mockResolvedValue([]);
    api.getAlertEvents.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 });
  });

  it('내비게이션으로 포트폴리오와 알림 화면을 분리해 표시한다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/']}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>
    );
    expect(screen.getByLabelText('종목 검색')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: '포트폴리오' }));
    expect(
      await screen.findByRole('heading', { name: '내 계좌 포트폴리오' })
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('종목 검색')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: '알림' }));
    expect(await screen.findByRole('heading', { name: '주가·지표 알림' })).toBeInTheDocument();
  });

  it('관심종목 추가 API에 종목 코드만 전달한다', async () => {
    api.getCurrentPrice.mockResolvedValue({
      code: '005930',
      currentPrice: '70000',
      changeAmount: '1000',
      changeRate: '1.45'
    });
    api.getDailyPrices.mockResolvedValue([]);
    api.addToWatchlist.mockResolvedValue({ code: '005930', groupName: '기본', note: '' });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/']}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>
    );

    fireEvent.change(screen.getByLabelText('종목 검색'), {
      target: { value: '005930' }
    });
    fireEvent.click(screen.getByRole('button', { name: '단일 조회' }));
    fireEvent.click(await screen.findByRole('button', { name: '관심종목 추가' }));

    await waitFor(() => expect(api.addToWatchlist).toHaveBeenCalledWith('005930'));
  });
});
