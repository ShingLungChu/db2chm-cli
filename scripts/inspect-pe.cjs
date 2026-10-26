'use strict';
const fs=require('node:fs'),crypto=require('node:crypto'),file=process.argv[2],bytes=fs.readFileSync(file);
if(bytes.toString('ascii',0,2)!=='MZ')throw new Error('Not a Windows PE executable');
const pe=bytes.readUInt32LE(0x3c);
if(bytes.toString('ascii',pe,pe+4)!=='PE\0\0')throw new Error('Invalid PE signature');
const machine=bytes.readUInt16LE(pe+4),sections=bytes.readUInt16LE(pe+6),optional=pe+24,size=bytes.readUInt16LE(pe+20);
if(machine!==0x8664||bytes.readUInt16LE(optional)!==0x20b)throw new Error('Expected PE32+ x86-64');
function offset(rva){
 for(let i=0;i<sections;i++){
  const s=optional+size+i*40,start=bytes.readUInt32LE(s+12),span=Math.max(bytes.readUInt32LE(s+8),bytes.readUInt32LE(s+16));
  if(rva>=start&&rva<start+span)return bytes.readUInt32LE(s+20)+rva-start;
 }
 throw new Error('Unmapped PE RVA');
}
const imports=[],rva=bytes.readUInt32LE(optional+120);
if(rva){let p=offset(rva);for(let i=0;i<1024;i++,p+=20){const name=bytes.readUInt32LE(p+12);if(!name)break;const n=offset(name),end=bytes.indexOf(0,n);if(end<n||end-n>300)throw new Error('Invalid import name');imports.push(bytes.toString('ascii',n,end));}}
console.log(JSON.stringify({machine:'x86_64',format:'PE32+',bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),imports:[...new Set(imports)].sort(),peOsVersion:[bytes.readUInt16LE(optional+40),bytes.readUInt16LE(optional+42)],minimumTestedWindows:'pending'},null,2));
