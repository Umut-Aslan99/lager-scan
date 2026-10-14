import { Component, ElementRef, OnDestroy, output, signal, viewChild } from '@angular/core';
import jsQR from 'jsqr';

interface Detector { detect(source: CanvasImageSource): Promise<{ rawValue: string }[]> }
type DetectorCtor = { new (o: { formats: string[] }): Detector; getSupportedFormats(): Promise<string[]> };

/**
 * Phone camera as barcode scanner. Uses the browser's native BarcodeDetector where available
 * (Chrome on Android: QR, EAN-13, Code 128) and falls back to jsQR, which reads QR codes everywhere (iOS too).
 */
@Component({
  selector: 'app-camera-scanner',
  template: `
    <div class="viewfinder">
      <video #video playsinline muted [hidden]="!active()"></video>
      @if (active()) {
        <span class="frame" aria-hidden="true"></span>
        <span class="hint">Etikett in den Rahmen halten · {{ engine() }}</span>
      } @else {
        <div style="display:grid;gap:10px;justify-items:center;padding:16px;text-align:center">
          @if (error()) { <span>{{ error() }}</span> }
          <button class="btn btn-primary btn-lg" type="button" (click)="start()" data-testid="camera-start">Kamera starten</button>
          <span style="font-size:.8rem">Oder Code unten eingeben bzw. mit dem Handscanner scannen.</span>
        </div>
      }
    </div>
    @if (active()) {
      <button class="btn btn-sm btn-ghost" type="button" (click)="stop()" style="justify-self:center">Kamera stoppen</button>
    }
  `,
  styles: [':host { display: grid; gap: 8px; }'],
})
export class CameraScanner implements OnDestroy {
  readonly scanned = output<string>();
  readonly active = signal(false);
  readonly error = signal('');
  readonly engine = signal('');
  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private stream?: MediaStream;
  private timer?: ReturnType<typeof setTimeout>;
  private detector?: Detector;
  private canvas = document.createElement('canvas');
  private last = { code: '', at: 0 };

  async start() {
    this.error.set('');
    if (!navigator.mediaDevices?.getUserMedia) {
      this.error.set('Dieser Browser erlaubt keinen Kamerazugriff (HTTPS nötig).');
      return;
    }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
    } catch {
      this.error.set('Kamera nicht verfügbar oder nicht erlaubt.');
      return;
    }
    const native = (window as unknown as { BarcodeDetector?: DetectorCtor }).BarcodeDetector;
    if (native) {
      const formats = (await native.getSupportedFormats()).filter((f) => ['qr_code', 'ean_13', 'code_128'].includes(f));
      if (formats.includes('qr_code')) this.detector = new native({ formats });
    }
    this.engine.set(this.detector ? 'Barcode-API des Browsers' : 'jsQR');
    const el = this.video().nativeElement;
    el.srcObject = this.stream;
    await el.play();
    this.active.set(true);
    this.loop();
  }

  stop() {
    clearTimeout(this.timer);
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = undefined;
    this.active.set(false);
  }

  ngOnDestroy() { this.stop(); }

  private loop() {
    this.timer = setTimeout(async () => {
      if (!this.active()) return;
      const code = await this.decode().catch(() => null);
      if (code && (code !== this.last.code || Date.now() - this.last.at > 2500)) {
        this.last = { code, at: Date.now() };
        navigator.vibrate?.(40);
        this.scanned.emit(code);
      }
      this.loop();
    }, 180);
  }

  private async decode(): Promise<string | null> {
    const el = this.video().nativeElement;
    if (el.readyState < 2) return null;
    if (this.detector) return (await this.detector.detect(el))[0]?.rawValue ?? null;
    const w = 480;
    const h = Math.round((el.videoHeight / el.videoWidth) * w) || 360;
    this.canvas.width = w;
    this.canvas.height = h;
    const ctx = this.canvas.getContext('2d', { willReadFrequently: true })!;
    ctx.drawImage(el, 0, 0, w, h);
    const img = ctx.getImageData(0, 0, w, h);
    return jsQR(img.data, w, h, { inversionAttempts: 'dontInvert' })?.data ?? null;
  }
}
