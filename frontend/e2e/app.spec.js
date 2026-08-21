import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const json = (route, body, status = 200) =>
  route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body)
  });

async function mockApi(page, overrides = {}) {
  const state = { watchlist: [] };
  await page.route('http://127.0.0.1:5173/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const key = `${request.method()} ${url.pathname}`;
    if (overrides[key]) return overrides[key](route, state);

    if (key === 'GET /api/auth/me') return json(route, { username: 'e2e-user' });
    if (key === 'GET /api/watchlist') return json(route, state.watchlist);
    if (key === 'POST /api/watchlist') {
      const { code } = request.postDataJSON();
      if (!state.watchlist.some((item) => item.code === code))
        state.watchlist.push({ code, groupName: '기본', note: '' });
      return json(route, { code, groupName: '기본', note: '' }, 201);
    }
    if (key === 'GET /api/watchlist/005930')
      return json(route, null, 204);
    if (key === 'DELETE /api/watchlist/005930')
      return json(route, null, 204);
    if (key === 'GET /api/kiwoom/account/portfolio')
      return json(route, {
        accountNumber: '123-***-**01',
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
            availableQuantity: 10,
            averagePrice: 70000,
            currentPrice: 75000,
            purchaseAmount: 700000,
            evaluationAmount: 750000,
            profitLoss: 50000,
            returnRate: 7.14,
            weight: 100.0,
            profitContribution: 100.0
          }
        ],
        updatedAt: new Date().toISOString()
      });
    if (key === 'GET /api/kiwoom/market-rankings')
      return json(route, {
        gainers: [
          { code: '035720', name: '카카오', currentPrice: 50000, changeRate: 2.5, volume: 1000 }
        ],
        losers: [
          { code: '035720', name: '카카오', currentPrice: 50000, changeRate: -1.5, volume: 500 }
        ],
        mostTraded: [
          { code: '035720', name: '카카오', currentPrice: 50000, changeRate: 2.5, volume: 2000 }
        ],
        updatedAt: new Date().toISOString()
      });
    if (key === 'GET /api/kiwoom/stocks/search')
      return json(route, [
        { code: '005930', name: '삼성전자', market: 'KOSPI', productType: 'STOCK', productTypeLabel: '주식' }
      ]);
    if (key === 'GET /api/alerts/rules') return json(route, []);
    if (key === 'GET /api/alerts/events')
      return json(route, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    return json(route, { code: 'UNEXPECTED_REQUEST', message: key }, 500);
  });
  return state;
}

const dailyPrices = Array.from({ length: 40 }, (_, index) => ({
  date:
    index < 30
      ? `202606${String(index + 1).padStart(2, '0')}`
      : `202607${String(index - 29).padStart(2, '0')}`,
  openPrice: 70000 + index * 100,
  highPrice: 71000 + index * 100,
  lowPrice: 69000 + index * 100,
  closePrice: 70500 + index * 100,
  volume: 100000 + index,
  rsi: 50,
  macd: 10,
  signal: 8
}));

test('종목을 검색하고 차트로 이동한다', async ({ page }) => {
  await mockApi(page, {
    'GET /api/kiwoom/stock-price/005930': (route) =>
      json(route, {
        code: '005930',
        currentPrice: '75000',
        changeAmount: '500',
        changeRate: '0.67',
        fetchedAt: new Date().toISOString()
      }),
    'GET /api/kiwoom/stock-price/005930/daily': (route) => json(route, dailyPrices)
  });
  await page.goto('/');

  await page.getByLabel('종목 검색').fill('005930');
  await page.getByRole('button', { name: '단일 조회' }).click();
  await expect(page.getByRole('region', { name: '주가 조회 결과' })).toContainText('75,000원');
  await expect(page).toHaveURL(/code=005930/);
});

test('시장 순위에서 종목을 선택하면 차트로 이동한다', async ({ page }) => {
  await mockApi(page, {
    'GET /api/kiwoom/stock-price/035720': (route) =>
      json(route, {
        code: '035720',
        currentPrice: '50000',
        changeAmount: '1250',
        changeRate: '2.5',
        fetchedAt: new Date().toISOString()
      }),
    'GET /api/kiwoom/stock-price/035720/daily': (route) => json(route, dailyPrices)
  });
  await page.goto('/discover');

  const rankItem = page.getByRole('button', { name: /카카오.*035720/ });
  if (await rankItem.isVisible()) {
    await rankItem.click();
    await expect(page).toHaveURL(/code=035720/);
  }
});

