import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api, RuleError, isOffline } from '../core/api';
import { CatalogCache } from '../core/catalog-cache';
import { Outbox } from '../core/outbox';
import { qty, signed, time, uuid } from '../core/format';
import { TYPE_LABEL, type BinView, type BookingType, type ScanResult } from '../core/models';
import { CameraScanner } from '../ui/camera-scanner';

type Mode = BookingType | 'PHOTO';

/**
 * The phone (or hand scanner) view: scan an article or a bin, pick an action, book.
 * While a booking form is open, scanning a bin label fills the bin field instead of opening the bin.
 */
@Component({
  selector: 'app-scan',
  imports: [CameraScanner, RouterLink],
  templateUrl: './scan.html',
})
export class ScanPage implements OnInit {
  private readonly api = inject(Api);
  private readonly cache = inject(CatalogCache);
  private readonly route = inject(ActivatedRoute);
  readonly outbox = inject(Outbox);

  readonly result = signal<ScanResult | null>(null);
  readonly bins = signal<BinView[]>([]);
  readonly mode = signal<Mode | null>(null);
  readonly busy = signal(false);
  readonly toast = signal<{ kind: 'ok' | 'err'; text: string } | null>(null);
  readonly joined = signal<string | null>(null);
  readonly code = signal('');

  readonly fromBin = signal('');
  readonly toBin = signal('');
  readonly quantity = signal(1);
  readonly counted = signal<number | null>(null);
  readonly note = signal('');

  readonly article = computed(() => this.result()?.article ?? null);
  readonly labels = TYPE_LABEL;
  readonly qty = qty;
  readonly signed = signed;
  readonly time = time;
  readonly device = /Mobi|Android|iPhone/i.test(navigator.userAgent) ? 'Handy' : 'Browser';
  private toastTimer?: ReturnType<typeof setTimeout>;

  async ngOnInit() {
    this.joined.set(this.route.snapshot.queryParamMap.get('joined'));
    try {
      const [bins, articles] = await Promise.all([this.api.bins(), this.api.articles()]);
      this.bins.set(bins);
      this.cache.store(articles, bins);
    } catch { /* offline start: the cache still resolves scans */ }
    const code = this.route.snapshot.queryParamMap.get('code');
    if (code) await this.lookup(code);
  }

  /** Camera, hand scanner (types + Enter) and manual input all end up here. */
  async lookup(raw: string) {
    const code = raw.trim();
    if (!code) return;
    this.code.set('');
    const upper = code.toUpperCase();
    // A bin label while a booking form is open fills the bin field.
    const mode = this.mode();
    if (mode && mode !== 'PHOTO' && (upper.startsWith('BIN:') || this.bins().some((b) => b.code === upper))) {
      const bin = upper.replace(/^BIN:/, '');
      if (mode === 'ISSUE' || (mode === 'TRANSFER' && !this.fromBin())) this.fromBin.set(bin);
      else this.toBin.set(bin);
      this.show('ok', `Lagerplatz ${bin} übernommen`);
      return;
    }
    this.busy.set(true);
    try {
      this.result.set(await this.api.scan(code));
      this.mode.set(null);
    } catch (e) {
      const cached = isOffline(e) ? this.cache.resolve(code) : null;
      if (cached) { this.result.set(cached); this.mode.set(null); }
      else this.show('err', e instanceof RuleError ? e.message : 'Code nicht gefunden – und keine Verbindung zum Server.');
    } finally {
      this.busy.set(false);
    }
  }

  async openArticle(number: string, fromBin?: string) {
    await this.lookup(`ART:${number}`);
    if (fromBin) this.fromBin.set(fromBin);
  }

  choose(mode: Mode) {
    const a = this.article();
    if (!a) return;
    const withStock = a.stock.filter((s) => s.quantity > 0).map((s) => s.binCode);
    this.mode.set(mode);
    this.quantity.set(1);
    this.counted.set(null);
    this.note.set('');
    this.fromBin.set(mode === 'ISSUE' || mode === 'TRANSFER' ? (this.fromBin() && withStock.includes(this.fromBin()) ? this.fromBin() : withStock[0] ?? '') : '');
    this.toBin.set(mode === 'RECEIPT' ? 'WE-01' : mode === 'COUNT' ? withStock[0] ?? '' : '');
  }

  async book() {
    const a = this.article();
    const mode = this.mode();
    if (!a || !mode || mode === 'PHOTO') return;
    this.busy.set(true);
    const result = await this.outbox.submit({
      clientId: uuid(), type: mode, articleNumber: a.article.number,
      fromBin: mode === 'ISSUE' || mode === 'TRANSFER' ? this.fromBin() : null,
      toBin: mode === 'RECEIPT' || mode === 'TRANSFER' || mode === 'COUNT' ? this.toBin() : null,
      quantity: mode === 'COUNT' ? null : this.quantity(), counted: mode === 'COUNT' ? this.counted() : null,
      note: this.note() || null, device: this.device, bookedAt: new Date().toISOString(),
    });
    this.busy.set(false);
    if (result.state === 'rejected') { this.show('err', result.message); return; }
    if (result.state === 'queued') this.show('ok', `${TYPE_LABEL[mode]} offline gespeichert – wird gesendet, sobald Netz da ist.`);
    else {
      const b = result.booking;
      this.show('ok', b.type === 'COUNT' ? `Inventur gebucht · Differenz ${signed(b.difference)}` : `${b.typeLabel} gebucht`);
    }
    this.mode.set(null);
    await this.lookup(`ART:${a.article.number}`).catch(() => undefined);
  }

  async photo(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const a = this.article();
    if (!file || !a) return;
    this.busy.set(true);
    try {
      await this.api.uploadPhoto(await compress(file), a.article.number);
      this.show('ok', 'Foto gespeichert');
      this.mode.set(null);
      await this.lookup(`ART:${a.article.number}`);
    } catch (e) {
      this.show('err', e instanceof RuleError ? e.message : 'Foto konnte nicht hochgeladen werden (offline?).');
    } finally {
      this.busy.set(false);
    }
  }

  step(delta: number) { this.quantity.update((q) => Math.max(1, (Number(q) || 0) + delta)); }

  num(e: Event) { const v = (e.target as HTMLInputElement).value; return v === '' ? null : Number(v.replace(',', '.')); }
  val(e: Event) { return (e.target as HTMLInputElement).value; }

  private show(kind: 'ok' | 'err', text: string) {
    clearTimeout(this.toastTimer);
    this.toast.set({ kind, text });
    this.toastTimer = setTimeout(() => this.toast.set(null), 3500);
  }
}

/** Phone photos are 3–8 MB; 1280 px JPEG at 80 % is plenty for identifying a part. */
async function compress(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, 1280 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('jpeg'))), 'image/jpeg', 0.8));
}
