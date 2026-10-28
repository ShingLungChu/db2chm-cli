#!/usr/bin/env node
'use strict';
const path=require('node:path'),fs=require('node:fs'),verify=require('./verify-release.cjs'),npm=require('./npm-command.cjs');
const args=process.argv.slice(2),bundle=args[0]||'target/npm',tag=args[1]||'beta';
if(!['beta','next'].includes(tag))throw new Error('Only beta/next publishing is enabled; promote latest after registry acceptance');
const {report,packages}=verify(bundle,path.join(bundle,'acceptance.json'));
const onlyIndex=args.indexOf('--only'),only=onlyIndex===-1?null:args[onlyIndex+1];
if(onlyIndex!==-1&&!['entry',...packages.filter(p=>p.platform).map(p=>p.platform)].includes(only))throw new Error('Invalid --only platform; use entry or an exact supported platform ID');
const selected=only?packages.filter(p=>only==='entry'?!p.platform:p.platform===only):packages;
const requireOidc=args.includes('--require-oidc'),events=[];
if(requireOidc){
 require('./oidc-runtime.cjs').checkContext();
 if(process.env.DB2CHM_AUTH_MODE!=='oidc-only')throw new Error('OIDC runtime preflight must complete before publishing');
}
if(!args.includes('--publish')){console.log('Release verified; --publish is required to write to npm');process.exit(0);}
if(process.env.GITHUB_ACTIONS!=='true'){
 // CI's trusted-publisher identity is exchanged per package; local sessions have an explicit account.
 const account=npm(['whoami','--registry=https://registry.npmjs.org']).stdout.trim();
 if(account!==require('../release/config.json').publisher)throw new Error('npm login account must match release/config.json publisher');
}
if(only==='entry')for(const item of packages.filter(p=>p.platform)){
 const integrity=JSON.parse(npm(['view',item.name+'@'+report.version,'dist.integrity','--json','--registry=https://registry.npmjs.org']).stdout);
 if(integrity!==item.integrity)throw new Error('Publish all four exact platform tarballs before the entry: '+item.name);
}
for(const item of selected.sort((a,b)=>Number(!a.platform)-Number(!b.platform))){
 let exists=false;
 try{
  const integrity=JSON.parse(npm(['view',item.name+'@'+report.version,'dist.integrity','--json','--registry=https://registry.npmjs.org']).stdout);
  if(integrity!==item.integrity)throw new Error('Published version has different bytes: '+item.name);
  exists=true;
 }catch(error){if(!error.message.includes('E404'))throw error;}
 if(!exists)npm(['publish',item.tarball,'--access=public','--tag='+tag,'--ignore-scripts',...(process.env.GITHUB_ACTIONS==='true'||args.includes('--provenance')?['--provenance']:[]),'--registry=https://registry.npmjs.org'],{stdio:'inherit',timeout:600000});
 const actual=JSON.parse(npm(['view',item.name+'@'+report.version,'dist.integrity','--json','--registry=https://registry.npmjs.org']).stdout);
 if(actual!==item.integrity)throw new Error('Registry integrity mismatch: '+item.name);
 console.log((exists?'Already verified: ':'Published and verified: ')+item.name);
 events.push({name:item.name,version:report.version,integrity:actual,action:exists?'already-present-same-integrity':requireOidc?'published-via-oidc-job':'published-with-local-session'});
 if(requireOidc)fs.writeFileSync(path.join(bundle,'publish-result.json'),JSON.stringify({version:report.version,sourceCommit:report.sourceCommit,authenticationMode:'oidc-only',events,actualPublishCount:events.filter(e=>e.action==='published-via-oidc-job').length,allFivePackagesExercisedOidc:events.length===5&&events.every(e=>e.action==='published-via-oidc-job')},null,2)+'\n');
}
if(requireOidc&&!events.some(e=>e.action==='published-via-oidc-job'))throw new Error('OIDC_NOT_EXERCISED: all versions already exist. This run did not verify trusted publishing or validate a new trust configuration.');
for(const item of selected){
 let tags=JSON.parse(npm(['view',item.name,'dist-tags','--json','--registry=https://registry.npmjs.org']).stdout);
 if(tags[tag]!==report.version){npm(['dist-tag','add',item.name+'@'+report.version,tag,'--registry=https://registry.npmjs.org'],{stdio:'inherit',timeout:600000});tags=JSON.parse(npm(['view',item.name,'dist-tags','--json','--registry=https://registry.npmjs.org']).stdout);}
 if(tags[tag]!==report.version)throw new Error('Dist-tag mismatch: '+item.name);
}
console.log((only?'Selected package verified: '+only+'. ':'All platform packages and entry verified. ')+report.version+' uses '+tag+'. latest was not changed.');
