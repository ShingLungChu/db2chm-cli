#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),crypto=require('node:crypto'),assert=require('node:assert/strict'),{spawn,spawnSync}=require('node:child_process'),artifactName=require('./artifact-name.cjs');
const baseline=require('../docs/evidence/personal-scope-acceptance-4de172f.json'),config=require('../release/config.json');
const digest=b=>crypto.createHash('sha256').update(b).digest('hex');
async function check(bundle,output,registryMode=false){
 bundle=path.resolve(bundle);output=path.resolve(output);if(fs.existsSync(output))throw new Error('Use a fresh installation result directory');
 const report=JSON.parse(fs.readFileSync(path.join(bundle,'report.json'),'utf8')),id=process.platform+'-'+process.arch;
 const selected=report.packages.find(p=>p.platform===id),entry=report.packages.find(p=>!p.platform);assert(selected&&entry);
 fs.mkdirSync(output,{recursive:true});const prefix=path.join(output,'prefix'),cache=path.join(output,'cache'),npmrc=path.join(output,'empty.npmrc');fs.writeFileSync(npmrc,'');
 const cli=[process.env.npm_execpath,path.join(path.dirname(process.execPath),'node_modules/npm/bin/npm-cli.js'),path.resolve(path.dirname(process.execPath),'../lib/node_modules/npm/bin/npm-cli.js')].filter(Boolean).find(p=>fs.existsSync(p));assert(cli);
 const requests=[];let server=null,registry='https://registry.npmjs.org';
 if(!registryMode){
  server=http.createServer((req,res)=>{try{
   const url=decodeURIComponent(req.url.split('?')[0]);
   if(url.startsWith('/tar/')){const item=report.packages.find(p=>artifactName(p.tarball)===url.slice(5));if(!item){res.writeHead(404);return res.end('{}');}requests.push(item.name);res.writeHead(200,{'content-type':'application/octet-stream'});return fs.createReadStream(path.join(bundle,artifactName(item.tarball))).pipe(res);}
   const item=report.packages.find(p=>'/'+p.name===url);if(!item){res.writeHead(404);return res.end('{}');}
   const bytes=fs.readFileSync(path.join(bundle,artifactName(item.tarball))),manifest=JSON.parse(require('node:child_process').execFileSync('tar',['-xOf',path.join(bundle,artifactName(item.tarball)),'package/package.json'],{encoding:'utf8'}));
   const value={...manifest,dist:{tarball:registry+'/tar/'+artifactName(item.tarball),integrity:'sha512-'+crypto.createHash('sha512').update(bytes).digest('base64'),shasum:crypto.createHash('sha1').update(bytes).digest('hex')}};
   res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify({name:item.name,'dist-tags':{latest:item.version,beta:item.version},versions:{[item.version]:value}}));
  }catch{res.writeHead(500);res.end('{}');}});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));registry='http://127.0.0.1:'+server.address().port;
 }
 try{
  if(registryMode)for(const item of [entry,selected]){
   const response=await fetch('https://registry.npmjs.org/'+encodeURIComponent(item.name)+'/'+report.version);assert(response.ok,'Published package metadata unavailable');
   const value=await response.json(),expected='sha512-'+crypto.createHash('sha512').update(fs.readFileSync(path.join(bundle,artifactName(item.tarball)))).digest('base64');assert.equal(value.dist.integrity,expected,'Published tarball integrity differs from accepted bytes');
  }
  const target=registryMode?entry.name+'@'+report.version:path.join(bundle,artifactName(entry.tarball));
  await new Promise((resolve,reject)=>{
   const child=spawn(process.execPath,[cli,'install','--global','--prefix',prefix,target,'--registry',registry,'--userconfig',npmrc,'--cache',cache,'--ignore-scripts','--no-audit','--no-fund'],{stdio:['ignore','pipe','pipe']});let stderr='';child.stdout.resume();child.stderr.on('data',b=>stderr+=b);
   const timer=setTimeout(()=>{child.kill();reject(new Error('Package installation timed out'));},180000);child.on('error',e=>{clearTimeout(timer);reject(e);});child.on('close',code=>{clearTimeout(timer);code?reject(new Error('Package installation failed: '+stderr)):resolve();});
  });
  if(!registryMode)assert.deepEqual(requests,[selected.name]);
  const moduleRoot=path.join(prefix,process.platform==='win32'?'node_modules':'lib/node_modules'),home=path.join(moduleRoot,selected.name),launcher=path.join(moduleRoot,entry.name,'bin/db2chm.cjs');
  const jarSha256=digest(fs.readFileSync(path.join(home,'lib/db2chm.jar'))),binary=path.join(home,'native',id==='win32-x64'?'chmcmd.exe':'chmcmd'),nativeSha256=digest(fs.readFileSync(binary));
  assert.equal(jarSha256,baseline.sharedJarSha256);assert.equal(nativeSha256,baseline.acceptedBaselinePayloadIdentity.platforms.find(p=>p.platform===id).nativeSha256);
  const version=spawnSync(process.execPath,[launcher,'--version','--json'],{encoding:'utf8'});assert.equal(version.status,0);assert.equal(JSON.parse(version.stdout).data.value,report.version);
  const document=path.join(output,'document 中文'),exported=spawnSync(process.execPath,[launcher,'export','--mock','--include','BIZ_ORDER','--compiler','chmcmd','--out',document,'--json'],{encoding:'utf8',timeout:60000});
  assert.equal(exported.status,0,exported.stderr);const body=JSON.parse(exported.stdout);assert(body.ok);assert.equal(body.data.tableCount,1);assert(body.data.compiler.path.startsWith(path.join(home,'native')+path.sep));
  assert.equal(fs.readFileSync(path.join(document,'database.chm')).subarray(0,4).toString(),'ITSF');
  const files=fs.readdirSync(document).filter(n=>/\.(html|css)$/.test(n)).map(file=>({file,sha256:digest(fs.readFileSync(path.join(document,file)))}));
  fs.writeFileSync(path.join(document,'verification.json'),JSON.stringify({scenario:registryMode?'registry':'candidate',tableCount:1,files},null,2)+'\n');
  const result={ok:true,platform:id,version:report.version,sourceCommit:report.sourceCommit,registryMode,reportSha256:digest(fs.readFileSync(path.join(bundle,'report.json'))),platformSha256:selected.sha256,entrySha256:entry.sha256,jarSha256,nativeSha256,totalDownloadBytes:entry.bytes+selected.bytes,filesVerified:files.length};
  assert(result.totalDownloadBytes<config.maxDownloadBytes);fs.writeFileSync(path.join(output,'installed.json'),JSON.stringify(result,null,2)+'\n');return result;
 }finally{if(server)await new Promise(resolve=>server.close(resolve));}
}
module.exports=check;
if(require.main===module)check(process.argv[2],process.argv[3],process.argv.includes('--registry')).then(r=>console.log(JSON.stringify(r))).catch(e=>{console.error('INSTALL_CHECK_FAILED: '+e.message);process.exitCode=1;});
