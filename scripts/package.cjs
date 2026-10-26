#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),npm=require('./npm-command.cjs');
const root=path.resolve(__dirname,'..'),config=require('../release/config.json'),argv=process.argv.slice(2);
const prototype=argv.includes('--prototype'),single=argv.includes('--platform')?argv[argv.indexOf('--platform')+1]:null;
const targets=single?config.platforms.filter(p=>p.id===single):config.platforms;
if(!targets.length)throw new Error('Unknown platform');
const buildBase=path.join(root,'target');fs.mkdirSync(buildBase,{recursive:true});
const build=fs.mkdtempSync(path.join(buildBase,'npm-build-')),output=path.join(buildBase,'npm');fs.mkdirSync(output,{recursive:true});
const jar=path.join(root,'target/db2chm.jar');
if(!fs.existsSync(jar))throw new Error('Build target/db2chm.jar first');
const digest=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const help=path.join(root,'src/main/resources/cli-help.txt'),packages=[];
function copy(source,destination){fs.mkdirSync(path.dirname(destination),{recursive:true});fs.copyFileSync(source,destination);}
function pack(dir,pkg){
 fs.writeFileSync(path.join(dir,'package.json'),JSON.stringify(pkg,null,2)+'\n');
 const r=JSON.parse(npm(['pack',dir,'--json','--ignore-scripts','--pack-destination',output,'--cache',path.join(buildBase,'npm-cache')],{cwd:root}).stdout)[0];
 const tarball=path.join(output,r.filename);packages.push({name:pkg.name,version:pkg.version,tarball,bytes:fs.statSync(tarball).size,unpackedBytes:r.unpackedSize,sha256:digest(tarball),platform:pkg.db2chmPlatform||null,manifest:pkg});
}
const base={version:config.version,license:'UNLICENSED',private:prototype,engines:{node:'>=22'},bin:{db2chm:'bin/db2chm.cjs'},files:['bin/','lib/','native/','help.txt','THIRD_PARTY_NOTICES.md','README.md'],publishConfig:{access:'public'}};
for(const platform of targets){
 const dir=path.join(build,platform.id);fs.mkdirSync(dir,{recursive:true});
 copy(jar,path.join(dir,'lib/db2chm.jar'));copy(help,path.join(dir,'help.txt'));copy(path.join(root,'npm/launcher.cjs'),path.join(dir,'bin/db2chm.cjs'));
 copy(path.join(root,'npm/info.cjs'),path.join(dir,'bin/info.cjs'));
 fs.chmodSync(path.join(dir,'bin/db2chm.cjs'),0o755);
 let notices='JAR includes FreeMarker 2.3.32 (Apache-2.0) and Gson 2.10.1 (Apache-2.0). See their LICENSE/NOTICE resources in the JAR.\nJava and vendor JDBC drivers are not bundled.\n';
 if(!prototype){
   const nativeRoot=path.join(root,'target/native',platform.id),manifestPath=path.join(nativeRoot,'manifest.json');
   if(!fs.existsSync(manifestPath))throw new Error('Missing verified native manifest for '+platform.id);
   const manifest=JSON.parse(fs.readFileSync(manifestPath,'utf8'));
   if(manifest.redistributionApproved!==true||!manifest.sourceCommit||!manifest.files?.length)throw new Error('Native redistribution/source approval missing for '+platform.id);
   let executable=false;
   for(const item of manifest.files){
     const source=path.resolve(nativeRoot,item.path);
     if(!source.startsWith(nativeRoot+path.sep)||digest(source)!==item.sha256)throw new Error('Invalid native path/checksum');
     const destination=path.join(dir,'native',item.path);copy(source,destination);
     if(item.path===platform.compiler){executable=true;fs.chmodSync(destination,0o755);}
   }
   if(!executable)throw new Error('Native compiler payload missing for '+platform.id);
   notices+='\n'+manifest.licenseNotice+'\nSource: '+manifest.sourceUrl+'\nCommit: '+manifest.sourceCommit+'\n';
 }else notices+='\nPRIVATE PROTOTYPE: no bundled CHM compiler. Supply --compiler-path for experiments. Not a release candidate.\n';
 fs.writeFileSync(path.join(dir,'THIRD_PARTY_NOTICES.md'),notices);
 fs.writeFileSync(path.join(dir,'README.md'),'# '+config.name+'\n\nRequires Node >=22 and a preinstalled Java runtime. Use db2chm --help and db2chm doctor --json.\n'+(prototype?'\nPrivate prototype; compiler distribution gates have not passed.\n':''));
 pack(dir,{...base,name:'@'+config.scope+'/'+config.name+'-'+platform.id,os:[platform.os],cpu:[platform.cpu],db2chmPlatform:platform.id});
}
if(!single){
 const dir=path.join(build,'entry');fs.mkdirSync(dir,{recursive:true});
 copy(help,path.join(dir,'help.txt'));copy(path.join(root,'npm/entry.cjs'),path.join(dir,'bin/db2chm.cjs'));fs.chmodSync(path.join(dir,'bin/db2chm.cjs'),0o755);
 copy(path.join(root,'npm/info.cjs'),path.join(dir,'bin/info.cjs'));
 const optionalDependencies=Object.fromEntries(config.platforms.map(p=>['@'+config.scope+'/'+config.name+'-'+p.id,config.version]));
 pack(dir,{...base,name:'@'+config.scope+'/'+config.name,files:['bin/','help.txt','README.md'],optionalDependencies});
}
const entry=packages.find(p=>!p.platform);
const sizes=packages.filter(p=>p.platform).map(p=>({platform:p.platform,platformBytes:p.bytes,entryBytes:entry?.bytes||0,totalDownloadBytes:p.bytes+(entry?.bytes||0),belowLimit:p.bytes+(entry?.bytes||0)<config.maxDownloadBytes}));
if(sizes.some(p=>!p.belowLimit))throw new Error('Installation download budget exceeded');
const report={version:config.version,prototype,releaseEligible:!prototype,maxDownloadBytes:config.maxDownloadBytes,jarSha256:digest(jar),packages,sizes};
fs.writeFileSync(path.join(output,single?'report-'+single+'.json':'report.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
