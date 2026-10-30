"""Edit genuine recorded UI takes into a narrated, captioned 1080p demo.

Requires Pillow and FFmpeg. Raw recordings and temporary renders stay ignored.
No generated application screens or simulated clinical data are inserted.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import textwrap
import wave

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / '.cache/video-demo'
OUT = ROOT / 'submission/video'
FONT = ROOT / 'apps/android/app/src/main/res/font'
FOREST = '#164C3F'
INK = '#172E29'
MUTED = '#596E66'
PINK = '#E9A4B7'


def run(args):
    result = subprocess.run(args, cwd=ROOT, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace')[-6000:])
    return result


def font(size, serif=False):
    return ImageFont.truetype(str(FONT / ('fraunces.ttf' if serif else 'dm_sans.ttf')), size)


def background(scene, mode, path):
    im = Image.new('RGB', (1920, 1080), 'white')
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((76, 66, 90, 105), 7, fill=PINK)
    d.text((112, 61), 'VITALINK', font=font(38), fill=FOREST)
    d.text((80, 130), 'HEALTH HACKATHON 3.0  /  PRODUCT DEMO', font=font(20), fill=MUTED)
    if mode == 'web':
        d.text((540, 53), scene['title'], font=font(34, True), fill=INK)
        d.text((540, 99), scene['subtitle'], font=font(22), fill=MUTED)
        d.rounded_rectangle((64, 929, 1856, 1044), 18, fill='#F1F5F1')
        d.multiline_text((80, 230), 'LIVE\nDOCTOR\nWORKSPACE', font=font(43, True), fill=FOREST, spacing=12)
        d.text((80, 455), 'One referral.', font=font(28), fill=INK)
        d.text((80, 498), 'One shared history.', font=font(28), fill=INK)
        d.line((80, 570, 350, 570), fill=PINK, width=4)
        d.multiline_text((80, 620), 'Facility-scoped access\nReal local API + database\nSynthetic demonstration', font=font(23), fill=MUTED, spacing=16)
    elif mode == 'closing':
        d.text((80, 275), 'Maternal care, connected.', font=font(83, True), fill=FOREST)
        d.text((83, 400), 'Presented by Winny Darlen', font=font(40), fill=INK)
        d.line((84, 494, 1836, 494), fill='#DCE6DE', width=2)
        for n, text in enumerate(('Offline assessment', 'Deterministic safety rules', 'A traceable clinical handoff')):
            d.ellipse((85, 550 + n * 61, 99, 564 + n * 61), fill=PINK)
            d.text((121, 534 + n * 61), text, font=font(32), fill=INK)
        d.text((82, 778), 'github.com/ATUMANYIRE/VITALINK', font=font(28), fill=FOREST)
        d.rounded_rectangle((64, 900, 1856, 1044), 18, fill='#F1F5F1')
    else:
        title = textwrap.fill(scene['title'], width=30)
        d.multiline_text((78, 266), title, font=font(68, True), fill=INK, spacing=12)
        d.text((82, 495), scene['subtitle'], font=font(28), fill=MUTED)
        d.line((82, 567, 1182, 567), fill='#DCE6DE', width=2)
        d.text((82, 607), 'ACTUAL NATIVE ANDROID RECORDING', font=font(21), fill=FOREST)
        d.text((82, 651), 'Presented by Winny Darlen', font=font(28), fill=INK)
        d.rounded_rectangle((64, 748, 1240, 1006), 22, fill='#F1F5F1')
        d.rounded_rectangle((1330, 10, 1830, 1070), 34, fill='#DDE8E0')
    d.text((80, 1037), f"{scene['id']:02} / 10   ·   RESEARCH PROTOTYPE   ·   SYNTHETIC RECORDS", font=font(18), fill=MUTED)
    im.save(path)


def timestamp(seconds, ass=False):
    ms = round(seconds * (100 if ass else 1000))
    base = 100 if ass else 1000
    s, fraction = divmod(ms, base)
    m, s = divmod(s, 60)
    h, m = divmod(m, 60)
    return f'{h}:{m:02}:{s:02}.{fraction:02}' if ass else f'{h:02}:{m:02}:{s:02},{fraction:03}'


def duration(ffmpeg, path):
    p = subprocess.run([ffmpeg, '-hide_banner', '-i', str(path)], capture_output=True)
    match = re.search(r'Duration: (\d+):(\d+):(\d+\.\d+)', p.stderr.decode(errors='replace'))
    if not match:
        raise RuntimeError(f'Cannot read actual media duration: {path.name}')
    h, m, s = map(float, match.groups())
    return h * 3600 + m * 60 + s


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', required=True)
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--reuse-renders', action='store_true', help='Reuse verified unchanged scene edits')
    args = parser.parse_args()
    ff = args.ffmpeg
    edit = CACHE / 'edit'
    edit.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    scenes = json.loads((ROOT / 'submission/DEMO_NARRATION.json').read_text(encoding='utf-8-sig'))
    scenario = json.loads((CACHE / 'scenario.json').read_text())
    if not args.prepare_only:
        required = ('airplaneModeVerified', 'pendingSyncObserved', 'restartPersistenceObserved',
                    'serverReceivedObserved', 'doctorAcknowledgmentObserved', 'mobileAcknowledgmentObserved')
        if not all(scenario.get(key) is True for key in required):
            raise RuntimeError('The actual complete referral workflow has not passed')
        if scenario['doctorAcknowledgedAt'] != scenario['acknowledgedAtObserved']:
            raise RuntimeError('Cross-platform acknowledgment timestamps differ')
        if scenario['doctorAcknowledgedBy'] != scenario['acknowledgedActorObserved']:
            raise RuntimeError('Cross-platform acknowledgment actors differ')
    # Measure speech. Preserve every spoken word; never force a too-short scene.
    clips = {}
    for scene in scenes:
        wavs = []
        for i, text in enumerate(scene['sentences'], 1):
            path = CACHE / 'audio' / f"{scene['id']:02}-{i:02}.wav"
            with wave.open(str(path)) as w:
                assert w.getnchannels() == 1 and w.getsampwidth() == 2 and w.getframerate() == 22050
                data = w.readframes(w.getnframes())
                wavs.append((text, data, len(data) / 44100))
        clips[scene['id']] = wavs
        scene['duration'] = sum(x[2] for x in wavs) + .8 + .16 * (len(wavs) - 1)
    spare = max(0, 180 - sum(s['duration'] for s in scenes))
    for scene in scenes:
        scene['duration'] += spare / len(scenes)
        scene['duration'] = round(scene['duration'] * 30 + .499) / 30
    all_pcm = bytearray()
    captions = []
    chapters = []
    clock = 0
    for scene in scenes:
        wavs = clips[scene['id']]
        length = scene['duration']
        local = .35
        pcm = bytearray(b'\0' * (2 * round(local * 22050)))
        local_caps = []
        for i, (text, data, seconds) in enumerate(wavs):
            captions.append((clock + local, clock + local + seconds, text))
            local_caps.append((local, local + seconds, text))
            pcm.extend(data)
            local += seconds
            if i != len(wavs) - 1:
                pcm.extend(b'\0' * (2 * round(.16 * 22050)))
                local += .16
        pcm.extend(b'\0' * max(0, 2 * round(length * 22050) - len(pcm)))
        all_pcm.extend(pcm)
        chapters.append({'id': scene['id'], 'startSeconds': round(clock, 3),
                         'durationSeconds': length, 'title': scene['title']})
        mode = 'web' if scene['id'] == 7 else 'closing' if scene['id'] == 10 else 'native'
        bg = edit / f"{scene['id']:02}-background.png"
        background(scene, mode, bg)
        # ASS uses libass with the repository's bundled, licensed DM Sans font.
        caption_pos = r'{\an7\pos(92,786)}' if mode == 'native' else r'{\an2\pos(960,1018)}'
        size, margin = (36, 650) if mode == 'native' else (31, 110)
        ass = '[Script Info]\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\nWrapStyle: 2\n'
        ass += '\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n'
        ass += f'Style: Default,DM Sans,{size},&H00292E17,&H00292E17,&H00F1F5F1,&H00F1F5F1,0,0,0,0,100,100,0,0,1,0,0,7,92,{margin},40,1\n'
        ass += '\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n'
        for start, end, text in local_caps:
            wrapped = r'\N'.join(textwrap.wrap(text, 55 if mode == 'native' else 100))
            ass += f'Dialogue: 0,{timestamp(start, True)},{timestamp(end, True)},Default,,0,0,0,,{caption_pos}{wrapped}\n'
        ass_path = edit / f"{scene['id']:02}-captions.ass"
        ass_path.write_text(ass, encoding='utf-8')
        if not args.prepare_only:
            inputs = ['-loop', '1', '-framerate', '30', '-i', str(bg)]
            video_filters = []
            used = []
            if scene['source']:
                raw = CACHE / 'raw' / scene['source']
                start, end = 0, duration(ff, raw)
                if scene.get('startMarker'):
                    takes = json.loads((CACHE / 'raw/mobile-recordings.json').read_text())
                    take = [t for t in takes if t['clip'] == scene['source']][-1]
                    base = take['startedEpochMs']
                    events = [e for e in scenario['events'] if e['epochMs'] >= base]
                    start = (next(e for e in events if e['name'] == scene['startMarker'])['epochMs'] - base) / 1000
                    end = (next(e for e in events if e['name'] == scene['endMarker'])['epochMs'] - base) / 1000
                elif scene['id'] == 7:
                    events = json.loads((CACHE / 'doctor-recording.json').read_text())['events']
                    start = next(e['seconds'] for e in events if e['name'] == 'Authenticated facility overview')
                    # Include the real confirmation click and server result before
                    # cutting back to the Android acknowledgment readback.
                    end = next(e['seconds'] for e in events if e['name'] == 'Server acknowledgment actor and timestamp verified') + 2
                elif scene['id'] in (5, 6, 8, 9):
                    takes = json.loads((CACHE / 'raw/mobile-recordings.json').read_text())
                    take = [t for t in takes if t['clip'] == scene['source']][-1]
                    base = take['startedEpochMs']
                    events = [e for e in scenario['events'] if e['epochMs'] >= base]
                    marker = {5: 'restart-verified', 6: 'connectivity-restored',
                              8: 'mobile-refresh', 9: 'privacy'}[scene['id']]
                    stamp = (next(e for e in events if e['name'] == marker)['epochMs'] - base) / 1000
                    start = max(0, stamp - (10 if scene['id'] in (5, 9) else 5))
                    last = {5: 'restart-verified', 6: 'server-received',
                            8: 'mobile-acknowledged', 9: 'privacy'}[scene['id']]
                    stop = (next(e for e in events if e['name'] == last)['epochMs'] - base) / 1000
                    end = min(end - 1, stop + 3.5)
                else:
                    # Exclude OS launch/runner transition, preserve genuine frames.
                    start = 2.5 if scene['id'] == 1 else min(4, end * .15)
                    end = max(start + 1, end - 1.5)
                speed = (end - start) / length
                inputs += ['-ss', str(start), '-t', str(end - start), '-i', str(raw)]
                scale = 'scale=1240:775:flags=lanczos' if mode == 'web' else 'scale=480:1040:flags=lanczos'
                # Android screenrecord is variable-frame-rate and can leave long
                # static gaps. Extend the last genuine frame, then cap the output
                # at the exact narration scene length so later captions stay aligned.
                video_filters += [f'[1:v]setpts=(PTS-STARTPTS)/{speed},{scale},fps=30,tpad=stop_mode=clone:stop_duration={length + 2}[ui]',
                                  f'[0:v][ui]overlay={600 if mode == "web" else 1340}:{145 if mode == "web" else 20}:shortest=1[base]']
                used.append({'file': scene['source'], 'inSeconds': round(start, 3), 'outSeconds': round(end, 3),
                             'playbackSpeed': round(speed, 4)})
            else:
                video_filters.append('[0:v]null[base]')
            rel = ass_path.relative_to(ROOT).as_posix()
            video_filters.append(f"[base]ass=filename='{rel}':fontsdir='apps/android/app/src/main/res/font'[final]")
            target = edit / f"{scene['id']:02}.mp4"
            print(f"Rendering scene {scene['id']:02} ({length:.2f}s)", flush=True)
            if not (args.reuse_renders and target.exists()):
                run([ff, '-y', '-hide_banner', '-loglevel', 'error', '-filter_complex_threads', '1', *inputs,
                     '-filter_complex', ';'.join(video_filters), '-map', '[final]', '-t', str(length),
                     '-an', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22', '-threads', '2',
                     '-pix_fmt', 'yuv420p', '-r', '30', str(target)])
            chapters[-1]['sourceEdits'] = used
            actual_length = duration(ff, target)
            if abs(actual_length - length) > .04:
                raise RuntimeError(f'Scene {scene["id"]} duration differs from its narration timeline')
        clock += length
    with wave.open(str(edit / 'narration.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(22050); w.writeframes(all_pcm)
    stem = 'VITALINK_Demo_Winny_Darlen'
    srt = '\n\n'.join(f'{i}\n{timestamp(a)} --> {timestamp(b)}\n{textwrap.fill(t, 80)}' for i, (a, b, t) in enumerate(captions, 1)) + '\n'
    (OUT / (stem + '.srt')).write_text(srt, encoding='utf-8')
    (OUT / (stem + '.vtt')).write_text('WEBVTT\n\n' + re.sub(r'(\d{2}:\d{2}:\d{2}),(\d{3})', r'\1.\2', srt), encoding='utf-8')
    if not args.prepare_only:
        listing = edit / 'concat.txt'
        listing.write_text(''.join(f"file '{i:02}.mp4'\n" for i in range(1, 11)), encoding='utf-8')
        target = OUT / (stem + '.mp4')
        run([ff, '-y', '-hide_banner', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', str(listing),
             '-i', str(edit / 'narration.wav'), '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac',
             '-b:a', '128k', '-ar', '48000', '-ac', '2', '-af', 'loudnorm=I=-16:TP=-1.5:LRA=11',
             '-movflags', '+faststart', '-metadata', 'title=VITALINK — Presented by Winny Darlen', str(target)])
        print('Verifying full media decode', flush=True)
        run([ff, '-hide_banner', '-v', 'error', '-i', str(target), '-f', 'null', '-'])
        manifest = {'presenter': 'Winny Darlen', 'language': 'en', 'voice': 'Microsoft Zira Desktop (synthesized)',
                    'resolution': '1920x1080', 'fps': 30, 'durationSeconds': duration(ff, target),
                    'sizeBytes': target.stat().st_size, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                    'editing': 'Actual UI takes trimmed and time-adjusted for narration; not a latency benchmark.',
                    'fullDecode': 'PASS', 'chapters': chapters}
        (OUT / 'production-manifest.json').write_text(json.dumps(manifest, indent=2))
        print(json.dumps({k: v for k, v in manifest.items() if k != 'chapters'}, indent=2), flush=True)
    else:
        print(f'Prepared {len(scenes)} scenes, {len(captions)} captions, {clock:.2f} seconds')


if __name__ == '__main__':
    main()
