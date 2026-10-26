'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),verify=require('../scripts/verify-release.cjs');
test('prototype reports and forged candidate flags cannot bypass publication gates',()=>{
 const bundle=path.resolve(__dirname,'../target/npm');
 assert.throws(()=>verify(bundle,path.join(bundle,'acceptance.json')),/Private prototypes/);
 const dir=fs.mkdtempSync(path.resolve(__dirname,'../target/release-guard-'));
 try{
  const report=JSON.parse(fs.readFileSync(path.join(bundle,'report.json'),'utf8'));
  report.prototype=false;report.kind='candidate';report.releaseEligible=true;
  for(const item of report.packages)fs.copyFileSync(item.tarball,path.join(dir,path.basename(item.tarball)));
  fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report));
  assert.throws(()=>verify(dir,path.join(dir,'acceptance.json')),/Unexpected package manifest/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
