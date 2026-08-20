import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Watchlist from './Watchlist';

describe('Watchlist', () => {
  it('빈 목록을 안내한다', () => {
    render(<Watchlist codes={[]} onSearch={vi.fn()} onRemove={vi.fn()} onUpdate={vi.fn()} />);
    expect(screen.getByText('등록된 관심종목이 없습니다.')).toBeInTheDocument();
  });

  it('종목 카드를 그룹별로 표시하고 클릭 시 차트로 이동한다', () => {
    const onSearch = vi.fn();
    render(
      <Watchlist
        codes={[
          { code: '005930', name: '삼성전자', groupName: '반도체', note: '장기 관찰' },
          { code: '000660', name: 'SK하이닉스', groupName: '반도체', note: '' },
          { code: '035420', name: 'NAVER', groupName: '인터넷', note: '성장주' }
        ]}
        onSearch={onSearch}
        onRemove={vi.fn()}
        onUpdate={vi.fn()}
      />
    );

    // 종목명과 종목코드 표시
    expect(screen.getByText('삼성전자')).toBeInTheDocument();
    expect(screen.getByText('005930')).toBeInTheDocument();
    expect(screen.getByText('SK하이닉스')).toBeInTheDocument();
    expect(screen.getByText('NAVER')).toBeInTheDocument();

    // 그룹 헤더 표시 (미분류 포함)
    expect(screen.getByText('반도체')).toBeInTheDocument();
    expect(screen.getByText('인터넷')).toBeInTheDocument();

    // 메모 표시
    expect(screen.getByText('장기 관찰')).toBeInTheDocument();
    expect(screen.getByText('성장주')).toBeInTheDocument();

    // 종목 코드 클릭 시 onSearch 호출
    fireEvent.click(screen.getByText('005930'));
    expect(onSearch).toHaveBeenCalledWith('005930');
  });

  it('그룹과 메모를 인라인으로 수정한다', () => {
    const onUpdate = vi.fn();
    render(
      <Watchlist
        codes={[{ code: '005930', name: '삼성전자', groupName: '반도체', note: '메모' }]}
        onSearch={vi.fn()}
        onRemove={vi.fn()}
        onUpdate={onUpdate}
      />
    );

    // 수정 버튼 클릭
    fireEvent.click(screen.getByRole('button', { name: '삼성전자 메모 수정' }));

    // 인라인 폼 표시
    const groupInput = screen.getByLabelText('그룹명');
    const noteInput = screen.getByLabelText('메모');
    expect(groupInput).toHaveValue('반도체');
    expect(noteInput).toHaveValue('메모');

    // 값 변경 후 저장
    fireEvent.change(groupInput, { target: { value: '장기' } });
    fireEvent.change(noteInput, { target: { value: '분할 매수' } });
    fireEvent.click(screen.getByText('저장'));

    expect(onUpdate).toHaveBeenCalledWith('005930', '장기', '분할 매수');
  });

  it('삭제 버튼이 클릭 이벤트를 전파하지 않는다', () => {
    const onRemove = vi.fn();
    const onSearch = vi.fn();
    render(
      <Watchlist
        codes={[{ code: '005930', name: '삼성전자', groupName: '반도체', note: '' }]}
        onSearch={onSearch}
        onRemove={onRemove}
        onUpdate={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: '삼성전자 관심종목 삭제' }));
    expect(onRemove).toHaveBeenCalledWith('005930');
    expect(onSearch).not.toHaveBeenCalled();
  });
});
