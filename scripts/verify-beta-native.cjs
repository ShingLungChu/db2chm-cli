'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto'),{execFileSync}=require('node:child_process');
const baseline=require('../docs/evidence/personal-scope-acceptance-4de172f.json');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const notice=Buffer.from('{ db2chm modifications, 2026-10-27: UTF-8/CP936 navigation and search; Unicode Windows arguments. See included patches. }\n');
module.exports=function(file,platform,jarSha256){
 const read=name=>execFileSync('tar',['-xOf',file,'package/'+name],{maxBuffer:32*1024*1024});
 const expected=baseline.acceptedBaselinePayloadIdentity.platforms.find(p=>p.platform===platform);
 const input=baseline.nativeInputSummary.find(p=>p.platform===platform);
 assert.equal(jarSha256,baseline.sharedJarSha256);assert.equal(hash(read('lib/db2chm.jar')),jarSha256);
 const binary=read('native/'+(platform==='win32-x64'?'chmcmd.exe':'chmcmd'));
 assert.equal(hash(binary),expected.nativeSha256,'Accepted native payload changed');
 const review=JSON.parse(read('native/native-license-review.json'));
 assert.equal(review.status,'beta-distribution-materials-checked');assert.equal(review.binarySha256,hash(binary));assert.equal(review.compiledUtilitySourcesChecked,15);
 const provenanceBytes=read('native/build-provenance.json');assert.equal(hash(provenanceBytes),input.provenanceFileSha256);
 const provenance=JSON.parse(provenanceBytes),archive=read('native/chm-source.tar.gz');assert.equal(hash(archive),review.releasedArchiveSha256);
 const source=name=>execFileSync('tar',['-xzOf','-',name],{input:archive,maxBuffer:4*1024*1024});
 const mapping=JSON.parse(source('source-manifest.json'));
 assert.equal(mapping.sourceAlreadyPatched,true);assert.equal(provenance.compiledUtilitySources.length,15);
 for(const entry of provenance.compiledUtilitySources){
  const body=source(entry.path),change=mapping.dateNoticeChanges.find(c=>c.path===entry.path);
  if(change){assert(body.subarray(0,notice.length).equals(notice));assert.equal(change.prefixBytes,notice.length);assert.equal(hash(body),change.releasedSourceSha256);assert.equal(hash(body.subarray(notice.length)),entry.sha256);}
  else assert.equal(hash(body),entry.sha256);
 }
 assert(mapping.dateNoticeChanges.length>=6);
 for(const name of ['REBUILD.md','rebuild.sh','rebuild.ps1'])assert(source(name).length>100,'Missing rebuild material');
 assert(read('native/RTL-COPYING.txt').includes(Buffer.from('GNU LESSER GENERAL PUBLIC LICENSE')));
 assert(read('native/RTL-COPYING.FPC').includes(Buffer.from('independent modules')));
 assert(read('native/FastHTMLParser-LICENSE.txt').includes(Buffer.from('This code may be used and modified by anyone')));
 assert(read('THIRD_PARTY_NOTICES.md').includes(Buffer.from('UNLICENSED')));
 return {compiledSources:15,binaryUnchanged:true,sourceArchiveSha256:hash(archive)};
};
