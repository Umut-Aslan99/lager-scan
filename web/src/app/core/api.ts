import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { ArticleSummary, ArticleView, BinView, BookingRequest, BookingResult, Dashboard, Labels, ScanResult, WorkspaceView } from './models';

/** Server said no for a business reason (HTTP 422): the message is meant for the user. */
export class RuleError extends Error {}

/** Thin, typed wrapper around the Spring Boot API. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  private get<T>(url: string) { return call(firstValueFrom(this.http.get<T>(url))); }

  dashboard() { return this.get<Dashboard>('/api/dashboard'); }
  workspace() { return this.get<WorkspaceView>('/api/workspace'); }
  scan(code: string) { return this.get<ScanResult>(`/api/scan?code=${encodeURIComponent(code)}`); }
  article(number: string) { return this.get<ArticleView>(`/api/articles/${encodeURIComponent(number)}`); }
  articles(q = '') { return this.get<ArticleSummary[]>(`/api/articles${q ? `?q=${encodeURIComponent(q)}` : ''}`); }
  bins() { return this.get<BinView[]>('/api/bins'); }
  labels() { return this.get<Labels>('/api/labels'); }
  reset() { return call(firstValueFrom(this.http.post<void>('/api/workspace/reset', null))); }
  book(req: BookingRequest) { return call(firstValueFrom(this.http.post<BookingResult>('/api/bookings', req))); }

  uploadPhoto(file: Blob, articleNumber: string) {
    const form = new FormData();
    form.append('file', file, 'foto.jpg');
    form.append('articleNumber', articleNumber);
    return call(firstValueFrom(this.http.post<{ id: string; url: string }>('/api/photos', form)));
  }
}

async function call<T>(p: Promise<T>): Promise<T> {
  try {
    return await p;
  } catch (e) {
    if (e instanceof HttpErrorResponse && e.status === 422) throw new RuleError(e.error?.detail ?? 'Nicht möglich.');
    throw e;
  }
}

export const isOffline = (e: unknown) => e instanceof HttpErrorResponse && e.status === 0;
