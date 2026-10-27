#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const config=require('../release/config.json'),policy=require('./beta-policy.cjs').policy;
const root=path.resolve(__dirname,'..'),input=path.resolve(process.argv[2]||'target/review-inputs');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
assert.equal(config.version,policy.version);assert.equal(config.scope,policy.scope);
const run=read(path.join(input,'run.json'));assert.equal(run.head_sha,policy.acceptedBaseline);assert.equal(run.conclusion,'success');
// Packaging/licensing changes can reuse the accepted executable code, never a changed application.
execFileSync('git',['diff','--exit-code',policy.acceptedBaseline,'HEAD','--','src/main/java','src/main/resources','npm','pom.xml'],{cwd:root,stdio:'pipe'});
const baseline=read(path.join(root,policy.requiredEvidence));
assert.equal(baseline.sourceCommit,policy.acceptedBaseline);
const checked=require('./collect-validation.cjs')(input,path.join(root,'target/beta-input-check'),{scope:baseline.scope,version:config.version});
const python=process.env.PYTHON||(process.platform==='win32'?'python':'python3');
for(const p of config.platforms){
 const out=path.join(root,'target/native',p.id);
 execFileSync(python,[path.join(root,'scripts/prepare-beta-native.py'),path.join(input,'native-'+p.id),out,p.id],{stdio:'inherit'});
}
fs.copyFileSync(path.join(input,'shared-java-runtime/db2chm.jar'),path.join(root,'target/db2chm.jar'));
execFileSync(process.execPath,[path.join(root,'scripts/package.cjs'),'--channel','beta'],{cwd:root,stdio:'pipe'});
const report=read(path.join(root,'target/npm/report.json'));assert.equal(report.jarSha256,checked.jarSha256);
console.log(JSON.stringify({ok:true,version:report.version,sourceCommit:report.sourceCommit,releaseChannel:report.releaseChannel,sizes:report.sizes}));
