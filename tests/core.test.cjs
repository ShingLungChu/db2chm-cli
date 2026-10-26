'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path'),{spawnSync}=require('node:child_process');
test('Unicode JVM argument transport',()=>{
  const root=path.resolve(__dirname,'..');
  const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
  const result=spawnSync(java,['-Dfile.encoding=UTF-8','-cp',[path.join(root,'target/test-classes'),path.join(root,'target/db2chm.jar')].join(path.delimiter),'com.db2chm.UnicodeCheck','过滤 output'],{encoding:'utf8',timeout:10000});
  assert.equal(result.status,0,result.stderr);const data=JSON.parse(result.stdout);console.log('JVM Unicode argv check: '+result.stdout.trim());assert.equal(data.matches,true);
});
test('JDBC guards, read-only setup, compiler argv/deadlines and output races',()=>{
  const root=path.resolve(__dirname,'..');
  const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
  const result=spawnSync(java,['-cp',[path.join(root,'target/test-classes'),path.join(root,'target/db2chm.jar')].join(path.delimiter),'com.db2chm.CoreChecks',process.execPath,path.join(root,'target')],{encoding:'utf8',timeout:20000});
  assert.ifError(result.error);assert.equal(result.status,0,result.stderr+result.stdout);
});
