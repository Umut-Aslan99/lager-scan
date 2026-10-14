import { Injectable, signal } from '@angular/core';
import type { BookingView } from './models';

/** Server-Sent Events from /api/events: bookings from any paired device arrive here instantly. */
@Injectable({ providedIn: 'root' })
export class Live {
  readonly connected = signal(false);
  readonly lastBooking = signal<BookingView | null>(null);
  readonly resets = signal(0);
  private source?: EventSource;

  connect() {
    if (this.source || typeof EventSource === 'undefined') return;
    this.source = new EventSource('/api/events');
    this.source.onopen = () => this.connected.set(true);
    this.source.onerror = () => this.connected.set(false); // EventSource reconnects by itself
    this.source.addEventListener('booking', (e) => this.lastBooking.set(JSON.parse((e as MessageEvent).data)));
    this.source.addEventListener('reset', () => this.resets.update((n) => n + 1));
  }
}
