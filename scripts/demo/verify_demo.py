"""Export non-secret evidence from the actual local demo recording run."""
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / '.cache/video-demo'
OUT = ROOT / 'evidence/demo'


def main():
    scenario = json.loads((CACHE / 'scenario.json').read_text())
    required = ('workerSignedInBeforeOffline', 'airplaneModeVerified', 'pendingSyncObserved',
                'restartPersistenceObserved', 'serverReceivedObserved',
                'doctorAcknowledgmentObserved', 'mobileAcknowledgmentObserved')
    assert all(scenario.get(key) is True for key in required)
    assert scenario['doctorAcknowledgedAt'] == scenario['acknowledgedAtObserved']
    assert scenario['doctorAcknowledgedBy'] == scenario['acknowledgedActorObserved']
    browser = json.loads((CACHE / 'doctor-recording.json').read_text())
    assert browser['status'] == 'PASS' and browser['referralId'] == scenario['referralId']
    OUT.mkdir(parents=True, exist_ok=True)
    tests = []
    for phase in ('setup', 'offline', 'restart', 'received', 'ack', 'privacy'):
        source = CACHE / 'raw' / f'{phase}-instrumentation.txt'
        log = source.read_text()
        assert 'OK (1 test)' in log and 'FAILURES!!!' not in log
        assert 'INSTRUMENTATION_STATUS_CODE: -3' not in log
        target = OUT / source.name
        shutil.copyfile(source, target)
        tests.append({'phase': phase, 'tests': 1, 'failures': 0,
                      'elapsedSeconds': float(re.search(r'Time: ([\d.]+)', log).group(1)),
                      'evidence': target.name})
    video = ROOT / 'submission/video/VITALINK_Demo_Winny_Darlen.mp4'
    manifest = json.loads((video.parent / 'production-manifest.json').read_text())
    assert manifest['fullDecode'] == 'PASS'
    assert hashlib.sha256(video.read_bytes()).hexdigest() == manifest['sha256']
    assert video.stat().st_size < 100_000_000
    result = {
        'recordingDateLocal': '2026-10-30', 'presenter': 'Winny Darlen',
        'status': 'PASS', 'scope': 'One real local synthetic Android/API/PostgreSQL/browser referral lifecycle',
        'referralId': scenario['referralId'], 'patientId': scenario['patientId'],
        'assertions': {k: scenario[k] for k in required},
        'acknowledgedBy': scenario['doctorAcknowledgedBy'],
        'acknowledgedAt': scenario['doctorAcknowledgedAt'],
        'nativePhases': tests, 'nativePhasesPassed': 6,
        'browserJourneysPassed': 1, 'browserEvents': browser['events'],
        'android': {'api': 30, 'resolution': '720x1560', 'densityDpi': 293,
                    'avd': 'Vitalink_UI_API30', 'host': 'Windows / WHPX',
                    'appSha256': '3129d428ee22e97975b5ed1992c10ff2f859abff323cd67866b9d0a95e392e6f'},
        'services': {'api': 'http://127.0.0.1:8087', 'web': 'http://localhost:3017',
                     'database': 'PostgreSQL 18, loopback 55432'},
        'video': manifest,
        'limitations': [
            'Local synthetic demonstration; no clinical validation or patient outcome evidence.',
            'Edited and time-adjusted UI footage; elapsed video time is not a performance benchmark.',
            'One setup phase and five subsequent native phases; separate from the historical 70 JVM / 20 device test run.',
            'An initial recording failed on a test dialog-scroll helper and had a System UI ANR. The helper was fixed, System UI restarted, and the affected take was discarded.',
            'No physical-device, public-deployment, Docker-engine or clinical-model validation claim.'
        ]
    }
    (OUT / '20261030-lifecycle.json').write_text(json.dumps(result, indent=2))
    print('Demo evidence: 6 native phases + 1 real browser journey PASS; same UUID and acknowledgment verified.')


if __name__ == '__main__':
    main()
