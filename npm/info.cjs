'use strict';
const fs=require('node:fs'),path=require('node:path');
module.exports=function info(args,pkg,root){
 const tokens=new Set(['help','version','--help','-h','--version','-V','--json']);
 if(!args.every(a=>tokens.has(a)))return false;
 const wantsHelp=args.some(a=>['help','--help','-h'].includes(a)),wantsVersion=args.some(a=>['version','--version','-V'].includes(a));
 if(wantsHelp&&wantsVersion)return false;
 if(args.length&&!wantsHelp&&!wantsVersion)return false;
 // Leave duplicate/conflicting forms to the strict Java parser.
 if(args.filter(a=>a==='--json').length>1||args.filter(a=>a!=='--json').length>1)return false;
 const command=wantsVersion?'version':'help',value=wantsVersion?pkg.version:fs.readFileSync(path.join(root,'help.txt'),'utf8');
 if(args.includes('--json'))console.log(JSON.stringify({schemaVersion:1,ok:true,command,data:{value},error:null}));else console.log(value);
 return true;
};
