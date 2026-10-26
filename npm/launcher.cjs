#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),{spawn,spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),pkg=require(path.join(root,'package.json')),args=process.argv.slice(2);
const json=args.includes('--json');
function emit(ok,data,error,code){process.stdout.write(JSON.stringify({schemaVersion:1,ok,command:args[0]||'help',data,error})+'\n');process.exitCode=code;}
function fail(code,message){if(json)emit(false,null,{code,message},3);else{console.error(code+': '+message);process.exitCode=3;}}
if(!require('./info.cjs')(args,pkg,root)){
  const expected=pkg.db2chmPlatform;
  if(expected && expected!==process.platform+'-'+process.arch)fail('UNSUPPORTED_PLATFORM','Install the package matching this operating system and CPU.');
  else {
    const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
    let transport;try{transport=require('./argv.cjs').forLaunch(args,process.cwd());}catch{if(json)emit(false,null,{code:'INVALID_ARGUMENT',message:'Arguments exceed the supported launcher limit.'},2);else{console.error('INVALID_ARGUMENT: Arguments exceed the supported launcher limit.');process.exitCode=2;}return;}
    const home64=Buffer.from(root,'utf8').toString('base64');
    const child=spawn(java,['-Dfile.encoding=UTF-8','-Ddb2chm.home64='+home64,'-jar','db2chm.jar','--cli',...transport.args],{cwd:path.join(root,'lib'),stdio:'inherit',shell:false,detached:process.platform!=='win32',env:{...process.env,...transport.env}});
    let stopping=false;
    function stop(signal){
      if(stopping)return;stopping=true;
      if(process.platform==='win32'){
        const killer=path.join(process.env.SystemRoot||'C:\\Windows','System32','taskkill.exe');
        spawnSync(killer,['/PID',String(child.pid),'/T','/F'],{stdio:'ignore',shell:false});
      }else{try{process.kill(-child.pid,signal);}catch{}}
      process.exitCode=130;
    }
    process.on('SIGINT',()=>stop('SIGINT'));process.on('SIGTERM',()=>stop('SIGTERM'));
    child.on('error',()=>fail('JAVA_NOT_FOUND','A usable Java runtime was not found. Check JAVA_HOME or PATH.'));
    child.on('exit',(code,signal)=>{process.exitCode=stopping?130:(code??(signal?130:3));});
  }
}
