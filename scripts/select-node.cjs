'use strict';
const fs=require('node:fs'),path=require('node:path');
const major=process.argv[2];
if(process.versions.node.split('.')[0]===major)process.exit(0);
const cache=path.join(process.env.RUNNER_TOOL_CACHE,'node');
const versions=fs.readdirSync(cache).filter(n=>n.startsWith(major+'.')).sort((a,b)=>a.localeCompare(b,undefined,{numeric:true})).reverse();
let selected;
for(const version of versions){
 const arch=path.join(cache,version,process.arch);
 const bin=process.platform==='win32'?arch:path.join(arch,'bin');
 if(fs.existsSync(path.join(bin,process.platform==='win32'?'node.exe':'node'))){selected=bin;break;}
}
if(!selected)throw new Error('Requested Node major absent from runner tool cache: '+major);
fs.appendFileSync(process.env.GITHUB_PATH,selected+'\n');
