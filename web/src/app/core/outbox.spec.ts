import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import 'fake-indexeddb/auto';
import { IDBFactory } from 'fake-indexeddb';
import { Api, RuleError } from './api';
import { Outbox } from './outbox';
import type { BookingRequest, BookingView } from './models';

const request = (clientId: string, bookedAt: string): BookingRequest =>
  ({ clientId, type: 'ISSUE', articleNumber: 'ET-1001', fromBin: 'A-01-01', quantity: 1, device: 'Test', bookedAt });

describe('Outbox', () => {
  let network: 'up' | 'down';
  let sent: string[];
  let outbox: Outbox;

  beforeEach(() => {
    globalThis.indexedDB = new IDBFactory();
    network = 'up';
    sent = [];
    const api = {
      book: async (r: BookingRequest) => {
        if (network === 'down') throw new HttpErrorResponse({ status: 0 });
        if (r.quantity! > 100) throw new RuleError('Auf A-01-01 liegen nur 5 Stk.');
        sent.push(r.clientId);
        return { duplicate: false, booking: { id: 'b-' + r.clientId, clientId: r.clientId } as BookingView };
      },
    };
    TestBed.configureTestingModule({ providers: [{ provide: Api, useValue: api }] });
    outbox = TestBed.inject(Outbox);
  });

  it('sends right away when online', async () => {
    const result = await outbox.submit(request('a', '2026-10-14T08:00:00Z'));
    expect(result.state).toBe('sent');
    expect(outbox.pending()).toBe(0);
  });

  it('keeps bookings offline and sends them later in booking order', async () => {
    network = 'down';
    expect((await outbox.submit(request('second', '2026-10-14T08:05:00Z'))).state).toBe('queued');
    expect((await outbox.submit(request('first', '2026-10-14T08:00:00Z'))).state).toBe('queued');
    expect(outbox.pending()).toBe(2);
    expect(outbox.online()).toBe(false);

    network = 'up';
    await outbox.flush();
    expect(sent).toEqual(['first', 'second']);
    expect(outbox.pending()).toBe(0);
    expect(outbox.online()).toBe(true);
  });

  it('drops a booking the server rejects and reports the reason', async () => {
    const result = await outbox.submit({ ...request('too-much', '2026-10-14T08:00:00Z'), quantity: 500 });
    expect(result).toEqual({ state: 'rejected', message: 'Auf A-01-01 liegen nur 5 Stk.' });
    expect(outbox.pending()).toBe(0);
    expect(outbox.rejected().length).toBe(0); // shown by the caller, not listed again
  });

  it('lists bookings that were rejected later, after waiting offline', async () => {
    network = 'down';
    await outbox.submit({ ...request('too-much', '2026-10-14T08:00:00Z'), quantity: 500 });
    network = 'up';
    await outbox.flush();
    expect(outbox.rejected().map((r) => r.message)).toEqual(['Auf A-01-01 liegen nur 5 Stk.']);
    outbox.dismiss('too-much');
    expect(outbox.rejected().length).toBe(0);
  });

  it('never sends a booking twice when flushes overlap', async () => {
    network = 'down';
    await outbox.submit(request('a', '2026-10-14T08:00:00Z'));
    network = 'up';
    await Promise.all([outbox.flush(), outbox.flush(), outbox.flush()]);
    expect(sent).toEqual(['a']);
  });
});
