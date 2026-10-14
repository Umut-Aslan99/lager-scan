import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { qty, signed, time } from '../core/format';
import type { ArticleView } from '../core/models';

@Component({
  selector: 'app-article',
  imports: [RouterLink],
  template: `
    <a class="btn btn-sm btn-ghost" routerLink="/">← Leitstand</a>
    @if (view(); as v) {
      <div class="page-head">
        <div>
          <p class="eyebrow">{{ v.article.group }} · EAN {{ v.article.ean }}</p>
          <h1>{{ v.article.name }}</h1>
          <p class="mono muted">{{ v.article.number }}</p>
        </div>
        <a class="btn" [routerLink]="['/scan']" [queryParams]="{ code: 'ART:' + v.article.number }">Im Scanner öffnen</a>
      </div>
      <div class="grid-2">
        <section class="card panel">
          <h3>Bestand: {{ qty(v.article.total) }} {{ v.article.unit }} @if (v.article.belowMin) { <span class="chip chip-critical">⚠ unter {{ qty(v.article.minStock) }}</span> }</h3>
          <ul class="list">@for (s of v.stock; track s.binCode) { <li><span class="mono grow">{{ s.binCode }}</span><b class="num">{{ qty(s.quantity) }} {{ s.unit }}</b></li> }</ul>
          @if (v.article.photoUrl) { <img [src]="v.article.photoUrl" alt="Foto" style="margin-top:12px;border-radius:10px;max-height:260px;object-fit:cover" /> }
        </section>
        <section class="card panel">
          <h3>Letzte Buchungen</h3>
          <ul class="list">
            @for (b of v.recent; track b.id) {
              <li><span class="type" [attr.data-t]="b.type">{{ b.typeLabel }}</span>
                <span class="grow"><span class="mono">{{ b.fromBin ?? '' }}{{ b.fromBin && b.toBin ? ' → ' : '' }}{{ b.toBin ?? '' }}</span><small>{{ time(b.bookedAt) }} · {{ b.device }}{{ b.note ? ' · ' + b.note : '' }}</small></span>
                <b class="num">{{ b.type === 'COUNT' ? signed(b.difference) : qty(b.quantity) }}</b></li>
            } @empty { <li class="muted">Noch keine Buchungen.</li> }
          </ul>
        </section>
      </div>
    }
  `,
})
export class ArticlePage implements OnInit {
  private readonly api = inject(Api);
  readonly number = input.required<string>();
  readonly view = signal<ArticleView | null>(null);
  readonly qty = qty;
  readonly signed = signed;
  readonly time = time;

  async ngOnInit() { this.view.set(await this.api.article(this.number())); }
}
