'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path'),{spawnSync}=require('node:child_process');
test('JDBC guards, read-only setup, compiler argv/deadlines and output races',()=>{
  const root=path.resolve(__dirname,'..');
  const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
  const result=spawnSync(java,['-cp',[path.join(root,'target/test-classes'),path.join(root,'target/db2chm.jar')].join(path.delimiter),'com.db2chm.CoreChecks',process.execPath,path.join(root,'target')],{encoding:'utf8',timeout:20000});
  assert.ifError(result.error);assert.equal(result.status,0,result.stderr+result.stdout);
});
