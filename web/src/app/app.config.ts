import { ApplicationConfig, inject, isDevMode, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { Api } from './core/api';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch()),
    // First visit: get the workspace cookie with ONE request before pages fire several in parallel –
    // otherwise each cookie-less request would create its own workspace. Offline start must not block.
    provideAppInitializer(() => inject(Api).workspace().then(() => undefined, () => undefined)),
    // App shell from the service worker: the scanner opens even without network.
    provideServiceWorker('ngsw-worker.js', { enabled: !isDevMode(), registrationStrategy: 'registerWhenStable:30000' }),
  ],
};
