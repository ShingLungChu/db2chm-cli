'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),config=require('../release/config.json'),collect=require('../scripts/collect-validation.cjs');
function fixture(){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'db2chm-review-'));return {dir,out:path.join(dir,'output'),write:(name,value)=>fs.writeFileSync(path.join(dir,name),JSON.stringify(value))};}
test('review collection rejects unsuccessful or foreign source runs before writing a bundle',()=>{
 const f=fixture();
 f.write('run.json',{conclusion:'failure',head_repository:{full_name:'ShingLungChu/db2chm-cli'}});
 assert.throws(()=>collect(f.dir,f.out));assert(!fs.existsSync(f.out));
 f.write('run.json',{conclusion:'success',head_repository:{full_name:'other/repository'}});
 assert.throws(()=>collect(f.dir,f.out));assert(!fs.existsSync(f.out));
});
test('review collection rejects a damaged CI archive before reading its payload or creating output',()=>{
 const f=fixture(),revision='1'.repeat(40);
 f.write('run.json',{conclusion:'success',head_sha:revision,head_repository:{full_name:'ShingLungChu/db2chm-cli'}});
 const names=['shared-java-runtime',...config.platforms.flatMap(p=>['private-native-npm-'+p.id,'native-'+p.id])];
 f.write('artifacts.json',{artifacts:names.map((name,id)=>({name,id,expired:false,digest:'sha256:'+'0'.repeat(64),workflow_run:{head_sha:revision}}))});
 fs.writeFileSync(path.join(f.dir,'shared-java-runtime.zip'),'damaged archive');
 assert.throws(()=>collect(f.dir,f.out),/Artifact archive digest mismatch/);assert(!fs.existsSync(f.out));
});
test('Windows tarball report paths resolve on the POSIX collection host',()=>{
 const artifactName=require('../scripts/artifact-name.cjs'),previous=path.basename;
 try{
  path.basename=path.posix.basename;
  assert.equal(artifactName('D:\\a\\db2chm-cli\\target\\npm\\publisher-db2chm-win32-x64-0.2.0-beta.1.tgz'),'publisher-db2chm-win32-x64-0.2.0-beta.1.tgz');
  assert.equal(artifactName('/home/runner/target/npm/publisher-db2chm-linux-x64-0.2.0-beta.1.tgz'),'publisher-db2chm-linux-x64-0.2.0-beta.1.tgz');
  assert.throws(()=>artifactName('package.tgz\n'),/Invalid tarball report path/);
 }finally{path.basename=previous;}
});
