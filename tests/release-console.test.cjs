'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),{configure}=require('../scripts/release-console.cjs');
test('scope configuration changes all current names while preserving publisher, version and beta evidence policy',()=>{
 const folder=fs.mkdtempSync(path.join(root,'target/scope-config-test-'));
 const names=['release/config.json','release/beta-policy.json','package.json','package-lock.json'];
 try{
  const originals=names.map(name=>fs.readFileSync(path.join(root,name)));
  for(let i=0;i<names.length;i++){const dest=path.join(folder,names[i]);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,originals[i]);}
  const before=JSON.parse(originals[0]),policy=JSON.parse(originals[1]);
  assert.deepEqual(configure(before.scope,false,folder).changedFiles,[]);
  configure('@example-org',true,folder);for(let i=0;i<names.length;i++)assert(fs.readFileSync(path.join(folder,names[i])).equals(originals[i]));
  for(const bad of ['','@a/b','Org','org;whoami','org with space'])assert.throws(()=>configure(bad,false,folder));
  const result=configure('@example-org',false,folder),values=names.map(name=>JSON.parse(fs.readFileSync(path.join(folder,name))));
  assert.equal(values[0].scope,'example-org');assert.equal(values[0].publisher,before.publisher);assert.equal(values[0].version,before.version);
  assert.deepEqual(values[1],{...policy,scope:'example-org'});assert.equal(values[2].name,'@example-org/db2chm-development');assert.equal(values[3].packages[''].name,values[2].name);
  for(let i=0;i<names.length;i++)assert(fs.readFileSync(path.join(result.backupDirectory,names[i])).equals(originals[i]));
  for(let i=0;i<names.length;i++)assert(fs.readFileSync(path.join(root,names[i])).equals(originals[i]),'Real workspace was changed');
 }finally{assert(folder.startsWith(path.join(root,'target')+path.sep));fs.rmSync(folder,{recursive:true,force:true});}
});
function simulatePublish({missingPlatform=false,account='jackiechen1994',only='entry'}={}){
 const published=[],packages=['win32-x64','darwin-x64','darwin-arm64','linux-x64',null].map(platform=>({name:'@jackiechen1994/db2chm'+(platform?'-'+platform:''),platform,integrity:'sha512-'+(platform||'entry'),tarball:(platform||'entry')+'.tgz'}));
 const context={require:name=>{
  if(name==='./verify-release.cjs')return()=>({report:{version:'0.2.0-beta.1'},packages});
  if(name==='../release/config.json')return {publisher:'jackiechen1994'};
  if(name==='./npm-command.cjs')return args=>{
   if(args[0]==='whoami')return {stdout:account};
   if(args[0]==='publish'){published.push(args[1]);return {stdout:''};}
   const item=packages.find(p=>args[1]===p.name+'@0.2.0-beta.1');
   if(args[0]==='view'&&args[2]==='dist.integrity'){
    if((missingPlatform&&item.platform)||(!item.platform&&!published.includes('entry.tgz')))throw new Error('E404 package not found');
    return {stdout:JSON.stringify(item.integrity)};
   }
   if(args[0]==='view'&&args[2]==='dist-tags')return {stdout:JSON.stringify({beta:'0.2.0-beta.1'})};
   throw new Error('Unexpected npm operation');
  };
  return require(name);
 },console:{log(){}},process:{argv:['node','publish.cjs','bundle','beta','--publish','--only',only],env:{},exit(){throw new Error('Unexpected exit');}}};
 let error;try{vm.runInNewContext(fs.readFileSync(path.join(root,'scripts/publish.cjs'),'utf8'),context);}catch(e){error=e;}
 return {published,error};
}
test('manual entry publication requires all exact platform versions and the expected logged-in account',()=>{
 const missing=simulatePublish({missingPlatform:true});assert.match(missing.error.message,/E404/);assert.deepEqual(missing.published,[]);
 const wrong=simulatePublish({account:'other-user'});assert.match(wrong.error.message,/login account/);assert.deepEqual(wrong.published,[]);
 const invalid=simulatePublish({only:'typo'});assert.match(invalid.error.message,/Invalid --only/);assert.deepEqual(invalid.published,[]);
 const ready=simulatePublish();assert.equal(ready.error,undefined);assert.deepEqual(ready.published,['entry.tgz']);
});
