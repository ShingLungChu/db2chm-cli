#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),args=process.argv.slice(2);
const options={types:'oracle,dm',configDir:path.join(root,'.local/databases'),compiler:null,compilerPath:null,include:'*'};
for(let i=0;i<args.length;i++){
 const key={'--types':'types','--config-dir':'configDir','--compiler':'compiler','--compiler-path':'compilerPath','--include':'include'}[args[i]];
 if(!key||!args[i+1]||args[i+1].startsWith('--'))throw new Error('Unknown option or missing value');
 options[key]=args[++i];
}
const types=options.types.split(',');
if(!types.length||types.some(t=>!['oracle','dm','vastbase'].includes(t))||new Set(types).size!==types.length)throw new Error('Select oracle,dm,vastbase without duplicates');
const configDir=path.resolve(options.configDir),versions=JSON.parse(fs.readFileSync(path.join(configDir,'versions.json'),'utf8'));
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const prepared=types.map(type=>{
 const file=path.join(configDir,type+'.json'),config=JSON.parse(fs.readFileSync(file,'utf8'));
 if(JSON.stringify(config).includes('REPLACE_')||!versions[type]?.serverVersion||versions[type]?.accountPermissionsConfirmed!==true)throw new Error(type+': fill connection, server version and account confirmation first');
 if(Object.hasOwn(config,'password'))throw new Error('Use local password environment variables instead of config passwords');
 const driver=path.resolve(path.dirname(file),config.driverJar||'');
 if(!config.driverJar||!fs.existsSync(driver)||!fs.statSync(driver).isFile())throw new Error(type+': configure a readable trusted driverJar file first');
 const secret=process.env['DB2CHM_'+type.toUpperCase()+'_PASSWORD'];
 if(secret===undefined)throw new Error('Set local DB2CHM_'+type.toUpperCase()+'_PASSWORD');
 return {type,file,secret,config,driver};
});
const destination=fs.mkdtempSync(path.join(root,'target/database-validation-')),summary=[];
let failed=false;
for(const item of prepared){
 const commands=[['tables','list'],['export','--max-tables','3','--out',path.join(destination,item.type)]];
 const results=[];
 for(const command of commands){
  if(command[0]==='export')command.push(...(options.compiler?['--compiler',options.compiler]:['--html-only']),...(options.compilerPath?['--compiler-path',options.compilerPath]:[]));
  const env={...process.env,DB2CHM_PASSWORD:item.secret};
  for(const type of ['ORACLE','DM','VASTBASE'])delete env['DB2CHM_'+type+'_PASSWORD'];
  const transport=require('../npm/argv.cjs')([...command,'--config',item.file,'--include',options.include,'--timeout','120','--json']);
  const run=spawnSync(java,['-jar',path.join(root,'target/db2chm.jar'),...transport.args],{encoding:'utf8',env:{...env,...transport.env},timeout:130000,maxBuffer:8*1024*1024});
  if(run.error){results.push({command:command[0],ok:false,error:'PROCESS_FAILED'});break;}
  let body;try{body=JSON.parse(run.stdout);}catch{body={ok:false,error:{code:'INVALID_CLI_OUTPUT'}};}
  // Full metadata output stays local; credentials and raw database exceptions are not persisted.
  fs.writeFileSync(path.join(destination,item.type+'-'+command[0]+'.json'),JSON.stringify(body,null,2)+'\n');
  results.push({command:command[0],ok:run.status===0&&body.ok,exitCode:run.status,tableCount:body.data?.tableCount,error:body.error?.code||null});
  if(run.status!==0||!body.ok)break;
 }
 const ok=results.length===2&&results.every(r=>r.ok)&&results.every(r=>r.tableCount>0);
 failed ||= !ok;
 const driver=item.driver;
 summary.push({type:item.type,ok,serverVersion:versions[item.type].serverVersion,driverSha256:crypto.createHash('sha256').update(fs.readFileSync(driver)).digest('hex'),platform:process.platform+'-'+process.arch,results,chmRequested:!!options.compiler,databaseAudit:'pending',viewerInteraction:'pending'});
}
fs.writeFileSync(path.join(destination,'summary.json'),JSON.stringify(summary,null,2)+'\n');
console.log(JSON.stringify({ok:!failed,outputDir:destination,results:summary},null,2));process.exitCode=failed?1:0;
