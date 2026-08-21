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
          returnRate: 7.14,
          weight: 100.0,
          profitContribution: 100.0
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

    // 요약 영역에 총 매입금액 표시 확인
    expect(screen.getAllByText(/700,000원/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/7\.14%/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('heading', { name: '계좌 분석' })).toBeInTheDocument();
    expect(screen.getByText('현금 추정액').nextSibling).toHaveTextContent('250,000원');
    expect(screen.getByText(/키움 총 평가금액 차이/)).toBeInTheDocument();

    // 종목 클릭 시 onSelectStock 호출
    fireEvent.click(screen.getAllByText('삼성전자')[0]);
    expect(onSelectStock).toHaveBeenCalledWith('005930');
  });
});
