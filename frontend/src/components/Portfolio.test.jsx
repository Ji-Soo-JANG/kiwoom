import {fireEvent, render, screen} from '@testing-library/react';
import {describe, expect, it, vi} from 'vitest';
import Portfolio from './Portfolio';

describe('Portfolio', () => {
    it('shows an empty state', () => {
        render(<Portfolio positions={[]} valuations={[]} loading={false}
                          onSave={vi.fn()} onRemove={vi.fn()} onValuate={vi.fn()}/>);
        expect(screen.getByText('등록된 보유 종목이 없습니다.')).toBeInTheDocument();
        expect(screen.getByRole('button', {name: '현재가로 평가'})).toBeDisabled();
    });

    it('submits a position and renders its valuation', async () => {
        const onSave = vi.fn().mockResolvedValue();
        render(<Portfolio positions={[{code: '005930', quantity: 10, averagePrice: 70000}]}
                          valuations={[{code: '005930', quantity: 10, averagePrice: 70000,
                              purchaseAmount: 700000, currentPrice: 75000, evaluationAmount: 750000,
                              profitLoss: 50000, returnRate: 7.14}]} loading={false}
                          onSave={onSave} onRemove={vi.fn()} onValuate={vi.fn()}/>);
        fireEvent.change(screen.getByLabelText('종목 코드'), {target: {value: '000660'}});
        fireEvent.change(screen.getByLabelText('보유 수량'), {target: {value: '2'}});
        fireEvent.change(screen.getByLabelText('평균 매입가'), {target: {value: '180000'}});
        fireEvent.click(screen.getByRole('button', {name: '저장'}));
        expect(onSave).toHaveBeenCalledWith('000660', 2, 180000);
        expect(screen.getAllByText('750,000원')).toHaveLength(2);
        expect(screen.getAllByText('7.14%')).toHaveLength(2);
        expect(screen.getByText('700,000원')).toBeInTheDocument();
        expect(screen.getByText('100.0%')).toBeInTheDocument();
    });

    it('기존 포지션을 폼에서 수정한다', async () => {
        const onSave = vi.fn().mockResolvedValue();
        render(<Portfolio positions={[{code: '005930', quantity: 10, averagePrice: 70000}]}
                          valuations={[]} loading={false} onSave={onSave}
                          onRemove={vi.fn()} onValuate={vi.fn()}/>);

        fireEvent.click(screen.getByRole('button', {name: '005930 포트폴리오 수정'}));
        expect(screen.getByLabelText('종목 코드')).toHaveValue('005930');
        expect(screen.getByLabelText('종목 코드')).toHaveAttribute('readonly');
        fireEvent.change(screen.getByLabelText('보유 수량'), {target: {value: '12'}});
        fireEvent.click(screen.getByRole('button', {name: '수정 저장'}));

        expect(onSave).toHaveBeenCalledWith('005930', 12, 70000);
    });
});
