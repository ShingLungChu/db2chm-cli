'use strict';
// Synthetic public fixture: no database connection or real metadata.
const fs=require('node:fs'),path=require('node:path');
const out=path.resolve(process.argv[2]);fs.mkdirSync(out,{recursive:true});
const esc=s=>s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const names=process.argv[4]?Array.from({length:+process.argv[4]},(_,i)=>'中文表_'+String(i).padStart(5,'0')+'_'+ '目录索引'.repeat(20)):['订单明细','用户_USER','转义&<>"名称','长名称_'+ '中文'.repeat(40)];
const pages=names.map((name,i)=>({name,url:'table_'+(i+1)+'.html'}));
const title=process.argv[3]||'数据库字典 中文验证';
const item=(name,url)=>'<LI><OBJECT type="text/sitemap"><param name="Name" value="'+esc(name)+'">'+(url?'<param name="Local" value="'+url+'">':'')+'</OBJECT>\n';
const sitemap=body=>'<!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML//EN"><HTML><HEAD><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></HEAD><BODY><UL>'+body+'</UL></BODY></HTML>\n';
const toc=[{name:'封面',url:'index.html'},{name:'表目录',url:'catalog.html'},{name:'表结构',url:null},...pages];
fs.writeFileSync(path.join(out,'contents.hhc'),sitemap(toc.slice(0,3).map(p=>item(p.name,p.url)).join('')+'<UL>'+pages.map(p=>item(p.name,p.url)).join('')+'</UL></LI>'));
fs.writeFileSync(path.join(out,'index.hhk'),sitemap(pages.map(p=>item(p.name,p.url)).join('')));
for(const p of [...toc.slice(0,2),...pages])fs.writeFileSync(path.join(out,p.url),'<!DOCTYPE html><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"><title>'+esc(p.name)+'</title></head><body><h1>'+esc(p.name)+'</h1><p>中文检索 db2chmneedle 订单 用户</p></body></html>');
fs.writeFileSync(path.join(out,'database.hhp'),'[OPTIONS]\nBinary TOC=Yes\nBinary Index=Yes\nCompatibility=1.1 or later\nCompiled file=database.chm\nContents file=contents.hhc\nIndex file=index.hhk\nDefault topic=index.html\nFull-text search=Yes\nLanguage=0x804\nTitle='+title+'\n\n[FILES]\n'+toc.filter(p=>p.url).map(p=>p.url).join('\n')+'\n\n[INFOTYPES]\n');
fs.writeFileSync(path.join(out,'navigation-expected.json'),JSON.stringify({title,toc,keywords:pages,search:['中文检索','db2chmneedle']},null,2)+'\n');
console.log(JSON.stringify({fixture:out,tables:pages.length}));
