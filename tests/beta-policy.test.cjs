'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),beta=require('../scripts/beta-policy.cjs');
test('beta audit limitations stay explicit and cannot authorize stable or unrecognized gates',()=>{
 const report={releaseChannel:'beta',version:beta.policy.version},acceptance={betaPolicySha256:beta.hash()};
 const gate={status:beta.policy.allowedGateStatuses.G2,limitations:beta.policy.limitations.G2};
 assert.equal(beta.permitted(report,acceptance,'G2',gate),true);
 assert.equal(beta.permitted({...report,version:'0.2.0'},acceptance,'G2',gate),false);
 assert.equal(beta.permitted({...report,releaseChannel:'stable'},acceptance,'G2',gate),false);
 assert.equal(beta.permitted(report,{betaPolicySha256:'unbound'},'G2',gate),false);
 assert.equal(beta.permitted(report,acceptance,'G1',gate),false);
 assert.equal(beta.permitted(report,acceptance,'G2',{...gate,limitations:[]}),false);
});
