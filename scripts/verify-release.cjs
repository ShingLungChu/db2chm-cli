#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{execFileSync}=require('node:child_process'),config=require('../release/config.json');
function verify(bundle,acceptancePath){
 bundle=path.resolve(bundle);
 const report=JSON.parse(fs.readFileSync(path.join(bundle,'report.json'),'utf8'));
 if(report.prototype||report.kind!=='candidate')throw new Error('Private prototypes cannot be published');
 if(report.version!==config.version||!/^[0-9a-f]{40}$/.test(report.sourceCommit||''))throw new Error('Invalid version/source identity');
 if(process.env.DB2CHM_EXPECTED_COMMIT&&report.sourceCommit!==process.env.DB2CHM_EXPECTED_COMMIT)throw new Error('Artifact source does not match the workflow run');
 const expected=['@'+config.scope+'/'+config.name,...config.platforms.map(p=>'@'+config.scope+'/'+config.name+'-'+p.id)].sort();
 if(JSON.stringify(report.packages.map(p=>p.name).sort())!==JSON.stringify(expected))throw new Error('Release needs exactly the entry and four platform packages');
 const packages=report.packages.map(item=>{
  const tarball=path.join(bundle,path.basename(item.tarball)),bytes=fs.readFileSync(tarball);
  if(crypto.createHash('sha256').update(bytes).digest('hex')!==item.sha256||bytes.length!==item.bytes)throw new Error('Package hash/size mismatch');
  const manifest=JSON.parse(execFileSync('tar',['-xOf',tarball,'package/package.json'],{encoding:'utf8',maxBuffer:1024*1024}));
  if(manifest.private===true||manifest.name!==item.name||manifest.version!==report.version||manifest.scripts)throw new Error('Unexpected package manifest or lifecycle hooks');
  const listing=execFileSync('tar',['-tzf',tarball],{encoding:'utf8',maxBuffer:2*1024*1024}).trim().split(/\r?\n/);
  if(listing.some(name=>!/^package\/(?:package\.json|README\.md|help\.txt|THIRD_PARTY_NOTICES\.md|runtime-sbom\.cdx\.json|(?:bin|lib|native|licenses)\/[^\r\n]+)$/.test(name)||name.split('/').includes('..')))throw new Error('Unexpected tarball payload');
  if(item.platform){
   const target=config.platforms.find(p=>p.id===item.platform);
   if(JSON.stringify(manifest.os)!==JSON.stringify([target.os])||JSON.stringify(manifest.cpu)!==JSON.stringify([target.cpu])||manifest.dependencies||manifest.optionalDependencies)throw new Error('Platform package constraints changed');
  }else{
   const dependencies=Object.fromEntries(config.platforms.map(p=>['@'+config.scope+'/'+config.name+'-'+p.id,config.version]));
   if(manifest.dependencies||JSON.stringify(manifest.optionalDependencies)!==JSON.stringify(dependencies))throw new Error('Entry dependencies must match exact platform versions');
  }
  return {...item,tarball,integrity:'sha512-'+crypto.createHash('sha512').update(bytes).digest('base64')};
 });
 const entry=packages.find(p=>!p.platform);
 for(const item of packages.filter(p=>p.platform))if(item.bytes+entry.bytes>=config.maxDownloadBytes)throw new Error('Installation size exceeds hard limit');
 const acceptance=JSON.parse(fs.readFileSync(acceptancePath,'utf8'));
 if(acceptance.version!==report.version||acceptance.sourceCommit!==report.sourceCommit||acceptance.reportSha256!==crypto.createHash('sha256').update(fs.readFileSync(path.join(bundle,'report.json'))).digest('hex'))throw new Error('Acceptance must identify these exact artifacts');
 const evidenceRoot=path.dirname(path.resolve(acceptancePath));
 for(const id of ['G0','G1','G2','G3','G4','G5']){
  const gate=acceptance.gates?.[id];
  if(gate?.status!=='passed'||!gate.evidence?.length)throw new Error(id+' acceptance evidence is missing');
  for(const evidence of gate.evidence){
   const file=fs.realpathSync(path.resolve(evidenceRoot,evidence.path));
   if(!file.startsWith(evidenceRoot+path.sep)||crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')!==evidence.sha256)throw new Error('Evidence path/hash mismatch');
  }
 }
 for(const type of ['ORACLE','DM'])if(acceptance.databases?.[type]!=='passed')throw new Error('Oracle and DM are required for this release');
 return {report,packages};
}
module.exports=verify;
if(require.main===module){
 const bundle=process.argv[2]||'target/npm',acceptance=process.argv[3]||path.join(bundle,'acceptance.json');
 try{const result=verify(bundle,acceptance);console.log('Verified exact release artifacts for '+result.report.version);}
 catch(error){console.error('RELEASE_BLOCKED: '+error.message);process.exitCode=1;}
}
