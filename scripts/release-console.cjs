#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),api='https://api.github.com/repos/ShingLungChu/db2chm-cli',web='https://github.com/ShingLungChu/db2chm-cli';
const stateFile=path.join(root,'.local/release-console.json');
const read=file=>JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
function run(command,args,options={}){
 const result=spawnSync(command,args,{cwd:root,encoding:'utf8',timeout:120000,...options});
 if(result.error?.code==='ETIMEDOUT')throw new Error(path.basename(command)+' 等待超时；请查看此前的网络或验证提示，确认状态后重试当前步骤。');
 if(result.error)throw new Error('无法执行 '+path.basename(command)+'，请检查是否已安装并加入 PATH。');
 if(result.status!==0)throw new Error(path.basename(command)+' 执行失败，请查看终端中此前的错误。');
 return result.stdout?.trim();
}
function settings(){return read(path.join(root,'release/config.json'));}
function writeState(value){fs.mkdirSync(path.dirname(stateFile),{recursive:true});fs.writeFileSync(stateFile,JSON.stringify(value,null,2)+'\n');}
function bundlePath(explicit){
 const directory=explicit?path.resolve(root,explicit):fs.existsSync(stateFile)?read(stateFile).bundleDirectory:null;
 if(!directory)throw new Error('尚未选择发布包。先执行 release.cmd fetch 构建编号，或 release.cmd check "完整目录"。');
 return directory;
}
function checkBundle(explicit){
 const directory=bundlePath(explicit),result=require('./verify-release.cjs')(directory,path.join(directory,'acceptance.json'));
 console.log('验收通过：'+result.report.version+'；源码提交 '+result.report.sourceCommit);
 for(const p of result.report.sizes)console.log(p.platform+'：入口＋平台压缩下载 '+p.totalDownloadBytes+' 字节');
 return {directory,...result};
}
function configure(scope,dryRun=false,workspace=root){
 scope=String(scope||'').replace(/^@/,'');
 if(!/^[a-z0-9][a-z0-9._-]{0,213}$/.test(scope))throw new Error('组织/scope 只能填写小写英文、数字、点、下划线和连字符；例如 nstc 或 @nstc。');
 const names=['release/config.json','release/beta-policy.json','package.json','package-lock.json'];
 const before=names.map(name=>fs.readFileSync(path.join(workspace,name))),values=before.map(b=>JSON.parse(b.toString('utf8')));
 if(values[0].version!==values[1].version)throw new Error('版本与 beta 验收策略不一致，请先处理版本配置。');
 if(values[0].platforms.some(p=>('@'+scope+'/'+values[0].name+'-'+p.id).length>214))throw new Error('scope 太长，生成的平台包全名超过 npm 的长度限制。');
 values[0].scope=values[1].scope=scope;
 values[2].name=values[3].name='@'+scope+'/'+values[0].name+'-development';
 values[3].packages[''].name=values[2].name;
 const after=values.map((v,i)=>JSON.stringify(v)===JSON.stringify(JSON.parse(before[i].toString('utf8')))?before[i]:Buffer.from(JSON.stringify(v,null,2)+'\n'));
 const changed=names.filter((_,i)=>!before[i].equals(after[i]));
 const summary={scope,publisher:values[0].publisher,version:values[0].version,changedFiles:changed,dryRun};
 if(dryRun||changed.length===0)return summary;
 const backup=path.join(workspace,'.local/release-config-backups',new Date().toISOString().replace(/[:.]/g,'-'));
 for(let i=0;i<names.length;i++){const file=path.join(backup,names[i]);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,before[i]);}
 try{for(let i=0;i<names.length;i++)fs.writeFileSync(path.join(workspace,names[i]),after[i]);}
 catch(error){for(let i=0;i<names.length;i++)fs.writeFileSync(path.join(workspace,names[i]),before[i]);throw error;}
 return {...summary,backupDirectory:backup};
}
function githubToken(){
 if(process.env.GH_TOKEN||process.env.GITHUB_TOKEN)return process.env.GH_TOKEN||process.env.GITHUB_TOKEN;
 const response=spawnSync('git',['-c','credential.interactive=never','credential','fill'],{cwd:root,encoding:'utf8',input:'protocol=https\nhost=github.com\n\n',env:{...process.env,GCM_INTERACTIVE:'Never',GIT_TERMINAL_PROMPT:'0'},timeout:20000});
 if(response.status!==0)throw new Error('未找到 GitHub 登录凭据。请先成功执行 git push origin main，再重试；不要把令牌发到聊天。');
 const token=response.stdout.split(/\r?\n/).find(line=>line.startsWith('password='))?.slice(9);
 if(!token)throw new Error('GitHub 凭据不可用，请先通过 Git Credential Manager 登录。');
 return token;
}
async function request(route,token,options={}){
 let response;try{response=await fetch(api+route,{...options,headers:{Authorization:'Bearer '+token,'X-GitHub-Api-Version':'2022-11-28',...(options.body?{'Content-Type':'application/json'}:{})},signal:AbortSignal.timeout(45000)});}
 catch{throw new Error('GitHub 网络请求失败；请检查网络后重试。请求内容和凭据未输出。');}
 if(!response.ok)throw new Error('GitHub 返回 HTTP '+response.status+'。请检查仓库 Actions 权限和登录状态。');
 return response.status===204?null:response.json();
}
async function dispatch(workflow,inputs,dryRun){
 const revision=run('git',['rev-parse','HEAD']);
 const dirty=run('git',['status','--porcelain','--','release/config.json','release/beta-policy.json','package.json','package-lock.json','src/main/java','src/main/resources','npm','pom.xml','scripts','.github/workflows']);
 if(dirty)throw new Error('发布配置或构建源码尚未提交。请按指南提交并推送；本工具不会自动提交其他文件。');
 if(dryRun){console.log(JSON.stringify({dryRun:true,workflow,ref:'main',sourceCommit:revision,inputs},null,2));return;}
 const token=githubToken(),remote=await request('/git/ref/heads/main',token);
 if(remote.object.sha!==revision)throw new Error('本地 HEAD 与 GitHub main 不一致。请先完成 git push，再启动构建。');
 await request('/actions/workflows/'+workflow+'/dispatches',token,{method:'POST',body:JSON.stringify({ref:'main',inputs})});
 console.log('已启动 GitHub 工作流：'+workflow+'\n'+web+'/actions/workflows/'+workflow+'\n请打开本次运行；全部绿色后，复制页面地址末尾的运行编号。');
}
async function fetchBundle(id){
 const token=githubToken();
 if(!id){
  const revision=run('git',['rev-parse','HEAD']),recent=await request('/actions/workflows/beta-candidate.yml/runs?head_sha='+revision+'&event=workflow_dispatch&per_page=5',token);
  const latest=recent.workflow_runs?.[0];
  if(!latest)throw new Error('当前源码提交还没有候选构建。请先执行 release.cmd build。');
  if(latest.conclusion!=='success')throw new Error('本次最新构建尚未全部通过，请查看：'+latest.html_url);
  id=String(latest.id);
 }
 if(!/^\d+$/.test(id))throw new Error('请填写 GitHub Actions 运行页面地址末尾的数字编号。');
 const runInfo=await request('/actions/runs/'+id,token);
 if(runInfo.conclusion!=='success'||runInfo.head_repository?.full_name!=='ShingLungChu/db2chm-cli'||!runInfo.path?.endsWith('/beta-candidate.yml'))throw new Error('需要本仓库 Build accepted beta candidate 全部成功的运行编号。');
 const index=await request('/actions/runs/'+id+'/artifacts?per_page=100',token),matches=index.artifacts.filter(a=>a.name==='accepted-npm-release'&&!a.expired);
 if(matches.length!==1)throw new Error('未找到唯一的 accepted-npm-release，或产物已过期；请重新构建。');
 const artifact=matches[0];
 if(artifact.workflow_run?.head_sha!==runInfo.head_sha||!/^sha256:[a-f0-9]{64}$/.test(artifact.digest||''))throw new Error('产物来源或摘要无效。');
 const folder=path.join(root,'target/releases',id),archive=path.join(folder,'accepted-npm-release.zip'),partial=archive+'.partial',directory=path.join(folder,'accepted-npm-release');
 fs.mkdirSync(folder,{recursive:true});
 if(!fs.existsSync(archive)||'sha256:'+hash(fs.readFileSync(archive))!==artifact.digest){
  let complete=false;
  for(let attempt=0;attempt<3&&!complete;attempt++){
   const offset=fs.existsSync(partial)?fs.statSync(partial).size:0;
   console.log('下载已验收原始包，第 '+(attempt+1)+' 次尝试；已下载 '+offset+' 字节。');
   let handle;
   try{
    const response=await fetch(api+'/actions/artifacts/'+artifact.id+'/zip',{headers:{Authorization:'Bearer '+token,...(offset?{Range:'bytes='+offset+'-'}:{})},signal:AbortSignal.timeout(180000)});
    if(![200,206].includes(response.status))throw new Error('download');
    if(response.status===206&&!response.headers.get('content-range')?.startsWith('bytes '+offset+'-'))throw new Error('range');
    handle=fs.openSync(partial,response.status===206?'a':'w');
    for await(const chunk of response.body)fs.writeSync(handle,chunk);
    fs.closeSync(handle);handle=undefined;
    complete='sha256:'+hash(fs.readFileSync(partial))===artifact.digest;
    if(!complete)throw new Error('digest');
   }catch{if(handle!==undefined)fs.closeSync(handle);if(attempt===2)throw new Error('下载尚未完成或摘要不匹配。可再次运行同一 fetch 命令续传；不会采用未校验文件。');}
  }
  fs.renameSync(partial,archive);
 }
 // Extract into a fresh staging directory, so interrupted downloads/checks never replace an earlier accepted bundle.
 const staging=fs.mkdtempSync(path.join(folder,'verified-'));
 const extracted=path.join(staging,'accepted-npm-release');
 run(process.env.PYTHON||'python',[path.join(root,'scripts/extract-ci-artifact.py'),archive,extracted],{stdio:'inherit'});
 const checked=checkBundle(extracted);
 if(checked.report.sourceCommit!==runInfo.head_sha)throw new Error('包内源码提交与本次构建不一致。');
 if(fs.existsSync(directory)){
  if(hash(fs.readFileSync(path.join(directory,'report.json')))!==hash(fs.readFileSync(path.join(extracted,'report.json'))))throw new Error('同一运行编号的本机目录已有不同文件，请保留原件并检查。');
  checkBundle(directory);
 }else fs.renameSync(extracted,directory);
 writeState({candidateRunId:id,bundleDirectory:directory,scope:settings().scope,version:settings().version,sourceCommit:runInfo.head_sha,artifactDigest:artifact.digest,verifiedAt:new Date().toISOString()});
 console.log('已下载并校验，可发布目录：\n'+directory+'\n下一步：release.cmd publish win32-x64');
}
async function verifyRegistry(explicit){
 const {directory,report,packages}=checkBundle(explicit),results=[];
 for(const item of packages){
  const response=await fetch('https://registry.npmjs.org/'+encodeURIComponent(item.name)+'/'+report.version,{signal:AbortSignal.timeout(30000)});
  if(!response.ok)throw new Error('npm 尚无可读取的版本：'+item.name+'@'+report.version+'（HTTP '+response.status+'）。');
  const metadata=await response.json();
  if(metadata.dist?.integrity!==item.integrity)throw new Error('npm 与已验收文件的 integrity 不一致：'+item.name);
  const tagResponse=await fetch('https://registry.npmjs.org/-/package/'+encodeURIComponent(item.name)+'/dist-tags',{signal:AbortSignal.timeout(30000)});
  if(!tagResponse.ok)throw new Error('无法读取 npm 标签：'+item.name);
  const tags=await tagResponse.json();if(tags.beta!==report.version)throw new Error('beta 标签与本次版本不一致：'+item.name);
  results.push({name:item.name,version:report.version,integrity:item.integrity,beta:tags.beta});console.log('npm 校验通过：'+item.name+'@'+report.version);
 }
 const result={checkedAt:new Date().toISOString(),bundleDirectory:directory,sourceCommit:report.sourceCommit,packages:results};
 fs.mkdirSync(path.dirname(stateFile),{recursive:true});fs.writeFileSync(path.join(path.dirname(stateFile),'release-registry-check.json'),JSON.stringify(result,null,2)+'\n');return result;
}
async function main(args=process.argv.slice(2)){
 if(Number(process.versions.node.split('.')[0])<22)throw new Error('需要 Node.js 22 或更高版本。');
 const command=args.shift()||'help',dryRun=args.includes('--dry-run');args=args.filter(a=>a!=='--dry-run');
 if(command==='help'){console.log('发布指南.md\nrelease.cmd status\nrelease.cmd scope 组织名\nrelease.cmd build\nrelease.cmd fetch [构建编号]\nrelease.cmd check [完整目录]\nrelease.cmd publish win32-x64|darwin-x64|darwin-arm64|linux-x64|entry\nrelease.cmd verify-registry\nrelease.cmd test-registry [构建编号]\n修改 scope 或启动构建可加 --dry-run；本工具不会自动 git add/commit/push。');return;}
 if(command==='status'){
  const config=settings();console.log('工作目录：'+root+'\n目标 scope：@'+config.scope+'\n登录/发布账号：'+config.publisher+'\n本版版本：'+config.version+'\n发布渠道：beta\n入口：@'+config.scope+'/'+config.name);
  for(const platform of config.platforms)console.log('平台包：@'+config.scope+'/'+config.name+'-'+platform.id);
  for(const [binary,arguments_] of [['node',['--version']],['git',['--version']],['python',['--version']],['tar',['--version']]])console.log(binary+'：'+run(binary,arguments_).split(/\r?\n/)[0]);
  if(fs.existsSync(stateFile))console.log('上次选择：'+read(stateFile).bundleDirectory);return;
 }
 if(command==='scope'){if(args.length!==1)throw new Error('用法：release.cmd scope nstc；组织名由你选择。');console.log(JSON.stringify(configure(args[0],dryRun),null,2));return;}
 if(command==='build'){
  if(args.length)throw new Error('用法：release.cmd build [--dry-run]');
  const config=settings(),policy=read(path.join(root,'release/beta-policy.json'));
  if(config.scope!==policy.scope||config.version!==policy.version)throw new Error('scope/版本与 beta policy 不一致。');
  const baseline=read(path.join(root,policy.requiredEvidence));
  await dispatch('beta-candidate.yml',{source_run_id:new URL(baseline.nativeRun).pathname.split('/').pop()},dryRun);return;
 }
 if(command==='fetch'){if(args.length>1||dryRun)throw new Error('用法：release.cmd fetch [构建编号]');await fetchBundle(args[0]);return;}
 if(command==='check'){
  if(args.length>1||dryRun)throw new Error('用法：release.cmd check [完整目录]');
  const {directory,report}=checkBundle(args[0]);if(args[0])writeState({bundleDirectory:directory,scope:settings().scope,version:report.version,sourceCommit:report.sourceCommit,verifiedAt:new Date().toISOString()});return;
 }
 if(command==='publish'){
  if(args.length!==1||!['win32-x64','darwin-x64','darwin-arm64','linux-x64','entry'].includes(args[0]))throw new Error('请指定一个平台或 entry，按指南逐包发布。');
  if(dryRun)throw new Error('发布预检请用 release.cmd check；publish 只用于你明确执行真实发布时。');
  run(process.execPath,[path.join(root,'scripts/publish.cjs'),bundlePath(),'beta','--publish','--only',args[0]],{stdio:'inherit',timeout:900000});return;
 }
 if(command==='verify-registry'){if(args.length||dryRun)throw new Error('用法：release.cmd verify-registry');await verifyRegistry();return;}
 if(command==='test-registry'){
  const id=args[0]||(fs.existsSync(stateFile)?read(stateFile).candidateRunId:null);
  if(args.length>1||!/^\d+$/.test(id||''))throw new Error('请先执行 release.cmd fetch，或指定本次候选构建编号。');
  if(!dryRun)await verifyRegistry();await dispatch('npm-registry-validation.yml',{candidate_run_id:String(id)},dryRun);return;
 }
 throw new Error('未知命令。执行 release.cmd 查看可用步骤。');
}
module.exports={configure,main};
if(require.main===module)main().catch(error=>{console.error('发布步骤未完成：'+error.message);process.exitCode=1;});
