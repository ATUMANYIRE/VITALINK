"""Host recorder for opt-in real native UI phases; dedicated synthetic emulator only."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
APP = 'org.vitalink.app.debug'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('setup', 'offline', 'restart', 'received', 'ack', 'privacy'))
    parser.add_argument('--adb', required=True)
    args = parser.parse_args()
    output = ROOT / '.cache/video-demo/raw'
    output.mkdir(parents=True, exist_ok=True)
    prefix = {'offline': '02-offline', 'restart': '05-restart', 'received': '06-server-received',
              'ack': '08-mobile-acknowledgment', 'privacy': '09-privacy'}.get(args.phase)

    def adb(*command, timeout=30, check=True):
        result = subprocess.run([args.adb, '-s', 'emulator-5556', *command],
                                capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError('ADB operation failed; private arguments omitted')
        return result

    if args.phase == 'offline':
        adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
        adb('shell', 'svc', 'wifi', 'disable')
        adb('shell', 'svc', 'data', 'disable')
        assert adb('shell', 'settings', 'get', 'global', 'airplane_mode_on').stdout.strip() == b'1'
    elif args.phase in ('setup', 'received', 'ack'):
        adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')
        adb('shell', 'svc', 'wifi', 'enable')
        adb('shell', 'svc', 'data', 'enable')
        time.sleep(3)
    adb('shell', 'am', 'force-stop', APP)
    command = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
               'org.vitalink.app.DemoRecordingTest', '-e', 'demoPhase', args.phase]
    if args.phase == 'setup':
        users = json.loads((ROOT/'services/api/.runtime/demo-credentials.json').read_text())['users']
        worker = next(u for u in users if u['role']=='HEALTH_WORKER' and u['facilityId']=='demo-facility')
        command += ['-e', 'workerUsername', worker['username'], '-e', 'workerPassword', worker['password']]
    command += [APP + '.test/androidx.test.runner.AndroidJUnitRunner']
    recording = None
    record_log = None
    started = time.time()
    try:
        if prefix:
            remote = '/sdcard/vitalink-' + prefix + '.mp4'
            record_log = (output / (prefix + '-screenrecord.log')).open('wb')
            recording = subprocess.Popen([args.adb, '-s', 'emulator-5556', 'shell', 'screenrecord',
                '--size', '720x1560', '--bit-rate', '2500000', '--time-limit', '180', remote],
                stdout=record_log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            time.sleep(1)
        result = adb(*command, timeout=300)
        log = result.stdout.decode(errors='replace').replace('\r', '').rstrip()+'\n'
        (output/(args.phase+'-instrumentation.txt')).write_text(log, encoding='utf-8')
        if 'OK (1 test)' not in log or 'FAILURES!!!' in log or 'INSTRUMENTATION_STATUS_CODE: -3' in log:
            raise RuntimeError('Native demo phase did not pass; inspect the local phase log')
        metadata = json.loads(adb('exec-out', 'run-as', APP, 'cat', 'files/demo-recording.json').stdout)
        # AndroidJUnitRunner does not consistently forward println to its stream.
        # Require structured evidence written only after the actual UI assertions.
        evidence_key = {'setup':'workerSignedInBeforeOffline', 'offline':'pendingSyncObserved',
                        'restart':'restartPersistenceObserved', 'received':'serverReceivedObserved',
                        'ack':'mobileAcknowledgmentObserved'}.get(args.phase)
        if evidence_key and metadata.get(evidence_key) is not True:
            raise RuntimeError('Required native phase evidence is absent')
        if args.phase == 'privacy' and not any(e.get('name')=='privacy' for e in metadata.get('events', [])):
            raise RuntimeError('Privacy screen evidence is absent')
        path = ROOT/'.cache/video-demo/scenario.json'
        prior = json.loads(path.read_text()) if path.exists() else {}
        if metadata.get('referralId') and prior.get('referralId') not in (None, metadata['referralId']):
            prior = {}
        prior.update(metadata)
        path.write_text(json.dumps(prior, indent=2))
        print('Verified actual native UI phase:', args.phase, flush=True)
    except subprocess.TimeoutExpired:
        raise RuntimeError('Native recording phase timed out; private invocation omitted') from None
    finally:
        if recording:
            pids = adb('shell', 'pidof', 'screenrecord', check=False).stdout.decode().split()
            for pid in pids:
                if pid.isdigit(): adb('shell', 'kill', '-2', pid)
            recording.wait(timeout=20)
            record_log.close()
            adb('pull', remote, str(output/(prefix+'.mp4')), timeout=90)
            info = output/'mobile-recordings.json'
            entries = json.loads(info.read_text()) if info.exists() else []
            entries.append({'clip':prefix+'.mp4','phase':args.phase,'startedEpochMs':int(started*1000),
                            'elapsedSeconds':round(time.time()-started,2),
                            'source':'Actual Android screenrecord with real native UI controls'})
            info.write_text(json.dumps(entries,indent=2))
            print('Saved actual mobile recording:',prefix,flush=True)


if __name__ == '__main__':
    try: main()
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
