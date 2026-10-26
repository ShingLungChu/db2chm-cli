'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),http=require('node:http'),crypto=require('node:crypto'),{spawn,spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),reportPath=path.join(root,'target/npm/report.json');
function run(command,args,options={}){
 return new Promise((resolve,reject)=>{const p=spawn(command,args,{cwd:root,stdio:['ignore','pipe','pipe'],...options});let out='',err='';p.stdout.on('data',b=>out+=b);p.stderr.on('data',b=>err+=b);const timer=setTimeout(()=>{p.kill();reject(new Error('npm installation timed out'));},90000);p.on('error',reject);p.on('close',code=>{clearTimeout(timer);if(code)reject(new Error(err+out));else resolve({out,err});});});
}
test('final-shape tarballs install through the entry package and download only this platform',async()=>{
 assert.ok(fs.existsSync(reportPath),'Run pack:prototype before package acceptance checks');
 const report=JSON.parse(fs.readFileSync(reportPath)),id=process.platform+'-'+process.arch;
 const selected=report.packages.find(p=>p.platform===id),entry=report.packages.find(p=>!p.platform);
 assert.ok(selected&&entry);
 const workspace=fs.mkdtempSync(path.join(root,'target/npm 安装-'));
 const requested=[];
 const server=http.createServer((req,res)=>{
  const route=decodeURIComponent(req.url.split('?')[0]);
  if(route.startsWith('/tar/')){
   const index=Number(route.slice(5).replace('.tgz','')),item=report.packages[index];
   if(!item){res.writeHead(404);return res.end();}
   requested.push(item.name);res.writeHead(200,{'content-type':'application/octet-stream'});return fs.createReadStream(item.tarball).pipe(res);
  }
  const item=report.packages.find(p=>'/'+p.name===route);
  if(!item){res.writeHead(404);return res.end('{}');}
  const bytes=fs.readFileSync(item.tarball),index=report.packages.indexOf(item);
  const metadata={...item.manifest,dist:{tarball:base+'/tar/'+index+'.tgz',shasum:crypto.createHash('sha1').update(bytes).digest('hex'),integrity:'sha512-'+crypto.createHash('sha512').update(bytes).digest('base64')}};
  res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify({name:item.name,'dist-tags':{latest:item.version},versions:{[item.version]:metadata}}));
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const base='http://127.0.0.1:'+server.address().port;
 const npmCli=[process.env.npm_execpath,path.join(path.dirname(process.execPath),'node_modules/npm/bin/npm-cli.js'),path.resolve(path.dirname(process.execPath),'../lib/node_modules/npm/bin/npm-cli.js')].filter(Boolean).find(p=>fs.existsSync(p)&&p.endsWith('.js'));
 assert.ok(npmCli,'Cannot locate npm CLI');
 const prefix=path.join(workspace,'prefix'),cache=path.join(workspace,'cache'),config=path.join(workspace,'empty.npmrc');fs.writeFileSync(config,'');
 try{
  await run(process.execPath,[npmCli,'install','--global','--prefix',prefix,entry.tarball,'--registry',base,'--userconfig',config,'--cache',cache,'--ignore-scripts','--no-audit','--no-fund']);
  assert.deepEqual(requested,[selected.name],'An unsupported platform tarball was downloaded');
  const installed=path.join(prefix,process.platform==='win32'?'node_modules':'lib/node_modules',entry.name,'bin/db2chm.cjs');
  const version=spawnSync(process.execPath,[installed,'--version','--json'],{encoding:'utf8',env:{...process.env,JAVA_HOME:path.join(workspace,'missing-java')}});
  assert.equal(version.status,0,version.stderr);assert.equal(JSON.parse(version.stdout).data.value,report.version);
  const target=path.join(workspace,'导出 output');
  const exportResult=spawnSync(process.execPath,[installed,'export','--mock','--include','BIZ_ORDER','--html-only','--out',target,'--json'],{encoding:'utf8',timeout:20000});
  assert.equal(exportResult.status,0,exportResult.stderr+exportResult.stdout);
  assert.equal(JSON.parse(exportResult.stdout).data.tableCount,1);
  assert.ok(fs.existsSync(path.join(target,'table_1.html')));
  const size=entry.bytes+selected.bytes;assert.ok(size<15000000);assert.equal(report.releaseEligible,false,'Prototype must not masquerade as a release');
  console.log('Actual local registry download bytes (entry + selected platform): '+size);
 }finally{await new Promise(resolve=>server.close(resolve));fs.rmSync(workspace,{recursive:true,force:true});}
});
