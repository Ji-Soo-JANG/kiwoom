import type { components, paths } from './generated/openapi';

export type AuthUser = components['schemas']['AuthUser'];
export type StockPrice = components['schemas']['StockPriceResponse'];
export type DailyPrice = components['schemas']['DailyPriceResponse'];
export type DailyPriceOperation = paths['/api/kiwoom/stock-price/{code}/daily']['get'];

const validUser: AuthUser = { username: 'type-check' };
void validUser;
