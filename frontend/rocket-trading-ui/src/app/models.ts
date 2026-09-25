export interface ApiResponse<T> {
  data: T;
  meta: {
    requestId: string;
    timestamp: string;
  };
}

export interface RegisterClientRequest {
  name: string;
  email: string;
  dateOfBirth?: string | null;
  riskProfile?: string | null;
  initialCash?: number | null;
}

export interface SignInRequest {
  email: string;
}

export interface SessionResponse {
  clientId: number;
  sessionId: number;
  expiresAt: string;
  accessToken: string;
  tokenType: string;
}

export interface QuoteResponse {
  symbol: string;
  bid: number;
  ask: number;
  price: number;
  bidVolume: number;
  askVolume: number;
  capturedAt: string;
  market: string;
}

export interface PositionResponse {
  symbol: string;
  quantity: number;
}

export interface PortfolioSummaryResponse {
  clientId: number;
  accountId: number;
  cashBalance: number;
  currency: string;
  positions: PositionResponse[];
}

export interface SubmitOrderRequest {
  symbol: string;
  side: string;
  quantity: number;
  market: string;
  orderType: string;
  limitPrice?: number | null;
}

export interface OrderResponse {
  orderId: number;
  symbol: string;
  side: string;
  orderType: string;
  quantity: number;
  status: string;
  rejectionReason?: string | null;
  submittedAt: string;
}
