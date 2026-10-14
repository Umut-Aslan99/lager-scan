import { CatalogCache } from './catalog-cache';
import type { ArticleSummary, BinView } from './models';

const article: ArticleSummary = {
  number: 'ET-1001', ean: '2000000010017', name: 'Rillenkugellager 6204-2RS', group: 'Lager', unit: 'Stk',
  total: 25, minStock: 10, belowMin: false, bins: ['A-01-01', 'WE-01'], photoUrl: null,
};
const bins: BinView[] = [
  { code: 'A-01-01', zone: 'Regal A', contents: [{ articleNumber: 'ET-1001', articleName: article.name, unit: 'Stk', binCode: 'A-01-01', quantity: 5 }] },
  { code: 'WE-01', zone: 'Wareneingang', contents: [{ articleNumber: 'ET-1001', articleName: article.name, unit: 'Stk', binCode: 'WE-01', quantity: 20 }] },
];

describe('CatalogCache', () => {
  const cache = new CatalogCache();
  beforeEach(() => { localStorage.clear(); cache.store([article], bins); });

  it('resolves article labels, EAN and plain article numbers offline', () => {
    for (const code of ['ART:ET-1001', '2000000010017', 'et-1001']) {
      const r = cache.resolve(code)!;
      expect(r.kind).toBe('article');
      expect(r.offline).toBe(true);
      expect(r.article!.stock.map((s) => s.binCode)).toEqual(['A-01-01', 'WE-01']);
    }
  });

  it('resolves bin labels', () => {
    expect(cache.resolve('BIN:A-01-01')!.bin!.code).toBe('A-01-01');
    expect(cache.resolve('we-01')!.kind).toBe('bin');
  });

  it('returns null for unknown codes or an empty cache', () => {
    expect(cache.resolve('ET-9999')).toBeNull();
    localStorage.clear();
    expect(cache.resolve('ET-1001')).toBeNull();
  });
});
