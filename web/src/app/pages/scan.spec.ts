import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Api } from '../core/api';
import { Outbox } from '../core/outbox';
import type { ArticleView, BinView, BookingRequest, ScanResult } from '../core/models';
import { ScanPage } from './scan';

const bins: BinView[] = [
  { code: 'A-01-01', zone: 'Regal A', contents: [] },
  { code: 'C-02-03', zone: 'Regal C', contents: [] },
  { code: 'WE-01', zone: 'Wareneingang', contents: [] },
];
const view: ArticleView = {
  article: { number: 'ET-1001', ean: '2000000010017', name: 'Rillenkugellager', group: 'Lager', unit: 'Stk', total: 5, minStock: 2, belowMin: false, bins: ['A-01-01'], photoUrl: null },
  stock: [{ articleNumber: 'ET-1001', articleName: 'Rillenkugellager', unit: 'Stk', binCode: 'A-01-01', quantity: 5 }],
  recent: [],
};

describe('ScanPage', () => {
  let scans: string[];
  let submitted: BookingRequest[];

  async function setup() {
    scans = [];
    submitted = [];
    const api = {
      bins: async () => bins,
      articles: async () => [view.article],
      scan: async (code: string): Promise<ScanResult> => { scans.push(code); return { kind: 'article', article: view, bin: null }; },
    };
    const outbox = {
      rejected: () => [],
      submit: async (r: BookingRequest) => { submitted.push(r); return { state: 'sent', booking: { type: r.type, typeLabel: 'Umlagerung' } }; },
    };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: Api, useValue: api }, { provide: Outbox, useValue: outbox }] });
    const fixture = TestBed.createComponent(ScanPage);
    await fixture.componentInstance.ngOnInit();
    return fixture.componentInstance;
  }

  it('fills the target bin from a scanned bin label while a transfer is open', async () => {
    const page = await setup();
    await page.lookup('ART:ET-1001');
    page.choose('TRANSFER');
    expect(page.fromBin()).toBe('A-01-01');

    await page.lookup('BIN:C-02-03');
    expect(page.toBin()).toBe('C-02-03');
    expect(scans).toEqual(['ART:ET-1001']); // the bin label did not navigate away

    await page.book();
    expect(submitted[0]).toMatchObject({ type: 'TRANSFER', articleNumber: 'ET-1001', fromBin: 'A-01-01', toBin: 'C-02-03', quantity: 1 });
  });

  it('suggests the receiving bin for goods receipt and sends counts without quantity', async () => {
    const page = await setup();
    await page.lookup('ET-1001');
    page.choose('RECEIPT');
    expect(page.toBin()).toBe('WE-01');

    page.choose('COUNT');
    expect(page.toBin()).toBe('A-01-01');
    page.counted.set(4);
    await page.book();
    expect(submitted[0]).toMatchObject({ type: 'COUNT', toBin: 'A-01-01', counted: 4, quantity: null, fromBin: null });
  });
});
