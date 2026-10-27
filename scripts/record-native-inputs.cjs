#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{execFileSync}=require('node:child_process');
const digest=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
function record(out,compiler,sourceRoot,platform){
 out=path.resolve(out);compiler=fs.realpathSync(compiler);sourceRoot=fs.realpathSync(sourceRoot);
 const logFile=path.join(out,'build.log'),log=fs.readFileSync(logFile,'utf8');
 const query=flag=>execFileSync(compiler,[flag],{encoding:'utf8'}).trim();
 const compilerRevision=query('-iW'),compilerVersion=query('-iV'),compilerDate=query('-iD');
 const sourceCommit=execFileSync('git',['-C',sourceRoot,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
 const inputs=new Map();
 for(const match of log.matchAll(/^\(([^)]+)\)\s+PPU Name:\s*(.+)$/gm)){
  const unit=match[1].trim(),file=fs.realpathSync(match[2].trim()),name=path.basename(file),local=file.startsWith(fs.realpathSync(out)+path.sep);
  const objects=[file.replace(/\.ppu$/i,'.o'),file.replace(/\.ppu$/i,'.a'),path.join(path.dirname(file),'libp'+name.replace(/\.ppu$/i,'.a'))].filter(f=>fs.existsSync(f));
  inputs.set(unit,{unit,origin:local?'rebuilt-utility-unit':'bootstrap-precompiled-unit',packageDirectory:path.basename(path.dirname(file)),ppu:{name,bytes:fs.statSync(file).size,sha256:digest(file)},objectCandidates:objects.map(f=>({name:path.basename(f),bytes:fs.statSync(f).size,sha256:digest(f)}))});
 }
 if(!inputs.size)throw new Error('Compiler trace contains no PPU input identities; build with -viu');
 const compiledSources=[];
 for(const match of log.matchAll(/^Compiling\s+(.+)$/gm)){
  const reported=path.resolve(match[1].trim());
  if(!fs.existsSync(reported))continue;
  const file=fs.realpathSync(reported);
  if(file.startsWith(sourceRoot+path.sep))compiledSources.push({path:path.relative(sourceRoot,file).replace(/\\/g,'/'),sha256:digest(file)});
 }
 if(!compiledSources.some(s=>s.path==='packages/chm/src/chmcmd.lpr'))throw new Error('Compiler trace does not identify the pinned utility entry source');
 const windows=platform==='win32-x64';
 if(windows&&compilerRevision!=='3.3.1-21298-g814ece0205')throw new Error('Unexpected Windows bootstrap source revision');
 const bootstrapSourceCommit=windows?'814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7':'0d122c49534b480be9284c21bd60b53d99904346';
 const result={schemaVersion:1,platform,utilitySourceCommit:sourceCommit,compiler:{version:compilerVersion,revision:compilerRevision,date:compilerDate,cpu:query('-iTP'),os:query('-iTO'),executableSha256:digest(compiler),upstreamSourceCommit:bootstrapSourceCommit,sourceIdentity:windows?'revision-reported-by-pinned-bootstrap':'upstream-release-baseline-distro-patches-not-confirmed'},traceSha256:digest(logFile),compilationInputs:[...inputs.values()].sort((a,b)=>a.unit.localeCompare(b.unit,'en')),compiledUtilitySources:compiledSources.sort((a,b)=>a.path.localeCompare(b.path,'en')),patches:['chmcmd-fpc322.patch','chmcmd-encoding.patch'].map(name=>({name,sha256:digest(path.join(out,name))})),sourceDistributionReview:'pending',limits:['PPU loading is a compilation-input inventory, not proof that every loaded unit contributed linked code.','Object candidates are identified by location; this is not a linker-map claim.','Compiler revision does not prove precompiled library source identity; per-unit source/CRC mapping and distro patches still require review.']};
 fs.writeFileSync(path.join(out,'build-provenance.json'),JSON.stringify(result,null,2)+'\n');
 return result;
}
module.exports=record;
if(require.main===module){try{const r=record(...process.argv.slice(2));console.log(JSON.stringify({platform:r.platform,revision:r.compiler.revision,compilationInputs:r.compilationInputs.length,compiledUtilitySources:r.compiledUtilitySources.length,sourceDistributionReview:r.sourceDistributionReview}));}catch(e){console.error(e.message);process.exitCode=1;}}
