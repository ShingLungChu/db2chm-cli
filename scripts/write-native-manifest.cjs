'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const [out,platform,sourceCommit,bootstrapVersion,bootstrap,bootstrapUrl,bootstrapArchiveSha256]=process.argv.slice(2);
if(!/^[0-9a-f]{40}$/.test(sourceCommit))throw new Error('Pinned source commit required');
const hash=f=>crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const files=fs.readdirSync(out).filter(n=>n!=='manifest.json'&&fs.statSync(path.join(out,n)).isFile()).map(n=>({path:n,sha256:hash(path.join(out,n)),bytes:fs.statSync(path.join(out,n)).size}));
const manifest={platform,sourceCommit,sourceUrl:'https://github.com/fpc/FPCSource/tree/'+sourceCommit,bootstrapVersion,bootstrapSha256:hash(bootstrap),bootstrapUrl:bootstrapUrl||null,bootstrapArchiveSha256:bootstrapArchiveSha256||null,redistributionApproved:false,licenseNotice:'FPC CHM utility: included exact utility sources, modification patch, upstream LICENSE and RTL COPYING files. Formal distribution/oldest-system review pending.',files};
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({platform,sourceCommit,bootstrapVersion,files:files.length,redistributionApproved:false}));
