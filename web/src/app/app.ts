import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Outbox } from './core/outbox';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
})
export class App {
  readonly outbox = inject(Outbox);
  readonly theme = signal(document.documentElement.dataset['theme'] ?? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'));

  toggleTheme() {
    const next = this.theme() === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset['theme'] = next;
    try { localStorage.setItem('theme', next); } catch { /* private mode */ }
    this.theme.set(next);
  }
}
