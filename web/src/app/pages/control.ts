import { Component, OnInit, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import QRCode from 'qrcode';
import { Api } from '../core/api';
import { Live } from '../core/live';
import { qty, signed, time } from '../core/format';
import type { ArticleSummary, Dashboard } from '../core/models';

/**
 * The control station (Leitstand) on the laptop: pairing QR code for the phone, today's figures,
 * goods waiting in receiving, articles below minimum stock and a live feed of every booking.
 */
@Component({
  selector: 'app-control',
  imports: [RouterLink],
  templateUrl: './control.html',
})
export class ControlPage implements OnInit {
  private readonly api = inject(Api);
  readonly live = inject(Live);
  readonly dash = signal<Dashboard | null>(null);
  readonly qr = signal('');
  readonly joinUrl = signal('');
  readonly articles = signal<ArticleSummary[]>([]);
  readonly query = signal('');
  readonly fresh = signal<string | null>(null);
  readonly qty = qty;
  readonly signed = signed;
  readonly time = time;
  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    // Every booking from any paired device refreshes the station and highlights the new line.
    effect(() => {
      const b = this.live.lastBooking();
      if (!b) return;
      untracked(() => { this.fresh.set(b.id); void this.refresh(); });
    });
    effect(() => { if (this.live.resets() > 0) untracked(() => void this.refresh()); });
  }

  async ngOnInit() {
    this.live.connect();
    const ws = await this.api.workspace();
    this.joinUrl.set(ws.joinUrl);
    this.qr.set(await QRCode.toDataURL(ws.joinUrl, { margin: 1, width: 296, color: { dark: '#111214', light: '#ffffff' } }));
    await this.refresh();
  }

  async refresh() {
    const [d, a] = await Promise.all([this.api.dashboard(), this.api.articles(this.query())]);
    this.dash.set(d);
    this.articles.set(a);
  }

  search(value: string) {
    this.query.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(async () => this.articles.set(await this.api.articles(value)), 250);
  }

  async reset() {
    if (!confirm('Lager auf die Demo-Daten zurücksetzen?')) return;
    await this.api.reset();
    await this.refresh();
  }

  val(e: Event) { return (e.target as HTMLInputElement).value; }
}
