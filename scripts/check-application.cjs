'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),compiler=path.resolve(process.argv[2]),output=path.resolve(process.argv[3]);
const reader=path.resolve('target/navigation-tools/chm-nav-dump'+(process.platform==='win32'?'.exe':''));
fs.mkdirSync(output,{recursive:true});
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const scenarios=[{name:'small',count:2,extra:['--include','*ORDER*']},{name:'scale',count:1000,extra:['--mock-tables','1000','--mock-columns','20']}];
for(const scenario of scenarios){
 const dir=path.join(output,scenario.name+' 中文 output');
 const started=Date.now();
 const transport=require('../npm/argv.cjs')(['export','--mock','--compiler','chmcmd','--compiler-path',compiler,'--out',dir,'--timeout','600','--json',...scenario.extra]);
 const result=spawnSync(java,['-jar',path.join(root,'target/db2chm.jar'),...transport.args],{encoding:'utf8',env:{...process.env,...transport.env},timeout:620000,maxBuffer:4*1024*1024});
 fs.writeFileSync(path.join(output,scenario.name+'-stderr.txt'),result.stderr||'');
 if(result.error)throw result.error;
 let body;try{body=JSON.parse(result.stdout);}catch{throw new Error('CLI stdout is not JSON: '+result.stdout);}
 if(result.status!==0||!body.ok||body.data.tableCount!==scenario.count)throw new Error(JSON.stringify(body));
 const files=fs.readdirSync(dir).filter(f=>/\.(html|css)$/.test(f)).sort().map(file=>({file,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(dir,file))).digest('hex')}));
 if(files.filter(f=>f.file.startsWith('table_')).length!==scenario.count)throw new Error('Incorrect table page count');
 for(const project of ['contents.hhc','index.hhk']){
  const text=fs.readFileSync(path.join(dir,project),'utf8');
  const count=(text.match(/name="Local"/g)||[]).length;
  if(count!==scenario.count+(project==='contents.hhc'?2:0))throw new Error('TOC/index source count mismatch');
 }
 const hhp=fs.readFileSync(path.join(dir,'database.hhp'),'utf8');
 if(!hhp.includes('Full-text search=Yes')||!hhp.includes('Binary TOC=Yes'))throw new Error('Required project options missing');
 if(scenario.name==='scale'&&!fs.readFileSync(path.join(dir,'table_1000.html'),'utf8').includes('FIXTURE_20'))throw new Error('Scale fixture must have 20 columns');
 require('./check-navigation.cjs').inspect(dir,reader);
 fs.writeFileSync(path.join(dir,'verification.json'),JSON.stringify({scenario:scenario.name,elapsedMs:Date.now()-started,tableCount:scenario.count,columns:scenario.name==='scale'?20:null,cli:body,files,viewerInteraction:'pending'},null,2)+'\n');
}
