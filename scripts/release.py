"""Build release metadata from independently verified APKs, or prepare the next version."""
import argparse, hashlib, json, os, pathlib, re, shutil, subprocess

ROOT=pathlib.Path(__file__).resolve().parents[1]
def version():
    return dict(line.split('=',1) for line in (ROOT/'version.properties').read_text().splitlines() if '=' in line)

def package(args):
    values=version(); code=int(values['versionCode']); name=values['versionName']
    assert re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+',name) and code>0
    sdk=pathlib.Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or pathlib.Path(os.environ['LOCALAPPDATA'])/'Android'/'Sdk')
    build_tools=sdk/'build-tools'/'36.0.0'
    signer=build_tools/('apksigner.bat' if os.name=='nt' else 'apksigner')
    aapt=build_tools/('aapt2.exe' if os.name=='nt' else 'aapt2')
    output=ROOT/'dist';output.mkdir(exist_ok=True)
    assets=[]
    for variant,filename in [('release','fitt.apk'),('legacy','fitt-legacy.apk')]:
        source=ROOT/'app'/'build'/'outputs'/'apk'/variant/f'app-{variant}.apk'
        certificate=subprocess.run([str(signer),'verify','--print-certs',str(source)],check=True,capture_output=True,text=True).stdout
        digest=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})',certificate).group(1)
        info=subprocess.run([str(aapt),'dump','badging',str(source)],check=True,capture_output=True,text=True).stdout
        assert f"name='io.github.nottarun7.fitt'" in info and f"versionCode='{code}'" in info and f"versionName='{name}'" in info
        assert 'application-debuggable' not in info, 'Public APK must not be debuggable'
        shutil.copyfile(source,output/filename)
        assets.append(dict(file=filename,sha256=hashlib.sha256(source.read_bytes()).hexdigest(),signerSha256=digest))
    assert len({asset['signerSha256'] for asset in assets})==2
    manifest=dict(schemaVersion=1,packageName='io.github.nottarun7.fitt',versionCode=code,versionName=name,minSdk=26,tag='v'+name,
        notes=args.notes,assets=assets)
    (output/'update.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    checksum_files=['fitt.apk','fitt-legacy.apk','update.json']
    (output/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256((output/f).read_bytes()).hexdigest()+'  '+f+'\n' for f in checksum_files),encoding='ascii')
    print('Verified and packaged both signed APKs, update.json and SHA256SUMS.txt in dist/')

def prepare(args):
    assert re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+',args.version), 'Use major.minor.patch'
    before=version()
    assert tuple(map(int,args.version.split('.')))>tuple(map(int,before['versionName'].split('.'))), 'Choose a newer version'
    dirty=subprocess.run(['git','status','--porcelain'],cwd=ROOT,check=True,capture_output=True,text=True).stdout
    assert not dirty.strip(), 'Commit current changes before preparing a release'
    (ROOT/'version.properties').write_text(f"versionCode={int(before['versionCode'])+1}\nversionName={args.version}\n",encoding='ascii')
    print(f'Prepared version {args.version}. Review and commit version.properties, then run:')
    print(f'git tag v{args.version}')
    print(f'git push origin main v{args.version}')
    print('The tag runs checks and publishes the signed APKs automatically.')

if __name__=='__main__':
    parser=argparse.ArgumentParser();commands=parser.add_subparsers(dest='command',required=True)
    packaged=commands.add_parser('package');packaged.add_argument('--notes',default='Improvements and fixes. See the GitHub release notes.')
    prepared=commands.add_parser('prepare');prepared.add_argument('version')
    args=parser.parse_args();(package if args.command=='package' else prepare)(args)
