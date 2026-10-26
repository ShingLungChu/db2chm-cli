'use strict';
module.exports=function transport(args){
 const encoded=Buffer.from(JSON.stringify(args),'utf8').toString('base64');
 if(encoded.length>24000||args.length>512)throw new Error('Arguments exceed the supported launcher limit');
 return {args:['--launcher-argv',...(args.includes('--json')?['--json']:[])],env:{DB2CHM_ARGV64:encoded}};
};
module.exports.forLaunch=function(args,cwd){
 const path=require('node:path'),values=[...args];
 for(let i=0;i<values.length;i++)if(['--config','--driver-jar','--compiler-path','--out'].includes(values[i])&&values[i+1]&&!values[i+1].startsWith('--'))values[++i]=path.resolve(cwd,values[i]);
 const exporting=values[0]==='export'||(values[0]?.startsWith('-')&&!values.includes('--help')&&!values.includes('--version'));
 if(exporting&&!values.includes('--out'))values.push('--out',path.resolve(cwd,'output'));
 return module.exports(values);
};
