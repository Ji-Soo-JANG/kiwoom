const ema = (values, period) => {
  const multiplier = 2 / (period + 1);
  const result = Array(values.length).fill(null);
  if (values.length < period) return result;

  let current = values.slice(0, period).reduce((sum, value) => sum + value, 0) / period;
  result[period - 1] = current;
  for (let index = period; index < values.length; index += 1) {
    current = (values[index] - current) * multiplier + current;
    result[index] = current;
  }
  return result;
};

export const calculateRsi = (values, period = 14) => {
  const result = Array(values.length).fill(null);
  if (values.length <= period) return result;

  let gains = 0;
  let losses = 0;
  for (let index = 1; index <= period; index += 1) {
    const change = values[index] - values[index - 1];
    gains += Math.max(change, 0);
    losses += Math.max(-change, 0);
  }

  let averageGain = gains / period;
  let averageLoss = losses / period;
  result[period] = averageLoss === 0 ? 100 : 100 - (100 / (1 + averageGain / averageLoss));

  for (let index = period + 1; index < values.length; index += 1) {
    const change = values[index] - values[index - 1];
    averageGain = ((averageGain * (period - 1)) + Math.max(change, 0)) / period;
    averageLoss = ((averageLoss * (period - 1)) + Math.max(-change, 0)) / period;
    result[index] = averageLoss === 0 ? 100 : 100 - (100 / (1 + averageGain / averageLoss));
  }
  return result;
};

export const calculateMacd = (values) => {
  const fast = ema(values, 12);
  const slow = ema(values, 26);
  const macd = values.map((_, index) =>
    fast[index] == null || slow[index] == null ? null : fast[index] - slow[index]
  );
  const available = macd.filter((value) => value != null);
  const signalValues = ema(available, 9);
  let signalIndex = 0;
  const signal = macd.map((value) => value == null ? null : signalValues[signalIndex++]);
  return { macd, signal };
};

export const addTechnicalIndicators = (dailyPrices) => {
  const closes = dailyPrices.map((item) => Number(item.closePrice));
  const rsi = calculateRsi(closes);
  const { macd, signal } = calculateMacd(closes);
  return dailyPrices.map((item, index) => ({
    ...item,
    rsi: rsi[index],
    macd: macd[index],
    signal: signal[index]
  }));
};
