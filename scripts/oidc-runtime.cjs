'use strict';
const fs=require('node:fs'),path=require('node:path'),settings=require('../release/trusted-publisher.json');
const forbidden=['NODE_AUTH_TOKEN','NPM_TOKEN','NPM_ID_TOKEN','NPM_CONFIG__AUTHTOKEN','NPM_CONFIG__AUTH','NPM_CONFIG_OTP','NPM_CONFIG_USERNAME','NPM_CONFIG_PASSWORD','NPM_CONFIG__PASSWORD'];
function checkContext(env=process.env){
 if(env.GITHUB_ACTIONS!=='true'||!env.ACTIONS_ID_TOKEN_REQUEST_URL||!env.ACTIONS_ID_TOKEN_REQUEST_TOKEN)throw new Error('OIDC requires a GitHub Actions job with id-token: write');
 const repository=settings.githubOwner+'/'+settings.githubRepository,ref='refs/heads/'+settings.branch;
 if(env.GITHUB_REPOSITORY!==repository||env.GITHUB_REF!==ref||env.GITHUB_WORKFLOW_REF!==repository+'/.github/workflows/'+settings.workflowFilename+'@'+ref)throw new Error('OIDC repository, workflow or main-branch identity mismatch');
 const configured=Object.keys(env).filter(name=>forbidden.includes(name.toUpperCase())&&env[name]);
 if(configured.length)throw new Error('OIDC-only job must not receive manually configured npm credentials: '+configured.join(', '));
 return {repository,workflow:settings.workflowFilename,environment:settings.environment,ref};
}
function prepare(env=process.env){
 const identity=checkContext(env);
 if(!env.RUNNER_TEMP||!env.GITHUB_ENV)throw new Error('GitHub runner paths are unavailable');
 const directory=fs.mkdtempSync(path.join(env.RUNNER_TEMP,'db2chm-npm-oidc-'));
 const user=path.join(directory,'user.npmrc'),global=path.join(directory,'global.npmrc');
 fs.writeFileSync(user,'');fs.writeFileSync(global,'');
 fs.appendFileSync(env.GITHUB_ENV,[['NPM_CONFIG_USERCONFIG',user],['NPM_CONFIG_GLOBALCONFIG',global],['NPM_CONFIG_CACHE',path.join(directory,'cache')],['NPM_CONFIG_REGISTRY',settings.registry]].map(([k,v])=>k+'='+v).join('\n')+'\n');
 return {...identity,isolatedNpmConfiguration:true,oidcAuthenticationExercised:false};
}
function check(env=process.env){
 const identity=checkContext(env);
 if(Number(process.versions.node.split('.')[0])!==settings.nodeMajor)throw new Error('Publish job must use Node '+settings.nodeMajor);
 const npm=require('./npm-command.cjs'),version=npm(['--version']).stdout.trim();
 if(version!==settings.npmVersion)throw new Error('Publish job must use the pinned npm '+settings.npmVersion+' (including OIDC dist-tag support)');
 for(const name of ['NPM_CONFIG_USERCONFIG','NPM_CONFIG_GLOBALCONFIG']){
  const file=env[name],temp=path.resolve(env.RUNNER_TEMP||'.')+path.sep;
  if(!file||!path.resolve(file).startsWith(temp)||fs.readFileSync(file,'utf8').trim())throw new Error('Publish job needs isolated empty npm configuration: '+name);
 }
 const projectConfig=path.resolve(__dirname,'../.npmrc');
 if(fs.existsSync(projectConfig))throw new Error('Project .npmrc is not allowed in this token-free publishing job');
 fs.appendFileSync(env.GITHUB_ENV,'DB2CHM_AUTH_MODE=oidc-only\n');
 return {...identity,node:process.versions.node,npm:version,isolatedNpmConfiguration:true,oidcAuthenticationExercised:false};
}
module.exports={checkContext,prepare,check};
if(require.main===module){try{console.log(JSON.stringify(process.argv[2]==='prepare'?prepare():check()));}catch(e){console.error('OIDC_PREFLIGHT_FAILED: '+e.message);process.exitCode=1;}}
