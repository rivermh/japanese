/* Real browser smoke test with synthetic HTTP responses only; no application DB connection. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {spawn} = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../main/resources');
const browser = process.env.TEST_CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
let html = fs.readFileSync(path.join(root, 'templates/admin/content-dry-run.html'), 'utf8')
    .replace(/<head[^>]*><\/head>/, '<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/css/app.css"><link rel="stylesheet" href="/css/app-v2.css"></head>')
    .replace('th:value="${_csrf.token}" th:attr="data-header=${_csrf.headerName}"', 'value="synthetic-token" data-header="X-CSRF-TOKEN"');
const harness = `
<script>
window.addEventListener('load', async () => {
 const out = document.createElement('pre'); out.id='browser-test-result'; document.body.append(out);
 const check=(condition,msg)=>{if(!condition)throw Error(msg);};
 const sleep=()=>new Promise(resolve=>setTimeout(resolve,80));
 const el=id=>document.getElementById(id);
 const input=(id,value)=>{el(id).value=value;el(id).dispatchEvent(new Event('input',{bubbles:true}));};
 const change=selector=>document.querySelector(selector).dispatchEvent(new Event('change',{bubbles:true}));
 let requests=[], decision='MANUAL_REVIEW_REQUIRED';
 const sample={contentItemId:77,slug:'synthetic',type:'WORD',expressionOrPattern:'synthetic <b>unsafe</b>',
 jlpt:'N5',decision,issueCodes:['EXAMPLE_MISSING','DUPLICATE_CANDIDATE'],sourceRef:'fixture',sourceRightsStatus:'ALLOWED'};
 window.fetch=async (url, options)=>{
  requests.push({url,body:JSON.parse(options.body)});
  if(url.endsWith('/dry-run')){
   const blocked=decision==='BLOCKED';
   return {ok:true,status:200,json:async()=>({
    filter:{type:'WORD',level:'N5',reviewStatus:'PENDING',published:false},gateVersion:'phase1a-v1',
    generatedAt:'2026-09-16T00:00:00Z',totalTargetCount:1,targetIds:[77],digest:'a'.repeat(64),
    decisionCounts:{releasable:0,manualReviewRequired:blocked?0:1,blocked:blocked?1:0},duplicateCandidateCount:1,
    issueCounts:{EXAMPLE_MISSING:1,DUPLICATE_CANDIDATE:1},
    issueDetails:{EXAMPLE_MISSING:{classification:'MANUAL_REVIEW',message:'예문 확인'},DUPLICATE_CANDIDATE:{classification:'MANUAL_REVIEW',message:'중복 확인'}},
    sourceRightsCounts:{UNKNOWN:0,MANUAL_REVIEW_REQUIRED:0,ALLOWED:1,BLOCKED:0,MISSING_OR_UNREGISTERED:0},
    samples:[sample],blockedSamples:blocked?[sample]:[],manualReviewSamples:blocked?[]:[sample],
    manualTargets:blocked?[]:[{content:sample,issues:[{code:'EXAMPLE_MISSING',message:'예문 확인'},{code:'DUPLICATE_CANDIDATE',message:'중복 확인'}]}]
   })};
  }
  await sleep();
  return {ok:false,status:400,json:async()=>({code:'STALE_PREVIEW'})};
 };
 try {
  check(el('release-preview').hidden,'default preview must be hidden');
  const form=el('release-filter');
  form.elements.type.value='WORD';form.elements.level.value='N5';
  form.requestSubmit();await sleep();await sleep();
  check(!el('release-preview').hidden,'preview visible');
  check(el('release-summary').textContent.includes('MANUAL_REVIEW_REQUIRED'),'decision text');
  check(el('rights-counts').textContent.includes('UNKNOWN'),'rights');
  check(el('issue-counts').textContent.includes('예문 확인'),'issue descriptions');
  check(el('release-samples').querySelectorAll('table').length===2,'samples');
  check(!el('release-samples').querySelector('b'),'sample HTML escaped');
  check(el('execute-button').disabled,'clean mode rejects manual');
  el('release-mode').value='ALLOW_EXPLICIT_MANUAL_OVERRIDES';change('#release-mode');
  input('execution-note','Reviewed fixture');
  el('confirm-preview').checked=true;change('#confirm-preview');
  el('confirm-manual').checked=true;change('#confirm-manual');
  const checks=[...el('manual-items').querySelectorAll('input')];
  checks[0].checked=true;checks[0].dispatchEvent(new Event('change',{bubbles:true}));
  check(el('execute-button').disabled,'partial issue acknowledgment');
  checks[1].checked=true;checks[1].dispatchEvent(new Event('change',{bubbles:true}));
  check(el('execute-button').disabled,'per-item reason required');
  const reason=el('manual-items').querySelector('textarea');reason.value='Both issues checked';reason.dispatchEvent(new Event('input',{bubbles:true}));
  check(!el('execute-button').disabled,'explicit manual request enabled');
  check(document.documentElement.scrollWidth<=window.innerWidth,'page horizontal overflow '+window.innerWidth+'/'+document.documentElement.scrollWidth+' '+[...document.querySelectorAll('main, section, fieldset, legend, select, .release-table')].map(e=>e.tagName+':'+Math.round(e.getBoundingClientRect().right)).join(','));
  el('release-execute').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
  el('release-execute').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
  check(el('execute-button').disabled,'immediate double-submit prevention');
  await sleep();await sleep();await sleep();
  const executes=requests.filter(r=>r.url.endsWith('/batches'));
  check(executes.length===1,'only one execution request');
  check(JSON.stringify(executes[0].body.targetIds)==='[77]','exact IDs');
  check(executes[0].body.digest==='a'.repeat(64),'exact digest');
  check(executes[0].body.gateVersion==='phase1a-v1','exact gate version');
  check(!('filter' in executes[0].body),'no filter reexecution');
  check(el('release-message').textContent.includes('새 Dry-run'),'stale recovery');
  check(!el('retry-preview').hidden,'retry link');
  check(el('release-preview').hidden,'stale preview disabled');
  decision='BLOCKED';form.requestSubmit();await sleep();await sleep();
  el('release-mode').value='ALLOW_EXPLICIT_MANUAL_OVERRIDES';change('#release-mode');
  check(el('manual-items').querySelectorAll('input').length===0,'no blocker override');
  check(el('execute-button').disabled,'blocked execution disabled');
  form.elements.source.value='changed';form.elements.source.dispatchEvent(new Event('input',{bubbles:true}));
  check(el('release-preview').hidden,'filter edit invalidates preview');
  out.textContent='BROWSER_TEST_PASSED';
 } catch(error){out.textContent='BROWSER_TEST_FAILED: '+error.stack;}
});
</script>`;
html = html.replace('</body>',harness+'</body>');
async function run(width) {
 const server=http.createServer((req,res)=>{
  if(req.url==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html);return;}
  const resources={'/js/admin-release.js':'static/js/admin-release.js','/css/admin-release.css':'static/css/admin-release.css',
   '/css/app.css':'static/css/app.css','/css/app-v2.css':'static/css/app-v2.css'};
  if(resources[req.url]){
   res.setHeader('Content-Type',req.url.endsWith('.js')?'text/javascript':'text/css');
   res.end(fs.readFileSync(path.join(root,resources[req.url])));return;
  }
  res.statusCode=404;res.end();
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const profile=fs.mkdtempSync(path.join(os.tmpdir(),'japanese-release-ui-'));
 let child, socket;
 try {
  const debugging=await new Promise((resolve,reject)=>{
   child=spawn(browser,['--headless','--disable-gpu','--no-first-run','--no-default-browser-check',
    '--user-data-dir='+profile,'--remote-debugging-port=0','about:blank']);
   let stderr='';
   const timeout=setTimeout(()=>reject(Error('Browser startup timed out')),15000);
   child.stderr.on('data',data=>{stderr+=data;const match=stderr.match(/DevTools listening on ws:\/\/(127\.0\.0\.1:\d+)/);if(match){clearTimeout(timeout);resolve(match[1]);}});
   child.on('error',error=>{clearTimeout(timeout);reject(error);});
  });
  const pages=await (await fetch('http://'+debugging+'/json')).json();
  socket=new WebSocket(pages.find(p=>p.type==='page').webSocketDebuggerUrl);
  await new Promise(resolve=>socket.addEventListener('open',resolve,{once:true}));
  let id=0;const pending=new Map();
  socket.addEventListener('message',event=>{const msg=JSON.parse(event.data);if(pending.has(msg.id)){pending.get(msg.id)(msg);pending.delete(msg.id);}});
  const send=(method,params={})=>new Promise((resolve,reject)=>{const key=++id;pending.set(key,msg=>msg.error?reject(Error(msg.error.message)):resolve(msg.result));socket.send(JSON.stringify({id:key,method,params}));});
  await send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:'http://127.0.0.1:'+server.address().port+'/'});
  let result;
  for(let attempt=0;attempt<100;attempt++){
   await new Promise(resolve=>setTimeout(resolve,100));
   result=await send('Runtime.evaluate',{expression:'document.getElementById("browser-test-result")?.textContent',returnByValue:true});
   if(result.result.value)break;
  }
  assert.equal(result.result.value,'BROWSER_TEST_PASSED');
  const viewport=await send('Runtime.evaluate',{expression:'window.innerWidth',returnByValue:true});
  assert.equal(viewport.result.value,width);
  console.log('Browser synthetic workflow passed at '+width+'px');
 } finally {if(socket)socket.close();if(child)child.kill();server.close();}
}
(async()=>{await run(1024);await run(390);})().catch(error=>{console.error(error);process.exitCode=1;});
