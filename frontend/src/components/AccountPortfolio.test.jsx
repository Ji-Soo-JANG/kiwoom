import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/kiwoomApi';
import AccountPortfolio from './AccountPortfolio';

vi.mock('../api/kiwoomApi');

describe('AccountPortfolio', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.getAccountPortfolio.mockResolvedValue({
      accountNumber: '1234567890',
      totalPurchaseAmount: 700000,
      totalEvaluationAmount: 750000,
      totalProfitLoss: 50000,
      totalReturnRate: 7.14,
      estimatedAssets: 1000000,
      positions: [
        {
          code: '005930',
          name: '삼성전자',
          quantity: 10,
          availableQuantity: 5,
          averagePrice: 70000,
          currentPrice: 75000,
          purchaseAmount: 700000,
          evaluationAmount: 750000,
          profitLoss: 50000,
          returnRate: 7.14
        }
      ]
    });
  });

  it('키움 계좌 요약과 보유종목을 표시하고 종목 선택을 전달한다', async () => {
    const onSelectStock = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <AccountPortfolio onSelectStock={onSelectStock} />
      </QueryClientProvider>
    );

    expect(await screen.findByText(/1234567890/)).toBeInTheDocument();

    // 종목명 (테이블 + 카드 뷰에 각각 존재)
    expect(screen.getAllByText('삼성전자').length).toBeGreaterThanOrEqual(1);

    // 종목코드 (테이블 + 카드 뷰)
    expect(screen.getAllByText('005930').length).toBeGreaterThanOrEqual(2);

    // 매입금액, 평균단가, 수익률 표시 확인 (요약+테이블+카드에 여러 번 노출)
    expect(screen.getAllByText(/700,000원/).length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText(/70,000원/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/7\.14%/).length).toBeGreaterThanOrEqual(1);

    // 종목 클릭 시 onSelectStock 호출
    fireEvent.click(screen.getAllByText('삼성전자')[0]);
    expect(onSelectStock).toHaveBeenCalledWith('005930');
  });
});
