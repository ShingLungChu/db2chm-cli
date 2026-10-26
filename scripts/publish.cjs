#!/usr/bin/env node
'use strict';
const path=require('node:path'),verify=require('./verify-release.cjs'),npm=require('./npm-command.cjs');
const args=process.argv.slice(2),bundle=args[0]||'target/npm',tag=args[1]||'beta';
if(!['beta','next'].includes(tag))throw new Error('Only beta/next publishing is enabled; promote latest after registry acceptance');
const {report,packages}=verify(bundle,path.join(bundle,'acceptance.json'));
if(!args.includes('--publish')){console.log('Release verified; --publish is required to write to npm');process.exit(0);}
for(const item of packages.sort((a,b)=>Number(!a.platform)-Number(!b.platform))){
 let exists=false;
 try{
  const integrity=JSON.parse(npm(['view',item.name+'@'+report.version,'dist.integrity','--json','--registry=https://registry.npmjs.org']).stdout);
  if(integrity!==item.integrity)throw new Error('Published version has different bytes: '+item.name);
  exists=true;
 }catch(error){if(!error.message.includes('E404'))throw error;}
 if(!exists)npm(['publish',item.tarball,'--access=public','--tag='+tag,'--provenance','--registry=https://registry.npmjs.org']);
 const actual=JSON.parse(npm(['view',item.name+'@'+report.version,'dist.integrity','--json','--registry=https://registry.npmjs.org']).stdout);
 if(actual!==item.integrity)throw new Error('Registry integrity mismatch: '+item.name);
 console.log((exists?'Already verified: ':'Published and verified: ')+item.name);
}
for(const item of packages){
 npm(['dist-tag','add',item.name+'@'+report.version,tag,'--registry=https://registry.npmjs.org']);
 const tags=JSON.parse(npm(['view',item.name,'dist-tags','--json','--registry=https://registry.npmjs.org']).stdout);
 if(tags[tag]!==report.version)throw new Error('Dist-tag mismatch: '+item.name);
}
console.log('All platform packages and entry use '+report.version+' on '+tag+'. latest was not changed.');
