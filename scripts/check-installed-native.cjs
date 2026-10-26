#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),{spawnSync}=require('node:child_process'),npm=require('./npm-command.cjs');
const root=path.resolve(__dirname,'..'),id=process.platform+'-'+process.arch;
const report=JSON.parse(fs.readFileSync(path.join(root,'target/npm/report-'+id+'.json'),'utf8')),platform=report.packages.find(p=>p.platform===id);
if(!report.nativePrototype||!platform)throw new Error('Expected a private native platform prototype');
const workspace=fs.mkdtempSync(path.join(root,'target/installed native 中文 ')),prefix=path.join(workspace,'prefix');
try{
 npm(['install','--global','--prefix',prefix,platform.tarball,'--offline','--ignore-scripts','--no-audit','--no-fund','--cache',path.join(workspace,'cache')]);
 const home=path.join(prefix,process.platform==='win32'?'node_modules':'lib/node_modules',platform.name);
 const env={...process.env};delete env.DB2CHM_COMPILER;delete env.DB2CHM_HHC;delete env.DB2CHM_PASSWORD;
 if(process.platform==='win32'){
  for(const key of Object.keys(env))if(['programfiles','programfiles(x86)'].includes(key.toLowerCase()))delete env[key];
  env.PATH=path.join(env.SystemRoot||'C:\\Windows','System32');
 }
 const output=path.resolve(process.argv[2]),run=spawnSync(process.execPath,[path.join(home,'bin/db2chm.cjs'),'export','--mock','--include','*ORDER*','--out',output,'--json'],{env,encoding:'utf8',timeout:30000});
 if(run.error)throw run.error;
 const body=JSON.parse(run.stdout);
 if(run.status!==0||!body.ok||body.data.tableCount!==2)throw new Error(JSON.stringify(body));
 if(!body.data.compiler.path.startsWith(path.join(home,'native')+path.sep))throw new Error('Export used an external compiler instead of the installed payload');
 const selectionChecks={bundledCompiler:true};
 if(process.platform==='win32'){
  if(body.data.compiler.kind!=='chmcmd')throw new Error('Windows auto mode did not select bundled chmcmd without HHC');
  const doctor=(extra=[])=>{
   const result=spawnSync(process.execPath,[path.join(home,'bin/db2chm.cjs'),'doctor',...extra,'--json'],{env,encoding:'utf8',timeout:10000});
   if(result.error)throw result.error;
   return {status:result.status,body:JSON.parse(result.stdout)};
  };
  const explicit=doctor(['--compiler','hhc']);
  if(explicit.status!==3||explicit.body.data.checks.compiler.code!=='COMPILER_NOT_FOUND')throw new Error('Explicit HHC selection unexpectedly fell back');
  const fixture=path.join(home,'native','hhc.exe');
  // Resolution-only fixture; doctor never executes compiler files.
  fs.writeFileSync(fixture,'resolution-only fixture');
  try{
   const preferred=doctor();
   if(preferred.status!==0||preferred.body.data.checks.compiler.kind!=='hhc'||preferred.body.data.checks.compiler.path!==fixture)throw new Error('Installed HHC was not preferred in auto mode');
  }finally{fs.unlinkSync(fixture);}
  selectionChecks.hhcPreferred=true;selectionChecks.explicitHhcNoFallback=true;
 }
 const files=fs.readdirSync(output).filter(f=>/\.(html|css)$/.test(f)).sort().map(file=>({file,sha256:require('node:crypto').createHash('sha256').update(fs.readFileSync(path.join(output,file))).digest('hex')}));
 fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify({scenario:'installed',tableCount:2,cli:body,selectionChecks,files,viewerInteraction:'pending'},null,2)+'\n');
 console.log(JSON.stringify({platform:id,totalDownloadBytes:report.sizes[0].totalDownloadBytes,compiler:body.data.compiler,prototype:true}));
}finally{fs.rmSync(workspace,{recursive:true,force:true});}
