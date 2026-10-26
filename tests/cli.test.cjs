'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {spawnSync, spawn} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const root = path.resolve(__dirname, '..');
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const jar = path.join(root, 'target', 'db2chm.jar');
const scratch = fs.mkdtempSync(path.join(root, 'target', 'db2chm-contract-'));
test.after(() => fs.rmSync(scratch, {recursive:true, force:true}));
function cli(args, extra={}) {
  const transport=require('../npm/argv.cjs')([...args,'--json']);
  const r = spawnSync(java, ['-jar', jar, '--cli', ...transport.args], {encoding:'utf8', timeout:20000, ...extra, env:{...process.env,...extra.env,...transport.env}});
  assert.ifError(r.error);
  let data; try { data = JSON.parse(r.stdout); } catch { assert.fail('stdout must be one JSON object: ' + r.stdout); }
  assert.equal(data.schemaVersion, 1);
  assert.equal(data.ok, r.status === 0, r.stderr);
  return {...r, body:data};
}
test('version and strict invalid arguments', () => {
  assert.equal(cli(['--version']).body.data.value, '0.2.0-beta.1');
  for(const args of [['export','--mock','--mock-tables','no'], ['export','--mock','--out'], ['export','--mock','--out','a','--out','b'],['export','--tables','1'],['export','--type','not-a-db']]) {
    assert.equal(cli(args).status, 2);
  }
});
test('list and export share include/exclude and deterministic sorting', () => {
  const args=['--mock','--include','BIZ_*','--exclude','*_ITEM'];
  const listed=cli(['tables','list',...args]);
  assert.equal(listed.body.data.tableCount,4);
  const out=path.join(scratch,'过滤 output');
  const exported=cli(['export',...args,'--html-only','--out',out]);
  assert.equal(exported.status,0);
  assert.deepEqual(exported.body.data.tables,listed.body.data.tables);
  const catalog=fs.readFileSync(path.join(out,'catalog.html'),'utf8');
  assert.match(catalog,/BIZ_ORDER/); assert.doesNotMatch(catalog,/BIZ_ORDER_ITEM/);
  assert.equal(fs.existsSync(path.join(out,'database.chm')),false);
  assert.match(fs.readFileSync(path.join(out,'index.html'),'utf8'),/charset=UTF-8|charset="UTF-8"|charset=UTF8/i);
});
test('case matching, repeated includes and no-match contract', () => {
  assert.equal(cli(['tables','list','--mock','--include','biz_order*']).body.data.tableCount,0);
  assert.equal(cli(['tables','list','--mock','--include','biz_order*','--ignore-case']).body.data.tableCount,2);
  assert.equal(cli(['tables','list','--mock','--include','BIZ_ORDER','--include','SYS_USER']).body.data.tableCount,2);
  const out=path.join(scratch,'no-match');
  const r=cli(['export','--mock','--include','absent','--html-only','--out',out]);
  assert.equal(r.status,5); assert.equal(r.body.error.code,'NO_TABLES_MATCHED'); assert.equal(fs.existsSync(out),false);
});
test('existing output and HHP option injection cannot overwrite files', () => {
  const out=path.join(scratch,'existing'); fs.mkdirSync(out); fs.writeFileSync(path.join(out,'database.chm'),'previous');
  const r=cli(['export','--mock','--html-only','--out',out]);
  assert.equal(r.status,7); assert.equal(r.body.error.code,'OUTPUT_EXISTS');
  assert.equal(fs.readFileSync(path.join(out,'database.chm'),'utf8'),'previous');
  const bad=cli(['export','--mock','--title','title\nCompiled file=outside.chm','--html-only','--out',path.join(scratch,'unsafe-title')]);
  assert.equal(bad.status,2);
});
test('dangerous JDBC properties and arbitrary driver class are refused before connection', () => {
  for(const suffix of ['allowMultiQueries=true','autoDeserialize=true','socketFactory=evil.Class','INIT=DROP%20TABLE%20x','%61llowMultiQueries=true','user=root','password=never-log-this']) {
    const r=cli(['tables','list','--type','MYSQL','--url','jdbc:mysql://localhost:3306/demo?'+suffix]);
    assert.equal(r.status,8); assert.equal(r.body.error.code,'CONNECTION_POLICY_REJECTED');
    assert.doesNotMatch(r.stdout+r.stderr,/never-log-this/);
  }
  assert.equal(cli(['tables','list','--type','MYSQL','--url','jdbc:mysql://localhost:3306/demo','--driver','arbitrary.Class']).status,8);
});
test('config refuses stored passwords and a missing approved driver is a dependency failure', () => {
  const config=path.join(scratch,'secret.json');
  fs.writeFileSync(config,JSON.stringify({type:'MYSQL',url:'jdbc:mysql://localhost:3306/demo',password:'never-log-this'}));
  const bad=cli(['tables','list','--config',config]); assert.equal(bad.status,2); assert.doesNotMatch(bad.stdout+bad.stderr,/never-log-this/);
  const missing=cli(['tables','list','--type','MYSQL','--url','jdbc:mysql://localhost:3306/demo']);
  assert.equal(missing.status,3); assert.equal(missing.body.error.code,'DRIVER_NOT_FOUND');
});
test('HTML escapes metadata and a compiler startup failure publishes nothing', () => {
  const out=path.join(scratch,'special');
  assert.equal(cli(['export','--mock','--include','AUDIT_LOG','--html-only','--out',out]).status,0);
  const html=fs.readFileSync(path.join(out,'table_1.html'),'utf8');
  assert.match(html,/&lt;0/); assert.match(html,/&amp;/);
  const fail=path.join(scratch,'failed');
  const r=cli(['export','--mock','--compiler-path',path.join(scratch,'missing-compiler'),'--out',fail]);
  assert.equal(r.status,3); assert.equal(fs.existsSync(fail),false);
});
test('whole operation deadline includes password stdin without leaking or hanging', async () => {
  await new Promise((resolve,reject) => {
    const child=spawn(java,['-jar',jar,'--cli','tables','list','--mock','--password-stdin','--timeout','1','--json'],{stdio:['pipe','pipe','pipe']});
    let out=''; child.stdout.setEncoding('utf8').on('data',s=>out+=s);
    const timer=setTimeout(()=>{child.kill();reject(new Error('timeout contract hung'));},10000);
    child.on('error',reject);
    child.on('close',code=>{clearTimeout(timer); try {assert.equal(code,124);assert.equal(JSON.parse(out).error.code,'OPERATION_TIMEOUT');resolve();}catch(e){reject(e)}});
  });
});
