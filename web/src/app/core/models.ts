// Mirrors the Java records in dev.umutaslan.lagerscan.web.Dtos.

export type BookingType = 'RECEIPT' | 'ISSUE' | 'TRANSFER' | 'COUNT';

export interface StockLine { articleNumber: string; articleName: string; unit: string; binCode: string; quantity: number }
export interface ArticleSummary {
  number: string; ean: string; name: string; group: string; unit: string; total: number; minStock: number;
  belowMin: boolean; bins: string[]; photoUrl: string | null;
}
export interface BookingView {
  id: string; clientId: string; type: BookingType; typeLabel: string; articleNumber: string; articleName: string;
  fromBin: string | null; toBin: string | null; quantity: number | null; counted: number | null; difference: number | null;
  note: string | null; photoUrl: string | null; device: string; bookedAt: string;
}
export interface ArticleView { article: ArticleSummary; stock: StockLine[]; recent: BookingView[] }
export interface BinView { code: string; zone: string; contents: StockLine[] }
export interface ScanResult { kind: 'article' | 'bin'; article: ArticleView | null; bin: BinView | null; offline?: boolean }
export interface Dashboard {
  articles: number; bins: number; bookingsToday: number; receiptsToday: number; issuesToday: number; transfersToday: number;
  countsToday: number; countDifferencesToday: number; belowMin: ArticleSummary[]; receiving: StockLine[]; recent: BookingView[];
  connectedDevices: number;
}
export interface WorkspaceView { joinUrl: string; createdAt: string; expiresAfterIdleHours: number }
export interface Labels {
  articles: { number: string; name: string; ean: string; payload: string }[];
  bins: { code: string; zone: string; payload: string }[];
}
export interface BookingRequest {
  clientId: string; type: BookingType; articleNumber: string; fromBin?: string | null; toBin?: string | null;
  quantity?: number | null; counted?: number | null; note?: string | null; photoId?: string | null; device: string; bookedAt: string;
}
export interface BookingResult { duplicate: boolean; booking: BookingView }

export const TYPE_LABEL: Record<BookingType, string> = { RECEIPT: 'Wareneingang', ISSUE: 'Entnahme', TRANSFER: 'Umlagerung', COUNT: 'Inventur' };
