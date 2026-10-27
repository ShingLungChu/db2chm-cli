#!/usr/bin/env node
'use strict';
// Preserve successful CI tarballs as a review bundle; never turn prototypes into releases.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const config=require('../release/config.json');
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const readJson=file=>JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
const member=(file,name)=>execFileSync('tar',['-xOf',file,'package/'+name],{maxBuffer:32*1024*1024});
function collect(input,output){
 input=fs.realpathSync(input);output=path.resolve(output);
 if(fs.existsSync(output))throw new Error('Use a fresh validation bundle directory');
 const run=readJson(path.join(input,'run.json'));
 assert.equal(run.conclusion,'success');assert.equal(run.head_repository?.full_name,'ShingLungChu/db2chm-cli');
 assert.match(run.head_sha,/^[a-f0-9]{40}$/);
 const artifactIndex=readJson(path.join(input,'artifacts.json')).artifacts;
 const artifactDigests=[];
 const requiredArtifacts=['shared-java-runtime',...config.platforms.flatMap(p=>['private-native-npm-'+p.id,'native-'+p.id])];
 for(const name of requiredArtifacts){
  const matches=artifactIndex.filter(a=>a.name===name&&!a.expired);assert.equal(matches.length,1,'Missing immutable artifact: '+name);
  const a=matches[0];assert.equal(a.workflow_run.head_sha,run.head_sha);assert.match(a.digest||'',/^sha256:[a-f0-9]{64}$/);
  assert.equal('sha256:'+sha(fs.readFileSync(path.join(input,name+'.zip'))),a.digest,'Artifact archive digest mismatch: '+name);
  artifactDigests.push({name,id:a.id,sha256:a.digest.slice(7)});
 }
 const provenance={runId:String(run.id),sourceCommit:run.head_sha,workflowRunUrl:run.html_url,kind:'private-validation-bundle',releaseEligible:false};
 const packages=[],natives=[],entries=[];
 const entryName='@'+config.scope+'/'+config.name;
 const entryPayloads=['package.json','README.md','help.txt','bin/db2chm.cjs','bin/info.cjs'];
 for(const target of config.platforms){
  const name='private-native-npm-'+target.id,artifact=artifactIndex.filter(a=>a.name===name&&!a.expired);
  assert.equal(artifact.length,1,'Missing exact npm artifact: '+name);
  assert.equal(artifact[0].workflow_run.head_sha,run.head_sha);
  const directory=path.join(input,name),report=readJson(path.join(directory,'report-'+target.id+'.json'));
  assert.equal(report.sourceCommit,run.head_sha);assert.equal(report.version,config.version);
  assert.equal(report.kind,'prototype');assert.equal(report.prototype,true);assert.equal(report.nativePrototype,true);assert.equal(report.packages.length,2);
  const nativeRoot=path.join(input,'native-'+target.id),native=readJson(path.join(nativeRoot,'manifest.json'));
  assert.equal(native.platform,target.id);assert.match(native.sourceCommit,/^[a-f0-9]{40}$/);assert(native.files.length>0);
  for(const f of native.files){
   assert.equal(path.basename(f.path),f.path,'Native files must be flat regular files');
   const full=path.join(nativeRoot,f.path);assert(fs.statSync(full).isFile());assert.equal(fs.realpathSync(full),path.resolve(full));
   const bytes=fs.readFileSync(full);assert.equal(bytes.length,f.bytes);assert.equal(sha(bytes),f.sha256);
  }
  const platformReport=report.packages.find(p=>p.platform===target.id),entryReport=report.packages.find(p=>p.platform===null);
  assert(platformReport&&entryReport);
  for(const [item,platform] of [[platformReport,target.id],[entryReport,null]]){
   const file=path.join(directory,path.basename(item.tarball)),bytes=fs.readFileSync(file);
   assert.equal(bytes.length,item.bytes);assert.equal(sha(bytes),item.sha256);
   const manifest=JSON.parse(member(file,'package.json'));
   assert.equal(manifest.name,platform?entryName+'-'+platform:entryName);assert.equal(manifest.version,config.version);assert.equal(manifest.private,true);
   assert(!manifest.scripts&&!manifest.dependencies,'No lifecycle hooks or hidden dependencies');
   const listing=execFileSync('tar',['-tzf',file],{encoding:'utf8',maxBuffer:2*1024*1024}).trim().split(/\r?\n/);
   assert(listing.every(n=>/^package\/(?:package\.json|README\.md|help\.txt|THIRD_PARTY_NOTICES\.md|runtime-sbom\.cdx\.json|(?:bin|lib|native|licenses)\/[^\r\n]+)$/.test(n)&&!n.includes('\\')&&!n.split('/').includes('..')),'Unexpected package payload');
   if(platform){
    assert.deepEqual(manifest.os,[target.os]);assert.deepEqual(manifest.cpu,[target.cpu]);assert(!manifest.optionalDependencies);
    assert.equal(sha(member(file,'lib/db2chm.jar')),report.jarSha256);
    const sbom=JSON.parse(member(file,'runtime-sbom.cdx.json'));
    assert.equal(sbom.metadata.component.properties.find(p=>p.name==='sourceCommit').value,run.head_sha);
    for(const f of native.files)assert.equal(sha(member(file,'native/'+f.path)),f.sha256,'Packaged native payload differs');
    const result={name:manifest.name,platform,tarball:path.basename(file),bytes:bytes.length,sha256:item.sha256,jarSha256:report.jarSha256,nativeSha256:native.files.find(f=>f.path===target.compiler)?.sha256,artifactId:artifact[0].id};
    assert(result.nativeSha256);packages.push({...result,sourceFile:file});
   }else{
    assert.deepEqual(manifest.optionalDependencies,Object.fromEntries(config.platforms.map(p=>[entryName+'-'+p.id,config.version])));
    entries.push({platform:target.id,sourceFile:file,tarball:path.basename(file),bytes:bytes.length,sha256:item.sha256,payload:Object.fromEntries(entryPayloads.map(n=>[n,sha(member(file,n))]))});
   }
  }
  natives.push({platform:target.id,sourceCommit:native.sourceCommit,bootstrapVersion:native.bootstrapVersion,bootstrapSha256:native.bootstrapSha256,redistributionApproved:native.redistributionApproved,files:native.files});
 }
 assert.equal(new Set(packages.map(p=>p.jarSha256)).size,1,'All platforms must use the same Java payload');
 for(const e of entries)assert.deepEqual(e.payload,entries[0].payload,'Entry payload differs across runners');
 const shared=fs.readFileSync(path.join(input,'shared-java-runtime/db2chm.jar'));
 assert.equal(sha(shared),packages[0].jarSha256);
 const entry=entries.find(e=>e.platform==='win32-x64');
 const sizes=packages.map(p=>({platform:p.platform,platformBytes:p.bytes,entryBytes:entry.bytes,totalDownloadBytes:p.bytes+entry.bytes,belowLimit:p.bytes+entry.bytes<config.maxDownloadBytes}));
 assert(sizes.every(s=>s.belowLimit),'Installation download budget exceeded');
 // All validation above completes before a new bundle is materialized.
 fs.mkdirSync(output,{recursive:true});
 const copied=[...packages,{...entry,name:entryName,platform:null}];
 for(const p of copied)fs.copyFileSync(p.sourceFile,path.join(output,p.tarball));
 const report={schemaVersion:1,...provenance,version:config.version,prototype:true,nativePrototype:true,maxDownloadBytes:config.maxDownloadBytes,jarSha256:packages[0].jarSha256,packages:copied.map(({sourceFile,payload,...p})=>p),sizes,natives,artifactDigests,entryAlternatives:entries.map(({sourceFile,...e})=>e)};
 fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(report,null,2)+'\n');
 fs.writeFileSync(path.join(output,'SHA256SUMS'),copied.map(p=>p.sha256+'  '+p.tarball).join('\n')+'\n');
 fs.writeFileSync(path.join(output,'README.md'),'# Private validation bundle\n\nExact CI artifacts from '+run.html_url+' at '+run.head_sha+'.\nAll tarballs remain private prototypes. This bundle is not a publishable release candidate.\nSelected one Windows-built entry archive; all four entry payloads were compared.\nDo not use this bundle as accepted-npm-release.\n');
 return report;
}
module.exports=collect;
if(require.main===module){
 try{if(!process.argv[2]||!process.argv[3])throw new Error('Usage: collect-validation.cjs INPUT_ARTIFACT_DIRECTORY NEW_OUTPUT_DIRECTORY');const result=collect(process.argv[2],process.argv[3]);console.log(JSON.stringify({ok:true,sourceCommit:result.sourceCommit,kind:result.kind,releaseEligible:result.releaseEligible,sizes:result.sizes},null,2));}
 catch(error){console.error('VALIDATION_COLLECTION_FAILED: '+error.message);process.exitCode=1;}
}
