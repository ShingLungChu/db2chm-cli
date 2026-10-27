'use strict';
// Validate compiled structures, rather than accepting unchanged HHC/HHK payloads.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
const decoder=new TextDecoder('gbk',{fatal:true});
const entities=s=>s.replace(/&(amp|lt|gt|quot|apos|#\d+|#x[\da-f]+);/gi,(_,s)=>({amp:'&',lt:'<',gt:'>',quot:'"',apos:"'"}[s]??String.fromCodePoint(s[1].toLowerCase()==='x'?parseInt(s.slice(2),16):+s.slice(1))));
function sourceItems(text){return [...text.matchAll(/<OBJECT\s+type="text\/sitemap">([\s\S]*?)<\/OBJECT>/gi)].map(m=>{const a=Object.fromEntries([...m[1].matchAll(/<param name="([^"]+)" value="([^"]*)">/gi)].map(p=>[p[1],entities(p[2])]));return {name:a.Name,url:a.Local??null};});}
function inspect(dir,reader){
 const chm=path.join(dir,'database.chm'),sha=()=>crypto.createHash('sha256').update(fs.readFileSync(chm)).digest('hex'),before=sha();
 const dump=fs.mkdtempSync(path.join(dir,'navigation-streams-'));
 const p=spawnSync(reader,[chm,dump],{encoding:'utf8',timeout:30000});assert.equal(p.status,0,p.stderr||'Stream reader failed');
 const read=n=>fs.readFileSync(path.join(dump,n+'.bin'));
 const strings=read('strings'),system=read('system'),toc=read('tocidx'),topics=read('topics'),urltbl=read('urltbl'),urlstr=read('urlstr'),btree=read('keyword-btree');
 const u32=(b,p)=>b.readUInt32LE(p),u16=(b,p)=>b.readUInt16LE(p);
 const cstr=(b,p)=>{assert(p<b.length,'String offset out of bounds');const end=b.indexOf(0,p);assert(end>=p);return decoder.decode(b.subarray(p,end));};
 const topic=id=>{const p=id*16;assert(p+16<=topics.length,'Invalid topic ID');const q=u32(topics,p+8);return {name:cstr(strings,u32(topics,p+4)),url:cstr(urlstr,u32(urltbl,q+8)+8)};};
 const records=new Map();for(let p=4;p+4<=system.length;){const code=u16(system,p),len=u16(system,p+2);p+=4;assert(p+len<=system.length);records.set(code,system.subarray(p,p+len));p+=len;}
 assert.equal(u32(records.get(4),0),0x804,'SYSTEM LCID');
 const hhp=fs.readFileSync(path.join(dir,'database.hhp'),'utf8'),expectedTitle=/^Title=(.*)$/m.exec(hhp)[1].trim();
 assert.equal(cstr(records.get(3),0),expectedTitle,'Window title must decode as CP936');
 const actualToc=[],seen=new Set();
 function walk(p){while(p){assert(!seen.has(p),'TOC cycle');seen.add(p);assert(p>=4096&&p+20<=toc.length);const props=u32(toc,p+4),id=u32(toc,p+8);actualToc.push(props&8?topic(id):{name:cstr(strings,id),url:null});if(props&4)walk(u32(toc,p+20));p=u32(toc,p+16);}}
 walk(4096);
 const expectedToc=sourceItems(fs.readFileSync(path.join(dir,'contents.hhc'),'utf8'));
 assert.deepEqual(actualToc,expectedToc,'Every binary TOC label and target URL must match source');
 for(const [stream,expected] of [['contents',expectedToc],['index',sourceItems(fs.readFileSync(path.join(dir,'index.hhk'),'utf8'))]]){
  const stored=decoder.decode(read(stream));assert(/charset=GBK/i.test(stored),'Archived sitemap charset must match its CP936 bytes');
  assert.deepEqual(sourceItems(stored),expected,'Textual '+stream+' labels and targets must also match');
 }
 assert.equal(u32(btree,48),936,'Keyword codepage');assert.equal(u32(btree,52),0x804,'Keyword LCID');
 const keywords=[],size=u16(btree,4),visited=new Set();let block=0;
 while(block!==0xffffffff){assert(!visited.has(block),'BTree cycle');visited.add(block);const base=76+block*size;assert(base+size<=btree.length);let p=base+12;const n=u16(btree,base+2);for(let i=0;i<n;i++){const start=p;while(u16(btree,p)!==0)p+=2;const name=btree.subarray(start,p).toString('utf16le');p+=2;assert.equal(u16(btree,p),0,'See-also fixture unsupported');const count=u32(btree,p+12);p+=16;for(let j=0;j<count;j++){const target=topic(u32(btree,p));keywords.push({name,url:target.url});p+=4;}p+=8;assert(p<=base+size-u16(btree,base),'BTree entry exceeds used data');}block=u32(btree,base+8);}
 const sort=items=>items.map(p=>JSON.stringify(p)).sort();
 const expectedKeywords=sourceItems(fs.readFileSync(path.join(dir,'index.hhk'),'utf8'));
 assert.deepEqual(sort(keywords),sort(expectedKeywords),'Every Unicode keyword and target URL must match source');assert.equal(u32(btree,44),expectedKeywords.length);
 const lastLeaf=u32(btree,26),rootBlock=u32(btree,30),blockCount=u32(btree,38),depth=u16(btree,42),treeSeen=new Set(),leafSet=new Set();
 assert.equal(btree.length,76+size*blockCount);assert(depth>=1&&depth<=16,'BTree depth');
 function firstKey(n,level){const base=76+n*size,p=base+(level===1?12:8);let end=p;while(u16(btree,end))end+=2;return btree.subarray(p,end).toString('utf16le');}
 function tree(n,level){assert(n<blockCount&&!treeSeen.has(n),'Invalid or repeated index child');treeSeen.add(n);if(level===1){assert(n<=lastLeaf);leafSet.add(n);return;}assert(n>lastLeaf,'Index level points to a leaf too early');const base=76+n*size,nr=u16(btree,base+2),children=new Set([u32(btree,base+4)]);let p=base+8;for(let i=0;i<nr;i++){const start=p;while(u16(btree,p))p+=2;const key=btree.subarray(start,p).toString('utf16le');p+=2;assert.equal(u16(btree,p),0);const pairs=u32(btree,p+12);p+=16+4*pairs;const child=u32(btree,p);p+=4;assert.equal(firstKey(child,level-1),key,'Index separator must identify its child first key');children.add(child);}assert.equal(p,base+size-u16(btree,base));for(const child of children)tree(child,level-1);}
 tree(rootBlock,depth);assert.equal(treeSeen.size,blockCount,'Every BTree block must be reachable');assert.deepEqual([...leafSet].sort((a,b)=>a-b),[...visited].sort((a,b)=>a-b),'Tree and leaf chain must cover the same keywords');
 const search=read('search');assert.equal(u32(search,0x7a),936,'Search codepage');assert.equal(u32(search,0x7e),0x804,'Search LCID');
 const obj=read('objinst'),first=u32(obj,8),second=u32(obj,16);
 assert.equal(u32(obj,first+0x14),3,'Chinese search must not load the extra language-specific search object');
 assert.equal(u32(obj,12),2655);assert.equal(second,first+2655);assert.equal(obj.length,second+36);
 for(const [cp,lcid] of [[first+0x18,first+0x1c],[second+0x14,second+0x18]]){assert.equal(u32(obj,cp),936,'Search object codepage');assert.equal(u32(obj,lcid),0x804,'Search object LCID');}
 for(let b=0x81;b<=0xfe;b++){const p=first+80+b*10;assert.equal(u16(obj,p),0x0a,'CP936 lead-byte classification');assert.deepEqual([...obj.subarray(p+4,p+8)],[b,b,b,b],'CP936 lead byte must not use Western case mapping');}
 const words=fs.readFileSync(path.join(dump,'search-words.tsv'),'utf8').trim().split(/\r?\n/).filter(Boolean).map(line=>{const [hex,id]=line.split('\t');return {word:decoder.decode(Buffer.from(hex,'hex')),target:topic(+id).url};});
 const fixtureFile=path.join(dir,'navigation-expected.json');
 if(fs.existsSync(fixtureFile))for(const term of JSON.parse(fs.readFileSync(fixtureFile,'utf8')).search){
  const tokens=/^[\u3400-\u9fff]+$/.test(term)?[...term]:[term];
  for(const token of tokens)for(const page of expectedToc.filter(p=>p.url))assert(words.some(w=>w.word===token&&w.target===page.url),'Search token '+token+' must point to '+page.url);
 }
 assert.equal(sha(),before,'Inspection must not modify input CHM');
 const result={ok:true,chmSha256:before,tocEntries:actualToc.length,keywords:keywords.length,keywordDepth:depth,searchPostings:words.length,title:expectedTitle,codepage:936,lcid:'0x804',viewerInteraction:'pending'};
 fs.writeFileSync(path.join(dir,'navigation-verification.json'),JSON.stringify(result,null,2)+'\n');return result;
}
if(require.main===module){const [dir,reader]=process.argv.slice(2);console.log(JSON.stringify(inspect(path.resolve(dir),path.resolve(reader))));}
module.exports={inspect};
