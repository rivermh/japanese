const endpoint = process.env.CDP_ENDPOINT ?? 'http://127.0.0.1:9223';
const base = process.env.APP_BASE_URL ?? 'http://localhost:8080';
const username = process.env.ADMIN_E2E_USERNAME;
const password = process.env.ADMIN_E2E_PASSWORD;
const mutating = process.env.ADMIN_E2E_MUTATING === 'true';
if (!username || !password) throw new Error('ADMIN_E2E_USERNAME and ADMIN_E2E_PASSWORD are required');

const target = await fetch(`${endpoint}/json/new?${encodeURIComponent('about:blank')}`, {method: 'PUT'}).then(r => r.json());
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
let sequence = 0;
const pending = new Map();
const failures = [];
const exceptions = [];
socket.onmessage = event => {
  const message = JSON.parse(event.data);
  if (message.id && pending.has(message.id)) {
    const {resolve, reject} = pending.get(message.id); pending.delete(message.id);
    message.error ? reject(new Error(JSON.stringify(message.error))) : resolve(message.result);
  }
  if (message.method === 'Network.responseReceived' && message.params.response.status >= 400)
    failures.push({status: message.params.response.status, url: message.params.response.url});
  if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.text);
};
function call(method, params = {}) {
  const id = ++sequence;
  socket.send(JSON.stringify({id, method, params}));
  return new Promise((resolve, reject) => pending.set(id, {resolve, reject}));
}
async function evaluate(expression) {
  const result = await call('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text);
  return result.result.value;
}
async function navigate(path) {
  const destination = path.startsWith('http') ? path : base + path;
  await call('Page.navigate', {url: destination});
  for (let i = 0; i < 80; i++) {
    await new Promise(resolve => setTimeout(resolve, 100));
    if (await evaluate(`document.readyState === 'complete' && location.href === ${JSON.stringify(destination)}`)) return;
  }
  throw new Error(`Navigation timeout: ${path}`);
}
async function viewport(width, height) {
  await call('Emulation.setDeviceMetricsOverride', {width, height, deviceScaleFactor: 1, mobile: width < 600});
}
async function layout(name) {
  const value = await evaluate(`(() => { const main=document.querySelector('main'); const fixed=[...document.querySelectorAll('*')].filter(e=>getComputedStyle(e).position==='fixed').map(e=>e.getBoundingClientRect().toJSON()); return {name:${JSON.stringify(name)},url:location.pathname,width:document.documentElement.clientWidth,scrollWidth:document.documentElement.scrollWidth,mainBottom:main?.getBoundingClientRect().bottom,fixed}; })()`);
  if (value.scrollWidth > value.width) throw new Error(`Horizontal overflow on ${name}: ${value.scrollWidth}/${value.width}`);
  return value;
}
async function submitReview(selector, note) {
  return evaluate(`(async()=>{const f=document.querySelector(${JSON.stringify(selector)});if(!f)throw new Error('review form missing');const body=new URLSearchParams(new FormData(f));body.set('note',${JSON.stringify(note)});const r=await fetch(f.action,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body});return {status:r.status,url:r.url,text:(await r.text()).slice(0,5000)};})()`);
}

await call('Page.enable'); await call('Runtime.enable'); await call('Network.enable');
await viewport(1440, 1000);
await navigate('/login');
await evaluate(`(()=>{document.querySelector('[name="username"]').value=${JSON.stringify(username)};document.querySelector('[name="password"]').value=${JSON.stringify(password)};document.querySelector('form.auth-form').requestSubmit();return true})()`);
for (let i=0;i<80 && !(await evaluate(`location.pathname!=='/login' && document.readyState==='complete'`));i++) await new Promise(r=>setTimeout(r,100));
if ((await evaluate('location.pathname'))==='/login') throw new Error('Browser login failed: '+await evaluate('location.href'));
await new Promise(r=>setTimeout(r,500));
const loginState = {url: await evaluate('location.href'), cookies: (await call('Network.getAllCookies')).cookies.map(c => ({name:c.name, domain:c.domain}))};
if (!mutating) {
  const desktop = [];
  await navigate('/admin'); desktop.push(await layout('dashboard'));
  await navigate('/admin/contents?status=PENDING'); desktop.push(await layout('content-list'));
  await navigate('/admin/contents?status=REJECTED'); const contentUrl=await evaluate(`document.querySelector('a[href^="/admin/contents/"]')?.getAttribute('href')`); await navigate(contentUrl); desktop.push(await layout('content-detail'));
  await navigate('/admin/grammars/curation?recordType=ENRICHMENT'); desktop.push(await layout('curation-list'));
  const curatedUrl=await evaluate(`document.querySelector('a[href*="/admin/grammars/curation/"]')?.getAttribute('href')`); await navigate(curatedUrl); desktop.push(await layout('curation-detail'));
  await viewport(390,844); const mobile=[];
  for(const [name,path] of [['dashboard','/admin'],['content-list','/admin/contents?status=PENDING'],['content-detail',contentUrl],['curation-list','/admin/grammars/curation?recordType=ENRICHMENT'],['curation-detail',curatedUrl]]){await navigate(path);mobile.push(await layout(name));}
  if(failures.length||exceptions.length)throw new Error(JSON.stringify({failures,exceptions}));
  console.log(JSON.stringify({readOnly:true,loginState,desktop,mobile,failures,exceptions},null,2));socket.close();process.exit(0);
}

const desktop = [];
await navigate('/admin'); desktop.push(await layout('dashboard'));
await navigate('/admin/contents?status=PENDING&oldest=true'); desktop.push(await layout('content-list'));
const approveUrl = await evaluate(`document.querySelector('a[href^="/admin/contents/"]')?.getAttribute('href')`);
if (!approveUrl) throw new Error('No PENDING content found');
await navigate(approveUrl); desktop.push(await layout('content-detail'));
const approveReplay = await evaluate(`(()=>{const f=document.querySelector('form[action$="/approve"]');return {action:f.action,csrf:f.querySelector('[name="_csrf"]').value}})()`);
const approve = await submitReview('form[action$="/approve"]', 'admin browser approve');
await navigate(new URL(approve.url).pathname);
const approveFeedback = approve.text.includes('승인하고 공개했습니다.');
if (!approveFeedback || !(await evaluate(`document.querySelector('.status-pill')?.textContent.trim()==='APPROVED'`))) throw new Error('Approve feedback/status missing');
const duplicateApprove = await evaluate(`fetch(${JSON.stringify(approveReplay.action)},{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:${JSON.stringify(approveReplay.csrf)},note:'duplicate'})}).then(r=>({status:r.status,url:r.url}))`);

await navigate('/admin/contents?status=PENDING&oldest=true');
const rejectUrl = await evaluate(`document.querySelector('a[href^="/admin/contents/"]')?.getAttribute('href')`);
await navigate(rejectUrl);
const reject = await submitReview('form[action$="/reject"]', 'admin browser reject');
await navigate(new URL(reject.url).pathname);
const rejectFeedback = reject.text.includes('반려했습니다.');
if (!rejectFeedback || !(await evaluate(`document.querySelector('.status-pill')?.textContent.trim()==='REJECTED'`))) throw new Error('Reject feedback/status missing');

await navigate('/admin/grammars/curation?recordType=ENRICHMENT&status=PENDING'); desktop.push(await layout('curation-list'));
const curationUrl = await evaluate(`document.querySelector('a[href*="/admin/grammars/curation/"]')?.getAttribute('href')`);
if (!curationUrl) throw new Error('No PENDING curated record found');
await navigate(curationUrl); desktop.push(await layout('curation-detail'));
const curated = await submitReview('form[action$="/approve"]', 'admin browser curated approve');
await navigate(new URL(curated.url).pathname);
if (!(await evaluate(`document.querySelector('.status-pill')?.textContent.trim()==='APPROVED'`))) throw new Error('Curated approve status missing');

await viewport(390, 844);
const mobile = [];
for (const [name, path] of [['dashboard','/admin'],['content-list','/admin/contents?status=PENDING'],['content-detail',rejectUrl],['curation-list','/admin/grammars/curation?recordType=ENRICHMENT'],['curation-detail',curationUrl]]) {
  await navigate(path); mobile.push(await layout(name));
}
if (failures.length || exceptions.length) throw new Error(JSON.stringify({failures, exceptions}));
console.log(JSON.stringify({login: await evaluate('location.pathname'), approve:{...approve,feedback:approveFeedback},duplicateApprove,reject:{...reject,feedback:rejectFeedback},curated,desktop,mobile,failures,exceptions}, null, 2));
socket.close();
