#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{execFileSync}=require('node:child_process'),config=require('../release/config.json');
const api='https://api.github.com/repos/ShingLungChu/db2chm-cli',hash=b=>crypto.createHash('sha256').update(b).digest('hex');
async function download(runId,output){
 if(!/^[0-9]+$/.test(runId||''))throw new Error('A numeric successful workflow run ID is required');
 const token=process.env.GH_TOKEN||process.env.GITHUB_TOKEN;
 if(!token)throw new Error('Use a read-only GitHub Actions token via GH_TOKEN or GITHUB_TOKEN');
 output=path.resolve(output);fs.mkdirSync(output,{recursive:true});
 async function request(route){
  const response=await fetch(api+route,{headers:{Authorization:'Bearer '+token,'X-GitHub-Api-Version':'2022-11-28'},signal:AbortSignal.timeout(180000)});
  if(!response.ok)throw new Error('GitHub artifact request failed with HTTP '+response.status);
  return response;
 }
 const run=await(await request('/actions/runs/'+runId)).json();
 if(run.conclusion!=='success'||run.head_repository?.full_name!=='ShingLungChu/db2chm-cli')throw new Error('Require a successful source run in this repository');
 const index=await(await request('/actions/runs/'+runId+'/artifacts?per_page=100')).json();
 const names=['shared-java-runtime',...config.platforms.flatMap(p=>['private-native-npm-'+p.id,'native-'+p.id])];
 const artifacts=names.map(name=>{const found=index.artifacts.filter(a=>a.name===name&&!a.expired);if(found.length!==1||found[0].workflow_run.head_sha!==run.head_sha||!/^sha256:[a-f0-9]{64}$/.test(found[0].digest||''))throw new Error('Missing immutable artifact '+name);return found[0];});
 const records=[];let next=0;
 await Promise.all(Array.from({length:3},async()=>{
  while(next<artifacts.length){
   const a=artifacts[next++],archive=path.join(output,a.name+'.zip'),expected=a.digest.slice(7);
   if(!fs.existsSync(archive)||hash(fs.readFileSync(archive))!==expected){
    const bytes=Buffer.from(await(await request('/actions/artifacts/'+a.id+'/zip')).arrayBuffer());
    if(hash(bytes)!==expected)throw new Error('Artifact archive checksum mismatch: '+a.name);
    fs.writeFileSync(archive,bytes);
   }
   const destination=path.join(output,a.name);
   if(!fs.existsSync(destination)){
    const python=process.env.PYTHON||(process.platform==='win32'?'python':'python3');
    execFileSync(python,[path.join(__dirname,'extract-ci-artifact.py'),archive,destination,...(a.name.startsWith('native-')?['--flat']:[])],{stdio:'pipe'});
   }
   records.push({name:a.name,artifactId:a.id,zipBytes:fs.statSync(archive).size,artifactDigest:a.digest,directory:a.name});
   console.log(JSON.stringify({verifiedArtifact:a.name}));
  }
 }));
 fs.writeFileSync(path.join(output,'run.json'),JSON.stringify(run,null,2)+'\n');
 fs.writeFileSync(path.join(output,'artifacts.json'),JSON.stringify(index,null,2)+'\n');
 fs.writeFileSync(path.join(output,'download-record.json'),JSON.stringify({sourceCommit:run.head_sha,runId:run.id,artifacts:records.sort((a,b)=>a.name.localeCompare(b.name,'en'))},null,2)+'\n');
 return {runId,sourceCommit:run.head_sha,artifacts:records.length};
}
module.exports=download;
if(require.main===module)download(process.argv[2],process.argv[3]||'target/downloaded-'+process.argv[2]).then(r=>console.log(JSON.stringify(r))).catch(()=>{console.error('DOWNLOAD_VALIDATION_FAILED: source run, access, archive checksum or extraction could not be verified');process.exitCode=1;});
