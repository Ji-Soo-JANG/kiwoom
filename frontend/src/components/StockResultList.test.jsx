import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StockResultList from './StockResultList';

describe('StockResultList', () => {
  it('현재가와 변동률을 표시한다', () => {
    render(<StockResultList stocks={[{ code: '005930', currentPrice: '75000', changeAmount: '+500', changeRate: '+0.67' }]} />);
    expect(screen.getByText('005930')).toBeInTheDocument();
    expect(screen.getByText('75,000원')).toBeInTheDocument();
    expect(screen.getByText('+0.67%')).toBeInTheDocument();
  });
});
