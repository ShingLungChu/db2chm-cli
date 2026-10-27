'use strict';
const path=require('node:path');
module.exports=value=>{
 if(typeof value!=='string'||/[\u0000-\u001f\u007f]/.test(value))throw new Error('Invalid tarball report path');
 const name=path.posix.basename(value.replace(/\\/g,'/'));
 if(!/^[a-z0-9][a-z0-9._-]*\.tgz$/i.test(name))throw new Error('Invalid tarball filename');
 return name;
};
