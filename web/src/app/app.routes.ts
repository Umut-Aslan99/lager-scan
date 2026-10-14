import { Routes } from '@angular/router';

// Lazy pages: the phone only loads the scanner (jsQR), the control station only the QR generator.
export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/control').then((m) => m.ControlPage), title: 'Leitstand · Lager-Scan' },
  { path: 'scan', loadComponent: () => import('./pages/scan').then((m) => m.ScanPage), title: 'Scanner · Lager-Scan' },
  { path: 'etiketten', loadComponent: () => import('./pages/labels').then((m) => m.LabelsPage), title: 'Etiketten · Lager-Scan' },
  { path: 'artikel/:number', loadComponent: () => import('./pages/article').then((m) => m.ArticlePage), title: 'Artikel · Lager-Scan' },
  { path: '**', redirectTo: '' },
];
