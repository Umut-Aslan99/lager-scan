import { CAMERA_CODE, CAMERA_VIDEO, writeQrVideo } from './fixtures';

export default function globalSetup() {
  writeQrVideo(CAMERA_CODE, CAMERA_VIDEO);
}
