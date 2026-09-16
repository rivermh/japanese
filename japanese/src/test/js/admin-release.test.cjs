const {test} = require('node:test');
const assert = require('node:assert/strict');
const {executionRequest, executionState, errorMessage, singleFlight} = require('../../main/resources/static/js/admin-release.js');
const preview = () => ({
 targetIds: [4, 8], totalTargetCount: 2, digest: 'a'.repeat(64), gateVersion: 'phase1a-v1',
 filter: {published: false, reviewStatus: 'PENDING'},
 decisionCounts: {releasable: 2, manualReviewRequired: 0, blocked: 0}, manualTargets: []
});
const manual = () => {
 const p = preview(); p.decisionCounts = {releasable: 1, manualReviewRequired: 1, blocked: 0};
 p.manualTargets = [{content: {contentItemId: 8}, issues: [{code: 'EXAMPLE_MISSING'}, {code: 'DUPLICATE_CANDIDATE'}]}]; return p;
};
const mode = 'ALLOW_EXPLICIT_MANUAL_OVERRIDES';
test('request preserves the actual ordered preview identity and sends no filters', () => {
 const p = preview(); const request = executionRequest(p, 'RELEASABLE_ONLY', [], ' inspected ', true, false);
 assert.deepEqual(request, {targetIds:[4,8],digest:p.digest,gateVersion:p.gateVersion,mode:'RELEASABLE_ONLY',note:'inspected',manualOverrides:[]});
 assert.notEqual(request.targetIds, p.targetIds);
});
test('blocker rejects even in manual mode', () => {
 const p = manual(); p.decisionCounts.blocked = 1;
 assert.throws(() => executionRequest(p,mode,[], 'reason', true,true), /BLOCKED/);
});
test('clean mode cannot execute manual targets', () => {
 assert.match(executionState(manual(),'RELEASABLE_ONLY',[],'reason',true,true), /MANUAL REVIEW/);
});
test('manual requires every issue and a per-item reason', () => {
 for (const override of [
   {contentItemId:8,acknowledgedIssueCodes:['EXAMPLE_MISSING'],reason:'ok'},
   {contentItemId:8,acknowledgedIssueCodes:['EXAMPLE_MISSING','DUPLICATE_CANDIDATE'],reason:' '},
   {contentItemId:4,acknowledgedIssueCodes:['EXAMPLE_MISSING','DUPLICATE_CANDIDATE'],reason:'ok'}
 ]) assert.throws(() => executionRequest(manual(),mode,[override],'reason',true,true));
});
test('all explicit acknowledgments produce exact override payload', () => {
 const overrides=[{contentItemId:8,acknowledgedIssueCodes:['EXAMPLE_MISSING','DUPLICATE_CANDIDATE'],reason:'Reviewed meanings and examples'}];
 assert.deepEqual(executionRequest(manual(),mode,overrides,'release',true,true).manualOverrides,overrides);
 assert.throws(() => executionRequest(manual(),mode,overrides,'release',true,false));
});
test('empty preview, note and unchecked confirmation cannot submit', () => {
 assert.throws(() => executionRequest(null,'RELEASABLE_ONLY',[],'reason',true,false));
 assert.throws(() => executionRequest(preview(),'RELEASABLE_ONLY',[],' ',true,false));
 assert.throws(() => executionRequest(preview(),'RELEASABLE_ONLY',[],'reason',false,false));
 const p=preview(); p.targetIds=[]; assert.throws(() => executionRequest(p,'RELEASABLE_ONLY',[],'reason',true,false));
});
test('public and rejected scopes require another preview', () => {
 const p=preview(); p.filter.published=true;
 assert.throws(() => executionRequest(p,'RELEASABLE_ONLY',[],'reason',true,false));
 p.filter.published=false; p.filter.reviewStatus='REJECTED';
 assert.throws(() => executionRequest(p,'RELEASABLE_ONLY',[],'reason',true,false));
});
test('stale and rollback conflict codes have actionable messages', () => {
 for(const code of ['STALE_PREVIEW','GATE_VERSION_MISMATCH','TARGET_SET_CHANGED'])
  assert.match(errorMessage(code), /새 Dry-run/);
 assert.match(errorMessage('BATCH_ROLLBACK_STATE_CONFLICT'), /전체 rollback이 거부/);
});
test('double submit makes one request and prevents native resubmission', async () => {
 let finish, calls=0, prevented=0;
 const waiting=new Promise(resolve=>finish=resolve);
 const submit=singleFlight(async()=>{calls++;await waiting;});
 const event={preventDefault(){prevented++;}};
 const first=submit(event); await submit(event); assert.equal(calls,1);assert.equal(prevented,2);
 finish();await first;await submit(event);assert.equal(calls,2);
});
