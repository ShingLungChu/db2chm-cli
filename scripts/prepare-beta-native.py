"""Prepare complete editable CHM source/notices around an unchanged accepted binary."""
import pathlib,sys,json,hashlib,tarfile,gzip,shutil,re

source,out,platform=pathlib.Path(sys.argv[1]).resolve(),pathlib.Path(sys.argv[2]).resolve(),sys.argv[3]
repo=pathlib.Path(__file__).resolve().parents[1]
if platform not in ['win32-x64','linux-x64','darwin-x64','darwin-arm64']:raise RuntimeError('Unknown platform')
if out.exists():raise RuntimeError('Use a fresh native output directory')
sha=lambda b:hashlib.sha256(b).hexdigest()
manifest=json.loads((source/'manifest.json').read_text())
assert manifest['platform']==platform and manifest['sourceCommit']=='de69d52048cf52c72ebfeef61617c04c9bc2005c'
for item in manifest['files']:
    assert pathlib.PurePosixPath(item['path']).name==item['path'] and '\\' not in item['path']
    b=(source/item['path']).read_bytes();assert len(b)==item['bytes'] and sha(b)==item['sha256']
provenance=json.loads((source/'build-provenance.json').read_text())
assert len(provenance['compiledUtilitySources'])==15
out.mkdir(parents=True);stage=out/'source-stage';stage.mkdir()
with tarfile.open(source/'chm-source.tar.gz') as archive:
    entries=[]
    for member in archive.getmembers():
        name=pathlib.PurePosixPath(member.name)
        assert not name.is_absolute() and '..' not in name.parts and '\\' not in member.name and ':' not in member.name
        assert member.isfile() or member.isdir()
        target=(stage/pathlib.Path(*name.parts)).resolve();assert target.is_relative_to(stage)
        entries.append((member,target))
    for member,target in entries:
        if member.isdir():target.mkdir(parents=True,exist_ok=True)
        else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.extractfile(member).read())
for item in provenance['compiledUtilitySources']:assert sha((stage/item['path']).read_bytes())==item['sha256']
original={item['path']:item['sha256'] for item in provenance['compiledUtilitySources']}
modified=set()
for patch in ['chmcmd-fpc322.patch','chmcmd-encoding.patch']:
    modified.update(re.findall(r'^\+\+\+ b/(packages/chm/src/[A-Za-z0-9_.-]+)$',(source/patch).read_text('utf8'),re.M))
assert len(modified)>=6
notice=b'{ db2chm modifications, 2026-10-27: UTF-8/CP936 navigation and search; Unicode Windows arguments. See included patches. }\n'
changes=[]
for name in sorted(modified):
    p=stage/name;before=p.read_bytes();p.write_bytes(notice+before)
    assert p.read_bytes()[len(notice):]==before
    changes.append({'path':name,'compiledSourceSha256':sha(before),'releasedSourceSha256':sha(p.read_bytes()),'change':'header-only dated Pascal comment','prefixBytes':len(notice)})
for item in manifest['files']:
    if item['path'] not in ['chm-source.tar.gz','SOURCE.txt']:shutil.copyfile(source/item['path'],out/item['path'])
for p in (repo/'release/native-rebuild').iterdir():shutil.copyfile(p,stage/p.name)
for name in ['chmcmd-fpc322.patch','chmcmd-encoding.patch','build-provenance.json']:
    shutil.copyfile(source/name,stage/name)
for name in ['FPC-LICENSE.txt','RTL-COPYING.FPC','RTL-COPYING.txt']:
    shutil.copyfile(source/name,stage/name)
source_map={'baseCommit':manifest['sourceCommit'],'compiledUtilitySources':original,'dateNoticeChanges':changes,'executableCodeUnchanged':True,'sourceAlreadyPatched':True}
(stage/'source-manifest.json').write_text(json.dumps(source_map,indent=2)+'\n')
parser=next((stage/'packages/chm/src').glob('[fF]ast[hH][tT][mM][lL][pP]arser.pas'))
header=parser.read_bytes().split(b'{$IFDEF FPC}')[0]
assert b'This code may be used and modified by anyone' in header
(out/'FastHTMLParser-LICENSE.txt').write_bytes(header)
links={'fpcLicenseFaq':'https://www.freepascal.org/faq.html#general-license','unmodifiedRuntimeLinkingException':'RTL-COPYING.FPC','compiler':provenance['compiler'],'exactRuntimeSourceReconstruction':'not-claimed','linuxDsc':'https://archive.ubuntu.com/ubuntu/pool/universe/f/fpc/fpc_3.2.2+dfsg-32.dsc','homebrewSource':'https://downloads.sourceforge.net/project/freepascal/Source/3.2.2/fpc-3.2.2.source.tar.gz','windowsCompilerSource':'https://github.com/fpc/FPCSource/tree/814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7'}
for dest in [out,stage]:(dest/'runtime-source-links.json').write_text(json.dumps(links,indent=2)+'\n')
def normalize(info):
    info.uid=info.gid=0;info.uname=info.gname='';info.mtime=0;info.pax_headers={};return info
with open(out/'chm-source.tar.gz','wb') as file:
    with gzip.GzipFile(fileobj=file,mode='wb',filename='',mtime=0) as gz:
        with tarfile.open(fileobj=gz,mode='w',format=tarfile.PAX_FORMAT) as tar:
            for name in sorted(stage.iterdir()):tar.add(name,arcname=name.name,filter=normalize)
license_notice=('Standalone chmcmd utility: LGPL terms, including LGPL-2.1-only LZX portions; '
                'FastHTMLParser has its separately retained permissive copyright/header. '
                'Unmodified FPC RTL/packages use the included independent-module linking exception. '
                'Java/Node UNLICENSED metadata does not restrict third-party source modification, debugging or relinking rights. '
                'Complete utility source, dated changes, licenses, patches, build scripts and runtime source links accompany the binary.')
(out/'SOURCE.txt').write_text(license_notice+'\nBase utility commit: '+manifest['sourceCommit']+'\nSource already patched; extract chm-source.tar.gz and read REBUILD.md.\n')
binary='chmcmd.exe' if platform=='win32-x64' else 'chmcmd'
assert sha((source/binary).read_bytes())==sha((out/binary).read_bytes())
review={'schemaVersion':1,'status':'beta-distribution-materials-checked','sourceReview':'docs/native-beta-distribution-review.md','binarySha256':sha((out/binary).read_bytes()),'originalArchiveSha256':sha((source/'chm-source.tar.gz').read_bytes()),'releasedArchiveSha256':sha((out/'chm-source.tar.gz').read_bytes()),'compiledUtilitySourcesChecked':15,'dateNoticeChanges':changes,'sourceAlreadyPatched':True,'runtimeSourceIdentity':'not-claimed; unmodified runtime linking exception','utilityLicenses':['LGPL-2.1-only','LicenseRef-FastHTMLParser'],'runtimeLicense':'LGPL-2.0-or-later WITH FPC-exception-2.0'}
(out/'native-license-review.json').write_text(json.dumps(review,indent=2)+'\n')
manifest.update({'redistributionApproved':True,'distributionChannel':'beta','licenseNotice':license_notice,'sourceDistributionReview':review['status']})
manifest['files']=[{'path':p.name,'sha256':sha(p.read_bytes()),'bytes':p.stat().st_size} for p in sorted(out.iterdir()) if p.is_file() and p.name!='manifest.json']
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'platform':platform,'sourceFilesVerified':15,'datedModifiedFiles':len(changes),'binaryUnchanged':True,'sourceArchiveBytes':(out/'chm-source.tar.gz').stat().st_size,'distributionChannel':'beta'}))
