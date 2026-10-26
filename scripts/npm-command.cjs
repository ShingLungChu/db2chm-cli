'use strict';
const fs=require('node:fs'),path=require('node:path'),{spawnSync}=require('node:child_process');
module.exports=function npm(args,options={}){
 const candidates=[process.env.npm_execpath,path.join(path.dirname(process.execPath),'node_modules/npm/bin/npm-cli.js'),path.resolve(path.dirname(process.execPath),'../lib/node_modules/npm/bin/npm-cli.js')].filter(Boolean);
 const cli=candidates.find(p=>fs.existsSync(p)&&p.endsWith('.js'));
 const result=spawnSync(cli?process.execPath:(process.platform==='win32'?'npm.cmd':'npm'),cli?[cli,...args]:args,{encoding:'utf8',timeout:120000,...options});
 if(result.error)throw result.error;
 if(result.status!==0)throw new Error('npm '+args[0]+' failed:\n'+result.stderr+'\n'+result.stdout);
 return result;
};