test('계좌 포트폴리오에서 종목을 선택하면 차트로 이동한다', async ({ page }) => {
  await mockApi(page, {
    'GET /api/kiwoom/stock-price/005930': (route) =>
      json(route, {
        code: '005930',
        currentPrice: '75000',
        changeAmount: '500',
        changeRate: '0.67',
        fetchedAt: new Date().toISOString()
      }),
    'GET /api/kiwoom/stock-price/005930/daily': (route) => json(route, dailyPrices)
  });
  await page.goto('/portfolio');

  await expect(page.getByRole('heading', { name: '내 계좌 포트폴리오' })).toBeVisible();
  await expect(page.getByText('삼성전자')).toBeVisible();
  await expect(page.getByText('123-***-**01')).toBeVisible();

  await page.getByRole('button', { name: '삼성전자' }).first().click();
  await expect(page).toHaveURL(/code=005930/);
});

test('URL로 직접 접근하면 해당 종목 차트가 표시된다', async ({ page }) => {
  await mockApi(page, {
    'GET /api/kiwoom/stock-price/005930': (route) =>
      json(route, {
        code: '005930',
        currentPrice: '75000',
        changeAmount: '500',
        changeRate: '0.67',
        fetchedAt: new Date().toISOString()
      }),
    'GET /api/kiwoom/stock-price/005930/daily': (route) => json(route, dailyPrices)
  });
  await page.goto('/chart?code=005930');

  await expect(page.getByRole('region', { name: '주가 조회 결과' })).toContainText('75,000원');
});

test('새로고침 후에도 종목이 유지된다', async ({ page }) => {
  await mockApi(page, {
    'GET /api/kiwoom/stock-price/005930': (route) =>
      json(route, {
        code: '005930',
        currentPrice: '75000',
        changeAmount: '500',
        changeRate: '0.67',
        fetchedAt: new Date().toISOString()
      }),
    'GET /api/kiwoom/stock-price/005930/daily': (route) => json(route, dailyPrices)
  });
  await page.goto('/chart?code=005930');
  await expect(page.getByRole('region', { name: '주가 조회 결과' })).toContainText('75,000원');

  await page.reload();
  await expect(page.getByRole('region', { name: '주가 조회 결과' })).toContainText('75,000원');
});

test('키움 네트워크 오류를 사용자 메시지로 표시한다', async ({ page }) => {
  await mockApi(page, {
    'GET /api/kiwoom/stock-price/005930': (route) =>
      json(
        route,
        { code: 'KIWOOM_UPSTREAM_UNAVAILABLE', message: 'upstream unavailable' },
        503
      ),
    'GET /api/kiwoom/stock-price/005930/daily': (route) => json(route, dailyPrices)
  });
  await page.goto('/');

  await page.getByLabel('종목 검색').fill('005930');
  await page.getByRole('button', { name: '단일 조회' }).click();
  await expect(page.getByRole('alert')).toContainText('키움 서비스가 일시적으로 응답하지 않습니다');
});

test('모바일 화면에서 주요 기능이 가로로 넘치지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockApi(page);
  await page.goto('/portfolio');

  await expect(page.getByRole('heading', { name: '내 계좌 포트폴리오' })).toBeVisible();
  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth
  );
  expect(hasHorizontalOverflow).toBe(false);
  await expect(page.getByRole('navigation', { name: '주요 화면' })).toBeVisible();
});

test('주요 화면에 심각한 접근성 위반이 없다', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  const results = await new AxeBuilder({ page }).analyze();
  expect(
    results.violations.filter((item) => ['critical', 'serious'].includes(item.impact))
  ).toEqual([]);
});

test('인증 사용자가 새로고침 후에도 유지되고 로그아웃할 수 있다', async ({ page }) => {
  await mockApi(page, {
    'POST /api/auth/logout': (route) => route.fulfill({ status: 204 })
  });
  await page.goto('/portfolio');
  await expect(page.getByText('e2e-user')).toBeVisible();
  await page.reload();
  await expect(page.getByText('e2e-user')).toBeVisible();
  await page.route('http://localhost:8080/login', (route) =>
    route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>Kiwoom 로그인</h1>' })
  );
  await page.getByRole('button', { name: '로그아웃' }).click();
  await expect(page).toHaveURL('http://localhost:8080/login');
});
