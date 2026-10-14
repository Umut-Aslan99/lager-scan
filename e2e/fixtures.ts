import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import QRCode from 'qrcode';

export const CAMERA_CODE = 'BIN:C-02-03';
export const CAMERA_VIDEO = resolve(import.meta.dirname, '.fixtures/qr-bin-label.y4m');

/**
 * Writes a short Y4M video (the raw format Chromium's fake camera reads) showing a QR code,
 * dark on white like a printed label.
 */
export function writeQrVideo(text: string, file: string, width = 640, height = 480, frames = 10) {
  const qr = QRCode.create(text, { errorCorrectionLevel: 'M' });
  const n = qr.modules.size;
  const scale = Math.floor((Math.min(width, height) * 0.7) / (n + 8));
  const size = (n + 8) * scale; // 4 modules quiet zone on each side
  const left = Math.floor((width - size) / 2);
  const top = Math.floor((height - size) / 2);

  const y = Buffer.alloc(width * height, 200); // light grey background, like a shelf
  for (let py = 0; py < size; py++) {
    for (let px = 0; px < size; px++) {
      const mx = Math.floor(px / scale) - 4;
      const my = Math.floor(py / scale) - 4;
      const dark = mx >= 0 && my >= 0 && mx < n && my < n && qr.modules.get(my, mx);
      y[(top + py) * width + left + px] = dark ? 16 : 235;
    }
  }
  const chroma = Buffer.alloc((width / 2) * (height / 2), 128);
  const frame = Buffer.concat([Buffer.from('FRAME\n'), y, chroma, chroma]);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, Buffer.concat([Buffer.from(`YUV4MPEG2 W${width} H${height} F10:1 Ip A1:1 C420jpeg\n`), ...Array(frames).fill(frame)]));
}
