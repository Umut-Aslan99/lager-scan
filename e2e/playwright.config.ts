import { defineConfig, devices } from '@playwright/test';
import { CAMERA_VIDEO } from './fixtures';

// Runs inside the app container's network namespace (ops.sh), so the app is at localhost:8080.
export default defineConfig({
  testDir: '.',
  timeout: 45_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  globalSetup: './global-setup.ts',
  use: { baseURL: process.env.BASE_URL ?? 'http://localhost:8080', trace: 'retain-on-failure', locale: 'de-DE', timezoneId: 'Europe/Berlin' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] }, testIgnore: /camera\.spec/ },
    { name: 'phone', use: { ...devices['Pixel 7'] }, testMatch: /tour\.spec/ },
    {
      // Chromium's fake camera plays a generated video of a QR bin label (see global-setup.ts).
      name: 'camera',
      testMatch: /camera\.spec/,
      use: {
        ...devices['Pixel 7'],
        permissions: ['camera'],
        launchOptions: { args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', `--use-file-for-fake-video-capture=${CAMERA_VIDEO}`] },
      },
    },
  ],
});
