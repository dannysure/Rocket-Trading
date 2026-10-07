import { MarketQuote } from './market-data.service';

export type PositionSide = 'long' | 'short';

export interface MaintenanceTier {
  readonly maxNotionalUsd: number;
  readonly maintenanceMarginRate: number;
  readonly maxLeverage: number;
}

export interface PerpetualMarketConfig {
  readonly id: string;
  readonly symbol: string;
  readonly displayName: string;
  readonly assetSymbol: string;
  readonly quoteCurrency: string;
  readonly settlementAsset: string;
  readonly spotSymbol: string;
  readonly tradingViewSymbol: string;
  readonly contractSize: number;
  readonly annualInterestRate: number;
  readonly fundingIntervalHours: number;
  readonly fundingClampBps: number;
  readonly impactNotionalUsd: number;
  readonly referenceSpotPrice: number;
  readonly referenceBasisBps: number;
  readonly referenceOpenInterestUsd: number;
  readonly referenceVolume24hUsd: number;
  readonly referenceInsuranceFundUsd: number;
  readonly makerFeeRate: number;
  readonly takerFeeRate: number;
  readonly maintenanceTiers: readonly MaintenanceTier[];
}

export interface FundingInputs {
  readonly basisAdjustmentBps: number;
  readonly inventorySkewBps: number;
}

export interface FundingSnapshot {
  readonly quote: MarketQuote;
  readonly indexPrice: number;
  readonly markPrice: number;
  readonly basisBps: number;
  readonly premiumIndex: number;
  readonly interestRatePerInterval: number;
  readonly fundingRate: number;
  readonly annualizedFundingRate: number;
  readonly fundingDirection: 'longs-pay-shorts' | 'shorts-pay-longs' | 'neutral';
  readonly longShare: number;
  readonly shortShare: number;
  readonly longShortRatio: number;
  readonly openInterestUsd: number;
  readonly volume24hUsd: number;
  readonly insuranceFundUsd: number;
  readonly nextFundingAt: string;
  readonly pegMessage: string;
}

export interface PositionSummary {
  readonly notionalUsd: number;
  readonly initialMarginUsd: number;
  readonly maintenanceMarginRate: number;
  readonly maintenanceMarginUsd: number;
  readonly liquidationPrice: number;
  readonly nextFundingCashflowUsd: number;
}

export const PERPETUAL_MARKETS: readonly PerpetualMarketConfig[] = [
  {
    id: 'eth-usd-perp',
    symbol: 'ETH-PERP',
    displayName: 'Ethereum Perpetual Market',
    assetSymbol: 'ETH',
    quoteCurrency: 'USD',
    settlementAsset: 'USDC',
    spotSymbol: 'ETH-USD',
    tradingViewSymbol: 'BITSTAMP:ETHUSD',
    contractSize: 1,
    annualInterestRate: 0.05,
    fundingIntervalHours: 8,
    fundingClampBps: 5,
    impactNotionalUsd: 250_000,
    referenceSpotPrice: 3_450,
    referenceBasisBps: 2,
    referenceOpenInterestUsd: 428_000_000,
    referenceVolume24hUsd: 1_860_000_000,
    referenceInsuranceFundUsd: 38_500_000,
    makerFeeRate: 0.0002,
    takerFeeRate: 0.00055,
    maintenanceTiers: [
      { maxNotionalUsd: 250_000, maintenanceMarginRate: 0.005, maxLeverage: 50 },
      { maxNotionalUsd: 1_000_000, maintenanceMarginRate: 0.01, maxLeverage: 25 },
      { maxNotionalUsd: 5_000_000, maintenanceMarginRate: 0.02, maxLeverage: 10 },
    ],
  },
];

export const DEFAULT_PERPETUAL_MARKET = PERPETUAL_MARKETS[0];

export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

export function calculateMarkPrice(indexPrice: number, basisBps: number): number {
  return indexPrice * (1 + basisBps / 10_000);
}

export function calculatePremiumIndex(markPrice: number, indexPrice: number): number {
  return (markPrice - indexPrice) / indexPrice;
}

export function calculateInterestRatePerInterval(
  annualInterestRate: number,
  fundingIntervalHours: number,
): number {
  const intervalsPerYear = (365 * 24) / fundingIntervalHours;
  return annualInterestRate / intervalsPerYear;
}

export function calculateFundingRate(
  premiumIndex: number,
  annualInterestRate: number,
  fundingIntervalHours: number,
  fundingClampBps: number,
): number {
  const clampRate = fundingClampBps / 10_000;
  const interestRatePerInterval = calculateInterestRatePerInterval(
    annualInterestRate,
    fundingIntervalHours,
  );
  const boundedSpread = clamp(
    interestRatePerInterval - premiumIndex,
    -clampRate,
    clampRate,
  );

  return premiumIndex + boundedSpread;
}

export function annualizeFundingRate(
  fundingRate: number,
  fundingIntervalHours: number,
): number {
  return fundingRate * (24 / fundingIntervalHours) * 365;
}

export function resolveMaintenanceTier(
  notionalUsd: number,
  maintenanceTiers: readonly MaintenanceTier[],
): MaintenanceTier {
  return maintenanceTiers.find((tier) => notionalUsd <= tier.maxNotionalUsd) ?? maintenanceTiers.at(-1)!;
}

export function calculateFundingCashflowUsd(
  notionalUsd: number,
  fundingRate: number,
  side: PositionSide,
): number {
  const absolutePayment = notionalUsd * fundingRate;

  if (fundingRate === 0) {
    return 0;
  }

  if (fundingRate > 0) {
    return side === 'long' ? -absolutePayment : absolutePayment;
  }

  return side === 'long' ? Math.abs(absolutePayment) : -Math.abs(absolutePayment);
}

