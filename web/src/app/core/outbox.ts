import { Injectable, inject, signal } from '@angular/core';
import { openDB, type IDBPDatabase } from 'idb';
import { Api, RuleError, isOffline } from './api';
import type { BookingRequest, BookingView } from './models';

export type SubmitResult = { state: 'sent'; booking: BookingView } | { state: 'queued' } | { state: 'rejected'; message: string };

/**
 * Offline-first booking: every booking is written to IndexedDB first, then sent. Without network it
 * waits there and is sent automatically later. The device-generated clientId makes resending safe.
 */
@Injectable({ providedIn: 'root' })
export class Outbox {
  private readonly api = inject(Api);
  private dbp?: Promise<IDBPDatabase>;
  /** Flushes run one after another, so a booking is never sent twice in parallel and order is kept. */
  private chain: Promise<unknown> = Promise.resolve();
  /** Bookings a caller of submit() is waiting for, and what the server answered for them. */
  private readonly waiting = new Set<string>();
  private readonly delivered = new Map<string, BookingView>();

  readonly online = signal(typeof navigator === 'undefined' ? true : navigator.onLine);
  readonly pending = signal(0);
  readonly rejected = signal<{ request: BookingRequest; message: string }[]>([]);
  readonly sent = signal<BookingView | null>(null);

  constructor() {
    if (typeof window === 'undefined') return;
    addEventListener('online', () => { this.online.set(true); void this.flush(); });
    addEventListener('offline', () => this.online.set(false));
    setInterval(() => void this.flush(), 15_000);
    void this.refreshCount().then(() => this.flush());
  }

  private db() {
    this.dbp ??= openDB('lagerscan', 1, { upgrade: (d) => d.createObjectStore('outbox', { keyPath: 'clientId' }) });
    return this.dbp;
  }

  async submit(request: BookingRequest): Promise<SubmitResult> {
    const id = request.clientId;
    this.waiting.add(id);
    try {
      await (await this.db()).put('outbox', request);
      await this.refreshCount();
      await this.flush();
      // Whichever flush sent it (this one or a background one), the result is recorded by clientId.
      const booking = this.delivered.get(id);
      if (booking) return { state: 'sent', booking };
      const rejection = this.rejected().find((r) => r.request.clientId === id);
      if (!rejection) return { state: 'queued' };
      this.dismiss(id); // the caller shows the reason right away; the list is for bookings rejected later
      return { state: 'rejected', message: rejection.message };
    } finally {
      this.waiting.delete(id);
      this.delivered.delete(id);
    }
  }

  /** Sends all queued bookings in booking order. */
  flush(): Promise<void> {
    const run = this.chain.then(() => this.send());
    this.chain = run.catch(() => undefined);
    return run;
  }

  private async send() {
    try {
      const db = await this.db();
      const queue = ((await db.getAll('outbox')) as BookingRequest[]).sort((a, b) => a.bookedAt.localeCompare(b.bookedAt));
      for (const request of queue) {
        try {
          const res = await this.api.book(request);
          await db.delete('outbox', request.clientId);
          this.online.set(true);
          this.sent.set(res.booking);
          if (this.waiting.has(request.clientId)) this.delivered.set(request.clientId, res.booking);
        } catch (e) {
          if (e instanceof RuleError) {
            await db.delete('outbox', request.clientId);
            this.rejected.update((r) => [...r, { request, message: e.message }]);
          } else {
            if (isOffline(e)) this.online.set(false);
            break; // keep the rest for later, in order
          }
        }
      }
    } finally {
      await this.refreshCount();
    }
  }

  dismiss(clientId: string) {
    this.rejected.update((r) => r.filter((x) => x.request.clientId !== clientId));
  }

  private async refreshCount() {
    this.pending.set(await (await this.db()).count('outbox'));
  }
}
