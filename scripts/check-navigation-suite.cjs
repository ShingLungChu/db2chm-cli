'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process'),{inspect}=require('./check-navigation.cjs');
const compiler=path.resolve(process.argv[2]),reader=path.resolve(process.argv[3]),out=path.resolve(process.argv[4]);
fs.mkdirSync(out,{recursive:true});
const valid=path.join(out,'chinese 中文 output');
const fixture=dir=>{const r=spawnSync(process.execPath,[path.join(__dirname,'create-navigation-fixture.cjs'),dir],{encoding:'utf8'});assert.equal(r.status,0,r.stderr);};
const compile=dir=>spawnSync(compiler,['--db2chm-encoding',path.join(dir,'database.hhp')],{cwd:dir,encoding:'utf8',timeout:30000});
fixture(valid);const run=compile(valid);assert.equal(run.status,0,run.stdout+run.stderr);
const navigation=inspect(valid,reader);
const files=fs.readdirSync(valid).filter(f=>/\.(html|css)$/.test(f)).sort().map(file=>({file,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(valid,file))).digest('hex')}));
fs.writeFileSync(path.join(valid,'verification.json'),JSON.stringify({scenario:'chinese',tableCount:4,navigation,files,viewerInteraction:'pending'},null,2)+'\n');
// Long Chinese keys require more than one index level; leaf iteration alone
// cannot catch corrupt index headers or unreachable keyword blocks.
const deep=path.join(out,'index-depth');
const deepFixture=spawnSync(process.execPath,[path.join(__dirname,'create-navigation-fixture.cjs'),deep,'中文多层关键字验证','200'],{encoding:'utf8'});
assert.equal(deepFixture.status,0,deepFixture.stderr);const deepRun=compile(deep);assert.equal(deepRun.status,0,deepRun.stdout+deepRun.stderr);
const deepNavigation=inspect(deep,reader);assert(deepNavigation.keywordDepth>=3,'Fixture must exercise at least three keyword levels');
for(const [name,mutate] of [
 ['unsupported',d=>{const f=path.join(d,'database.hhp');fs.appendFileSync(f,'');fs.writeFileSync(f,fs.readFileSync(f,'utf8').replace('Title=','Title=😀'));}],
 ['invalid-utf8',d=>{const f=path.join(d,'database.hhp'),s=fs.readFileSync(f,'utf8'),i=s.indexOf('Title=');fs.writeFileSync(f,Buffer.concat([Buffer.from(s.slice(0,i+6)),Buffer.from([0xff,0xfe]),Buffer.from(s.slice(i+6))]));}],
 ['wrong-lcid',d=>{const f=path.join(d,'database.hhp');fs.writeFileSync(f,fs.readFileSync(f,'utf8').replace('Language=0x804','Language=0x409'));}]
]){const dir=path.join(out,'rejected-'+name);fixture(dir);mutate(dir);const r=compile(dir);assert.notEqual(r.status,0,name+' must be rejected');assert.match(r.stdout+r.stderr,/DB2CHM_ENCODING:/,name+' must give an explicit encoding error');}
console.log(JSON.stringify({ok:true,navigation,deepNavigation,negativeCases:3}));