export function calculateLiquidationPrice(
  entryPrice: number,
  leverage: number,
  maintenanceMarginRate: number,
  side: PositionSide,
): number {
  const initialMarginRate = 1 / leverage;
  const multiplier =
    side === 'long'
      ? 1 - initialMarginRate + maintenanceMarginRate
      : 1 + initialMarginRate - maintenanceMarginRate;

  return entryPrice * multiplier;
}

export function createReferenceQuote(config: PerpetualMarketConfig): MarketQuote {
  return {
    symbol: config.spotSymbol,
    price: config.referenceSpotPrice,
    bid: config.referenceSpotPrice - 2,
    ask: config.referenceSpotPrice + 2,
    spreadBps: 0.58,
    currency: config.quoteCurrency,
    change: 32,
    changePercent: 0.94,
    previousClose: config.referenceSpotPrice - 32,
    asOf: new Date().toISOString(),
    marketState: 'REFERENCE',
    disclaimer: 'Reference snapshot generated in the frontend.',
    source: 'Local model',
    stale: true,
    spreadSource: 'modeled',
  };
}

export function buildFundingSnapshot(
  config: PerpetualMarketConfig,
  quote: MarketQuote,
  inputs: FundingInputs,
): FundingSnapshot {
  const indexPrice = quote.price;
  const longShare = clamp(0.5 + inputs.inventorySkewBps / 200, 0.38, 0.62);
  const shortShare = 1 - longShare;
  const basisBps = clamp(
    config.referenceBasisBps + inputs.basisAdjustmentBps + inputs.inventorySkewBps * 0.75,
    -75,
    75,
  );
  const markPrice = calculateMarkPrice(indexPrice, basisBps);
  const premiumIndex = calculatePremiumIndex(markPrice, indexPrice);
  const fundingRate = calculateFundingRate(
    premiumIndex,
    config.annualInterestRate,
    config.fundingIntervalHours,
    config.fundingClampBps,
  );
  const annualizedFundingRate = annualizeFundingRate(
    fundingRate,
    config.fundingIntervalHours,
  );
  const direction =
    fundingRate > 0 ? 'longs-pay-shorts' : fundingRate < 0 ? 'shorts-pay-longs' : 'neutral';
  const nextFundingAt = getNextFundingTimestamp(config.fundingIntervalHours);

  return {
    quote,
    indexPrice,
    markPrice,
    basisBps,
    premiumIndex,
    interestRatePerInterval: calculateInterestRatePerInterval(
      config.annualInterestRate,
      config.fundingIntervalHours,
    ),
    fundingRate,
    annualizedFundingRate,
    fundingDirection: direction,
    longShare,
    shortShare,
    longShortRatio: longShare / shortShare,
    openInterestUsd: config.referenceOpenInterestUsd * (1 + inputs.inventorySkewBps / 500),
    volume24hUsd: config.referenceVolume24hUsd * (1 + Math.abs(basisBps) / 250),
    insuranceFundUsd: config.referenceInsuranceFundUsd,
    nextFundingAt,
    pegMessage: buildPegMessage(direction, basisBps),
  };
}

export function calculatePositionSummary(
  snapshot: FundingSnapshot,
  config: PerpetualMarketConfig,
  side: PositionSide,
  contracts: number,
  leverage: number,
): PositionSummary {
  const quantity = Math.max(contracts, 0);
  const boundedLeverage = clamp(leverage, 1, config.maintenanceTiers[0].maxLeverage);
  const notionalUsd = snapshot.markPrice * config.contractSize * quantity;
  const initialMarginUsd = notionalUsd / boundedLeverage;
  const maintenanceTier = resolveMaintenanceTier(notionalUsd, config.maintenanceTiers);
  const maintenanceMarginUsd = notionalUsd * maintenanceTier.maintenanceMarginRate;

  return {
    notionalUsd,
    initialMarginUsd,
    maintenanceMarginRate: maintenanceTier.maintenanceMarginRate,
    maintenanceMarginUsd,
    liquidationPrice: calculateLiquidationPrice(
      snapshot.markPrice,
      boundedLeverage,
      maintenanceTier.maintenanceMarginRate,
      side,
    ),
    nextFundingCashflowUsd: calculateFundingCashflowUsd(
      notionalUsd,
      snapshot.fundingRate,
      side,
    ),
  };
}

function buildPegMessage(
  direction: FundingSnapshot['fundingDirection'],
  basisBps: number,
): string {
  if (direction === 'longs-pay-shorts') {
    return `Positive funding compensates shorts while basis sits ${basisBps.toFixed(
      1,
    )} bps above spot, creating pressure for the perp to trade back down toward index.`;
  }

  if (direction === 'shorts-pay-longs') {
    return `Negative funding compensates longs while basis sits ${Math.abs(basisBps).toFixed(
      1,
    )} bps below spot, creating pressure for the perp to trade back up toward index.`;
  }

  return 'Flat funding means the contract is already trading close enough to spot that the peg needs little additional pressure.';
}

function getNextFundingTimestamp(fundingIntervalHours: number): string {
  const now = new Date();
  const nextFunding = new Date(now);
  nextFunding.setUTCMinutes(0, 0, 0);

  const currentHour = now.getUTCHours();
  const nextHour = Math.ceil((currentHour + 1) / fundingIntervalHours) * fundingIntervalHours;

  if (nextHour >= 24) {
    nextFunding.setUTCDate(nextFunding.getUTCDate() + 1);
    nextFunding.setUTCHours(nextHour - 24, 0, 0, 0);
    return nextFunding.toISOString();
  }

  nextFunding.setUTCHours(nextHour, 0, 0, 0);
  return nextFunding.toISOString();
}
