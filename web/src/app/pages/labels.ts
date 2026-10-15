import { Component, OnInit, inject, signal } from '@angular/core';
import QRCode from 'qrcode';
import { Api } from '../core/api';

interface Label { id: string; title: string; sub: string; img: string; testId: string }

/** Printable QR labels for bins and articles – also scannable straight off this screen with the phone. */
@Component({
  selector: 'app-labels',
  template: `
    <div class="page-head">
      <div>
        <p class="eyebrow">Etiketten</p>
        <h1>Etiketten zum Scannen</h1>
        <p class="muted">Mit dem verbundenen Handy direkt vom Bildschirm scannen – oder auf A4 drucken und ans Regal kleben.</p>
      </div>
      <button class="btn btn-primary no-print" type="button" (click)="print()">Drucken</button>
    </div>
    <h2 class="section">Lagerplätze</h2>
    <div class="labels" data-tour="labels">
      @for (l of bins(); track l.id) {
        <figure class="label-card" [attr.data-testid]="l.testId"><img [src]="l.img" [alt]="'QR ' + l.title" /><strong>{{ l.title }}</strong><span>{{ l.sub }}</span></figure>
      }
    </div>
    <h2 class="section">Artikel</h2>
    <div class="labels">
      @for (l of articles(); track l.id) {
        <figure class="label-card" [attr.data-testid]="l.testId"><img [src]="l.img" [alt]="'QR ' + l.title" /><strong>{{ l.title }}</strong><span>{{ l.sub }}</span></figure>
      }
    </div>
  `,
})
export class LabelsPage implements OnInit {
  private readonly api = inject(Api);
  readonly bins = signal<Label[]>([]);
  readonly articles = signal<Label[]>([]);

  async ngOnInit() {
    const data = await this.api.labels();
    const qr = (payload: string) => QRCode.toDataURL(payload, { margin: 1, width: 256, errorCorrectionLevel: 'M' });
    this.bins.set(await Promise.all(data.bins.map(async (b) => ({ id: b.payload, title: b.code, sub: b.zone, img: await qr(b.payload), testId: `label-${b.code}` }))));
    this.articles.set(await Promise.all(data.articles.map(async (a) => ({ id: a.payload, title: a.number, sub: a.name, img: await qr(a.payload), testId: `label-${a.number}` }))));
  }

  print() { window.print(); }
}
