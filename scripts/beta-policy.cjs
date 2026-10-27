'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),policy=require('../release/beta-policy.json');
const policyFile=path.resolve(__dirname,'../release/beta-policy.json');
const hash=()=>crypto.createHash('sha256').update(fs.readFileSync(policyFile)).digest('hex');
function permitted(report,acceptance,id,gate){
 if(report.releaseChannel!=='beta'||report.version!==policy.version||require('../release/config.json').scope!==policy.scope||!/^\d+\.\d+\.\d+-beta\.\d+$/.test(report.version)||acceptance.betaPolicySha256!==hash())return false;
 return policy.allowedGateStatuses[id]===gate.status&&JSON.stringify(gate.limitations)===JSON.stringify(policy.limitations[id]);
}
module.exports={policy,hash,permitted};
