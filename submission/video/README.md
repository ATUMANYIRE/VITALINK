# VITALINK demonstration — Winny Darlen

[Watch/download the narrated MP4](VITALINK_Demo_Winny_Darlen.mp4) · [English SRT captions](VITALINK_Demo_Winny_Darlen.srt) · [English WebVTT captions](VITALINK_Demo_Winny_Darlen.vtt)

**Presented by Winny Darlen for Health Hackathon 3.0.** English narration uses the local Microsoft Zira Desktop synthesized voice. It is not a recording or imitation of Winny Darlen's own voice. Captions are burned into the video and supplied separately for the video host's caption track.

The MP4 uses 1920×1080, 30 fps, H.264/yuv420p video, AAC stereo audio at 48 kHz and fast-start metadata. Its exact duration, size, SHA-256, chapter timings and source edits are recorded in [production-manifest.json](production-manifest.json). The intended runtime is approximately three minutes.

## What the recording demonstrates

Actual native Android and browser footage follows one synthetic case through:

1. Welcome and offline maternal assessment with airplane mode enabled.
2. BP 160/90 mmHg, severe headache and immediate on-device escalation messaging.
3. An immutable locally persisted assessment, pending referral and offline handoff summary.
4. A real app process restart with the committed referral still present.
5. Network restoration and confirmed backend receipt, explicitly separate from acknowledgment.
6. The live doctor overview, inbox, urgent queue, stored observations and rule explanations.
7. An explicit clinician acknowledgment and its actor/timestamp returning to Android.
8. Privacy controls, engineering evidence and the rules-only research limitation.

The native app, real Java API, PostgreSQL and actual Next.js workspace share referral `d7c049eb-c5b4-458a-8c0e-9323bde84376`. [Recording verification](../../evidence/demo/20261030-lifecycle.json) documents six opt-in native UI phases and one browser journey. These recording checks are separate from the earlier 70 JVM / 20 Android device test release evidence.

Footage is trimmed and adjusted in speed for narration. OS launch transitions and failed takes are excluded; the edit is not a latency benchmark or an uninterrupted real-time recording. UI screens are genuine captures, with no replacement screen mockups. Credentials were entered before offline recording; the published browser segment begins after sign-in. No real patient information is used.

## Upload to Vimeo

Upload `VITALINK_Demo_Winny_Darlen.mp4` using your own Vimeo account. Use [VIMEO_DESCRIPTION.md](VIMEO_DESCRIPTION.md) for a ready-to-paste title and description. Add the `.srt` or `.vtt` as an English caption track if available in the upload interface. Use [thumbnail.png](thumbnail.png) as the cover. Preview the uploaded video and captions before copying its actual share URL into Devpost.

No upload, public deployment or competition submission was performed by this recording workflow.

## Reproduce the edit

Source tooling is in [scripts/demo](../../scripts/demo/). Raw takes, credentials, audio intermediates and browser profiles stay in ignored `.cache/` or `.runtime/` directories. To produce new genuine takes, start the component services, install the app and test APK on the dedicated emulator, sign in with generated local fixtures, and run the native phases in order: `setup`, `offline`, `restart`, `received`. Then run `node scripts/demo/record_doctor.mjs`, followed by native `ack` and `privacy` phases.

Each native phase uses `python scripts/demo/record_mobile.py <phase> --adb <absolute-adb-path>`. The opt-in `DemoRecordingTest` interacts with actual UI controls and asserts stored state; it does not inject sessions or write clinical records directly. The dedicated recording scripts target emulator port 5556 and local services 8087/3017; inspect these settings before using another environment.

Generate narration with `scripts/demo/synthesize.ps1`. With Pillow and FFmpeg available, run `python scripts/demo/render_demo.py --ffmpeg <absolute-ffmpeg-path>`, then `python scripts/demo/verify_demo.py`. The renderer requires completed matching acknowledgment evidence and performs a full media decode. Inspect the finished video visually and audibly before publishing a new take. Bundled app fonts retain their existing license notices.

**Clinical boundary:** a research/hackathon prototype, not a validated medical device. No diagnosis, treatment recommendation, predictive accuracy or patient outcome is claimed. Urgent escalation must follow local clinical procedures immediately, regardless of connectivity or any future model score.
