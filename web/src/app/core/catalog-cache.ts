import { Injectable } from '@angular/core';
import type { ArticleSummary, BinView, ScanResult } from './models';

const KEY = 'lagerscan.catalog';

/**
 * Last known articles and bins, kept on the device so a scan still resolves without network.
 * Stock figures may be outdated then – the scan page says so.
 */
@Injectable({ providedIn: 'root' })
export class CatalogCache {
  store(articles: ArticleSummary[], bins: BinView[]) {
    try { localStorage.setItem(KEY, JSON.stringify({ articles, bins, at: Date.now() })); } catch { /* storage full or blocked */ }
  }

  resolve(raw: string): ScanResult | null {
    let data: { articles: ArticleSummary[]; bins: BinView[] } | null = null;
    try { data = JSON.parse(localStorage.getItem(KEY) ?? 'null'); } catch { return null; }
    if (!data) return null;
    const code = raw.trim();
    const upper = code.toUpperCase();
    const binCode = upper.startsWith('BIN:') ? upper.slice(4) : upper;
    const articleCode = upper.startsWith('ART:') ? upper.slice(4) : upper;
    const bin = !upper.startsWith('ART:') ? data.bins.find((b) => b.code.toUpperCase() === binCode) : undefined;
    if (bin) return { kind: 'bin', article: null, bin, offline: true };
    const article = data.articles.find((a) => a.number.toUpperCase() === articleCode || a.ean === code);
    if (!article) return null;
    const stock = data.bins.flatMap((b) => b.contents.filter((c) => c.articleNumber === article.number));
    return { kind: 'article', article: { article, stock, recent: [] }, bin: null, offline: true };
  }
}
