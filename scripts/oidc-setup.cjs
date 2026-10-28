'use strict';
const fs=require('node:fs'),path=require('node:path'),publisher=require('../release/trusted-publisher.json'),config=require('../release/config.json');
const root=path.resolve(__dirname,'..'),file=path.join(root,'.local/oidc-setup-status.json');
function plan(){
 const names=[...config.platforms.map(p=>'@'+config.scope+'/'+config.name+'-'+p.id),'@'+config.scope+'/'+config.name];
 return {checkedAt:new Date().toISOString(),scope:config.scope,npmAccount:config.publisher,githubOwner:publisher.githubOwner,githubRepository:publisher.githubRepository,workflowFilename:publisher.workflowFilename,environment:publisher.environment,branch:publisher.branch,nodeMajor:publisher.nodeMajor,npmVersion:publisher.npmVersion,allowedActions:publisher.allowedActions,packages:names.map(name=>({name,url:'https://www.npmjs.com/package/'+name})),npmTrustedPublishersVerified:false,actualOidcPublishVerified:false};
}
const github=(route,options)=>require('./release-console.cjs').githubRequest(route,options);
async function environmentStatus(){
 const route='/environments/'+encodeURIComponent(publisher.environment);
 let value;try{value=await github(route);}catch(e){if(e.statusCode===404)return {exists:false,name:publisher.environment,mainOnly:false};throw e;}
 const policy=value.deployment_branch_policy;
 let rules=[];if(policy?.custom_branch_policies)rules=(await github(route+'/deployment-branch-policies?per_page=100')).branch_policies||[];
 return {exists:true,name:value.name,mainOnly:Boolean(policy?.custom_branch_policies&&rules.length===1&&rules[0].name===publisher.branch&&rules[0].type==='branch'),deploymentBranchPolicy:policy,branchRules:rules.map(({name,type})=>({name,type})),protectionRuleTypes:(value.protection_rules||[]).map(r=>r.type)};
}
async function publicPackageStatus(name){
 let response;try{response=await fetch(publisher.registry+'/'+encodeURIComponent(name),{signal:AbortSignal.timeout(20000)});}catch{throw new Error('无法连接 npm 官方 registry。请检查网络后重试。');}
 if(response.status===404)return {name,publiclyReadable:false,httpStatus:404,bootstrapRequiredOrPrivatePackage:true};
 if(!response.ok)throw new Error('npm metadata 返回 HTTP '+response.status+'：'+name);
 const body=await response.json();return {name,publiclyReadable:true,httpStatus:response.status,versions:Object.keys(body.versions||{}),distTags:body['dist-tags']||{}};
}
async function status(){
 const result=plan();
 const [packages,environment]=await Promise.all([Promise.all(result.packages.map(p=>publicPackageStatus(p.name))),environmentStatus()]);
 Object.assign(result,{packages,githubEnvironment:environment,setupState:packages.every(p=>p.publiclyReadable)?'npm-package-settings-and-actual-oidc-publish-unverified':'bootstrap-or-package-access-confirmation-required'});
 fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,JSON.stringify(result,null,2)+'\n');return result;
}
async function setupGithub(dryRun){
 const before=await environmentStatus(),route='/environments/'+encodeURIComponent(publisher.environment);
 if(dryRun)return {dryRun:true,before,desired:{name:publisher.environment,onlyBranch:publisher.branch},npmPermissionsChanged:false};
 if(before.exists){
  if(!before.mainOnly)throw new Error('npm-release 已存在，但并非仅允许 main。本工具保留已有保护规则，请按手册在 GitHub 页面调整；不会自动清除审核人或其他分支规则。');
  return {githubEnvironment:before,changed:false,npmPermissionsChanged:false};
 }
 await github(route,{method:'PUT',body:JSON.stringify({deployment_branch_policy:{protected_branches:false,custom_branch_policies:true}})});
 await github(route+'/deployment-branch-policies',{method:'POST',body:JSON.stringify({name:publisher.branch,type:'branch'})});
 const after=await environmentStatus();if(!after.mainOnly)throw new Error('GitHub 环境已创建，但 main 分支规则未确认，请在设置页核对。');
 return {githubEnvironment:after,changed:true,npmPermissionsChanged:false,npmTrustedPublishersVerified:false};
}
async function startPreflight(id){
 if(!id){const state=path.join(root,'.local/release-console.json');if(fs.existsSync(state))id=JSON.parse(fs.readFileSync(state,'utf8')).candidateRunId;}
 if(!/^\d+$/.test(id||''))throw new Error('请先选择已验收候选，或执行 release.cmd oidc-preflight 数字候选编号。');
 const candidate=await github('/actions/runs/'+id);
 if(candidate.conclusion!=='success'||!candidate.path?.endsWith('/beta-candidate.yml'))throw new Error('预检需要成功的 beta-candidate 运行编号。');
 await github('/actions/workflows/'+publisher.workflowFilename+'/dispatches',{method:'POST',body:JSON.stringify({ref:publisher.branch,inputs:{artifact_run_id:String(id),dist_tag:'beta',dry_run:'true'}})});
 return {started:true,dryRun:true,candidateRunId:String(id),workflowUrl:'https://github.com/'+publisher.githubOwner+'/'+publisher.githubRepository+'/actions/workflows/'+publisher.workflowFilename,willPublish:false,npmTrustedPublishersVerified:false};
}
async function main(args=process.argv.slice(2)){
 const command=args[0]||'plan',dryRun=args.includes('--dry-run');
 const value=command==='plan'?plan():command==='status'?await status():command==='setup-github'?await setupGithub(dryRun):command==='preflight'?await startPreflight(args[1]):null;
 if(!value)throw new Error('用法：release.cmd oidc-plan / oidc-status / oidc-setup-github [--dry-run]');
 console.log(JSON.stringify(value,null,2));
}
module.exports={plan,environmentStatus,setupGithub,status,startPreflight,main};
if(require.main===module)main().catch(e=>{console.error('OIDC_SETUP_INCOMPLETE: '+e.message);process.exitCode=1;});
