#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),pkg=require(path.join(root,'package.json')),args=process.argv.slice(2),json=args.includes('--json');
if(!require('./info.cjs')(args,pkg,root)){
 const id=process.platform+'-'+process.arch,name=pkg.name+'-'+id;
 const supported=Object.hasOwn(pkg.optionalDependencies||{},name);
 let modulePath;try{if(supported)modulePath=require.resolve(name+'/bin/db2chm.cjs');}catch{}
 if(!modulePath){const code=supported?'PLATFORM_PACKAGE_MISSING':'UNSUPPORTED_PLATFORM';const message=supported?'The platform dependency is missing. Reinstall with optional dependencies enabled, or install '+name+'.':'Unsupported operating system or CPU.';if(json)console.log(JSON.stringify({schemaVersion:1,ok:false,command:args[0],data:null,error:{code,message}}));else console.error(code+': '+message);process.exitCode=3;}
 else require(modulePath);
}
