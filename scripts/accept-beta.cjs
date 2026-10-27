#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict'),beta=require('./beta-policy.cjs'),verifyNative=require('./verify-beta-native.cjs');
const bundle=path.resolve(process.argv[2]),checksRoot=path.resolve(process.argv[3]),root=path.resolve(__dirname,'..');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),read=f=>JSON.parse(fs.readFileSync(f,'utf8'));
const beforeBytes=fs.readFileSync(path.join(bundle,'report.json')),report=JSON.parse(beforeBytes),testedHash=hash(beforeBytes);
assert.equal(report.version,beta.policy.version);assert.equal(report.releaseChannel,'beta');assert.equal(report.prototype,false);
const baseline=require('../docs/evidence/personal-scope-acceptance-4de172f.json');assert.equal(report.jarSha256,baseline.sharedJarSha256);
const records=[];
function walk(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const f=path.join(dir,e.name);if(e.isDirectory())walk(f);else if(e.name==='installed.json')records.push(read(f));}}
walk(checksRoot);assert.equal(records.length,4);assert.equal(new Set(records.map(r=>r.platform)).size,4);
const extraction=read(path.join(checksRoot,'windows-verification.json'));assert.equal(extraction.length,4);assert(extraction.every(r=>r.hhExitCode===0&&r.extractedVerified===4));
for(const p of report.packages.filter(p=>p.platform)){
 const c=records.find(r=>r.platform===p.platform);assert(c?.ok);assert.equal(c.reportSha256,testedHash);assert.equal(c.platformSha256,p.sha256);assert(c.totalDownloadBytes<15000000);
 verifyNative(path.join(bundle,path.basename(p.tarball)),p.platform,report.jarSha256);
}
const evidence=path.join(bundle,'evidence');fs.mkdirSync(evidence,{recursive:true});
fs.writeFileSync(path.join(evidence,'package-report-before-acceptance.json'),beforeBytes);
fs.copyFileSync(path.join(checksRoot,'windows-verification.json'),path.join(evidence,'windows-verification.json'));
fs.writeFileSync(path.join(evidence,'beta-installation-checks.json'),JSON.stringify({ok:true,scope:'four exact candidate tarballs via entry package',records,testedReportSha256:testedHash},null,2)+'\n');
for(const [source,name] of [['release/beta-policy.json','beta-policy.json'],['docs/native-beta-distribution-review.md','native-beta-distribution-review.md'],['docs/support-matrix.md','support-matrix.md'],['docs/evidence/personal-scope-acceptance-4de172f.json','accepted-baseline.json'],['docs/evidence/chm-viewer-human-acceptance-20261027.json','document-acceptance.json']])fs.copyFileSync(path.join(root,source),path.join(evidence,name));
report.releaseEligible=true;fs.writeFileSync(path.join(bundle,'report.json'),JSON.stringify(report,null,2)+'\n');
const items=fs.readdirSync(evidence).map(name=>({path:'evidence/'+name,sha256:hash(fs.readFileSync(path.join(evidence,name)))}));
const gates={};for(const id of ['G0','G1','G2','G3','G4','G5'])gates[id]={status:beta.policy.allowedGateStatuses[id]||'passed',...(beta.policy.limitations[id]?{limitations:beta.policy.limitations[id]}:{}),evidence:items};
const acceptance={schemaVersion:1,version:report.version,sourceCommit:report.sourceCommit,reportSha256:hash(fs.readFileSync(path.join(bundle,'report.json'))),testedReportSha256:testedHash,betaPolicySha256:beta.hash(),gates,databases:{ORACLE:'passed',DM:'passed',VASTBASE:'passed',MYSQL:'unverified',KINGBASE:'unverified'},databaseAccountAndSqlAudit:'unverified; user has no evidence',releaseChannel:'beta',stableReleaseApproved:false};
fs.writeFileSync(path.join(bundle,'acceptance.json'),JSON.stringify(acceptance,null,2)+'\n');
require('./verify-release.cjs')(bundle,path.join(bundle,'acceptance.json'));
console.log(JSON.stringify({accepted:true,releaseChannel:'beta',stableReleaseApproved:false,version:report.version,sourceCommit:report.sourceCommit,packages:report.packages.length}));
