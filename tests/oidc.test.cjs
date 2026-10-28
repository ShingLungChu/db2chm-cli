'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),test=require('node:test'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),runtime=require('../scripts/oidc-runtime.cjs'),publisher=require('../release/trusted-publisher.json');
function context(){return {GITHUB_ACTIONS:'true',GITHUB_REPOSITORY:'ShingLungChu/db2chm-cli',GITHUB_REF:'refs/heads/main',GITHUB_WORKFLOW_REF:'ShingLungChu/db2chm-cli/.github/workflows/npm-publish.yml@refs/heads/main',ACTIONS_ID_TOKEN_REQUEST_URL:'https://example.invalid/oidc',ACTIONS_ID_TOKEN_REQUEST_TOKEN:'test-only-never-log'};}
test('OIDC preflight restricts workflow identity and refuses npm credentials without exposing values',()=>{
 assert.equal(runtime.checkContext(context()).environment,'npm-release');
 for(const change of [{GITHUB_REF:'refs/heads/other'},{GITHUB_REPOSITORY:'other/repo'},{GITHUB_WORKFLOW_REF:'ShingLungChu/db2chm-cli/.github/workflows/other.yml@refs/heads/main'},{ACTIONS_ID_TOKEN_REQUEST_TOKEN:''}])assert.throws(()=>runtime.checkContext({...context(),...change}));
 for(const name of ['NODE_AUTH_TOKEN','NPM_TOKEN','NPM_ID_TOKEN','npm_config__authtoken']){
  assert.throws(()=>runtime.checkContext({...context(),[name]:'test-credential-secret'}),error=>!error.message.includes('test-credential-secret')&&error.message.includes(name));
 }
});
test('OIDC preparation writes empty isolated npm config without claiming authentication success',()=>{
 const folder=fs.mkdtempSync(path.join(root,'target/oidc-runtime-test-'));
 try{
  const env={...context(),RUNNER_TEMP:folder,GITHUB_ENV:path.join(folder,'github-env')},result=runtime.prepare(env),lines=fs.readFileSync(env.GITHUB_ENV,'utf8').trim().split('\n'),values=Object.fromEntries(lines.map(line=>{const i=line.indexOf('=');return [line.slice(0,i),line.slice(i+1)];}));
  assert.equal(result.oidcAuthenticationExercised,false);assert.equal(fs.readFileSync(values.NPM_CONFIG_USERCONFIG,'utf8'),'');assert.equal(fs.readFileSync(values.NPM_CONFIG_GLOBALCONFIG,'utf8'),'');
  assert(!fs.readFileSync(env.GITHUB_ENV,'utf8').includes(env.ACTIONS_ID_TOKEN_REQUEST_TOKEN));assert.equal(values.DB2CHM_AUTH_MODE,undefined);
 }finally{assert(folder.startsWith(path.join(root,'target')+path.sep));fs.rmSync(folder,{recursive:true,force:true});}
});
function publication(existing){
 const packages=['win32-x64','darwin-x64','darwin-arm64','linux-x64',null].map(platform=>({name:'@t20-ai/db2chm'+(platform?'-'+platform:''),platform,integrity:'sha512-'+(platform||'entry'),tarball:(platform||'entry')+'.tgz'}));
 const uploads=[],results=[],present=new Set(existing?packages.map(p=>p.name):[]),env={...context(),DB2CHM_AUTH_MODE:'oidc-only'};
 const sandbox={process:{argv:['node','publish.cjs','bundle','beta','--publish','--require-oidc'],env},console:{log(){}},require:name=>{
  if(name==='./verify-release.cjs')return()=>({report:{version:'0.2.0-beta.1',sourceCommit:'a'.repeat(40)},packages});
  if(name==='./oidc-runtime.cjs')return {checkContext:()=>runtime.checkContext(env)};
  if(name==='node:fs')return {writeFileSync:(_file,value)=>results.push(JSON.parse(value))};
  if(name==='./npm-command.cjs')return args=>{
   if(args[0]==='publish'){assert(args.includes('--provenance'));const p=packages.find(p=>p.tarball===args[1]);present.add(p.name);uploads.push(p.name);return {stdout:''};}
   if(args[0]==='view'&&args[2]==='dist.integrity'){
    const p=packages.find(p=>args[1]===p.name+'@0.2.0-beta.1');if(!present.has(p.name))throw new Error('E404 missing');return {stdout:JSON.stringify(p.integrity)};
   }
   if(args[0]==='view'&&args[2]==='dist-tags')return {stdout:JSON.stringify({beta:'0.2.0-beta.1'})};
   throw new Error('Unexpected npm operation: '+args[0]);
  };
  return require(name);
 }};
 let error;try{vm.runInNewContext(fs.readFileSync(path.join(root,'scripts/publish.cjs'),'utf8'),sandbox);}catch(e){error=e;}
 return {error,uploads,result:results.at(-1)};
}
test('OIDC publication distinguishes five real uploads from a no-upload replay',()=>{
 const replay=publication(true);assert.match(replay.error.message,/OIDC_NOT_EXERCISED/);assert.equal(replay.uploads.length,0);assert.equal(replay.result.allFivePackagesExercisedOidc,false);
 const fresh=publication(false);assert.equal(fresh.error,undefined);assert.equal(fresh.uploads.length,5);assert.equal(fresh.uploads.at(-1),'@t20-ai/db2chm');assert.equal(fresh.result.allFivePackagesExercisedOidc,true);
});
test('OIDC workflow pins runtime, exposes dry-run and avoids npm secrets',()=>{
 const text=fs.readFileSync(path.join(root,'.github/workflows/npm-publish.yml'),'utf8');
 assert(text.includes('npm@'+publisher.npmVersion));assert(text.includes('node scripts/oidc-runtime.cjs prepare'));assert(text.includes('node scripts/oidc-runtime.cjs check'));assert(text.includes('--require-oidc'));
 assert(text.includes('id-token: write'));assert(text.includes('environment: '+publisher.environment));assert(!/secrets\.(?:NPM_TOKEN|NODE_AUTH_TOKEN)/.test(text));assert(/dry_run:[\s\S]*?default: true/.test(text));
});
