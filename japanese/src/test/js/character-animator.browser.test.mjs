// Run: node --test src/test/js/character-animator.browser.test.mjs
// Uses headless Edge (or CHROME_PATH), real assets, and DOM/CDP only; no screenshots.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { once } from 'node:events';

const root = new URL('../../main/resources/static/', import.meta.url);
// Fixture-only optional frames: the server aliases unchanged poster bytes to frame URLs.
// This verifies lifecycle mechanics without pretending that production Blink artwork exists.
const poster = 'haru-stage-1.png';
const frameNames = [poster, 'haru-stage-1-blink-01.png', 'haru-stage-1-blink-02.png', 'haru-stage-1-blink-03.png', poster];
const manifest = {version:2,frameDefaults:{durationMs:140,crossfadeMs:90},
 motionPresets:{breathe:{durationMs:3600},settle:{durationMs:2400},focus:{durationMs:1800},happy:{durationMs:1100},celebrate:{durationMs:1800}},
 stages:{'stage-1':{idle:{asset:poster},states:{idle:{poster},
 blink:{poster,frames:frameNames.map((asset,i)=>({asset,durationMs:[90,70,110,70,120][i]})),returnToIdle:true},
 growth:{poster,motion:'celebrate',returnToIdle:true},study:{poster,motion:'focus',returnToIdle:false},happy:{poster,motion:'happy',returnToIdle:false},'goal-complete':{poster,motion:'celebrate',returnToIdle:false}},
 ambient:[{motion:'breathe',weight:4},{state:'blink',weight:2},{motion:'settle',weight:1}],ambientDelayMs:{min:8000,max:16000}}}};
const frames = manifest.stages['stage-1'].states.blink.frames;
const assets = ['haru-stage-1.png', 'haru-stage-1-blink-01.png', 'haru-stage-1-blink-02.png', 'haru-stage-1-blink-03.png', 'haru-stage-1.png'];
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

test('Haru Blink browser lifecycle, requests, cancellation and reduced motion', async t => {
    assert.deepEqual(frames.map(f => f.asset), assets);
    assert.deepEqual(frames.map(f => f.durationMs), [90, 70, 110, 70, 120]);
    const requests = [];
    let releaseImage;
    let slowImage = false;
    let growthClaims = 0;
    const server = createServer(async (req, res) => {
        const path = req.url.split('?')[0];
        if (path === '/api/v1/characters/growth/present') { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify({claimed: growthClaims++ === 0})); return; }
        if (path === '/') {
            res.setHeader('Content-Type', 'text/html');
            res.end(`<link rel="stylesheet" href="/css/app.css"><link rel="stylesheet" href="/css/app-v2.css">
                <div class="home-desk"><aside class="haru-room" data-stage="stage-1" data-state="idle"
                data-asset-directory="/images/characters/haru" data-animation-manifest="/images/characters/haru/animation.json">
                <div class="character-renderer" data-character-renderer><img width="1024" height="1024"
                class="character-frame is-visible" data-character-frame src="/images/characters/haru/haru-stage-1.png"></div></aside></div>
                <script src="/js/character-animator.js"></script>`);
            return;
        }
        try {
            const bytes = path.endsWith('/animation.json') ? JSON.stringify(manifest) : await readFile(new URL('.' + (path.includes('-blink-') ? '/images/characters/haru/haru-stage-1.png' : path), root));
            if (slowImage && path.endsWith('haru-stage-1-blink-02.png')) await new Promise(r => { releaseImage = r; });
            requests.push({ path, status: 200 });
            res.setHeader('Cache-Control', 'no-store');
            res.setHeader('Content-Type', path.endsWith('.js') ? 'text/javascript' : path.endsWith('.css') ? 'text/css' : path.endsWith('.json') ? 'application/json' : 'image/png');
            res.end(bytes);
        } catch {
            requests.push({ path, status: 404 });
            res.writeHead(404).end();
        }
    });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    const profile = await mkdtemp(join(tmpdir(), 'haru-blink-lifecycle-'));
    const browser = spawn(process.env.CHROME_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
        ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'],
        { windowsHide: true, stdio: 'ignore' });
    let socket;
    t.after(async () => { socket?.close(); browser.kill(); server.closeAllConnections(); server.close(); });
    let port;
    for (let i = 0; i < 100; i++) {
        try { port = (await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; break; }
        catch { await pause(100); }
    }
    assert.ok(port, 'headless browser starts');
    const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    socket = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl);
    await once(socket, 'open');
    let id = 0;
    const pending = new Map();
    socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id) { pending.get(message.id)?.(message); pending.delete(message.id); }
    });
    const cdp = (method, params = {}) => new Promise((resolve, reject) => {
        const requestId = ++id;
        pending.set(requestId, r => r.error ? reject(r.error) : resolve(r.result));
        socket.send(JSON.stringify({ id: requestId, method, params }));
    });
    const evaluate = async expression => {
        const result = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
        assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails));
        return result.result.value;
    };
    const until = async expression => {
        for (let i = 0; i < 200; i++) { if (await evaluate(expression)) return; await pause(10); }
        assert.fail(`Timed out: ${expression}`);
    };
    const boot = async (reduced = false) => {
        await cdp('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: reduced ? 'reduce' : 'no-preference' }] });
        await cdp('Page.navigate', { url: `http://127.0.0.1:${server.address().port}/` });
        await until('typeof CharacterAnimator !== "undefined" && document.querySelector("img").complete');
        await evaluate(`window.jobs = new Map(); window.jobId = 0; window.randomValue = 0;
            Math.random = () => randomValue;
            window.setTimeout = (fn, ms) => { jobs.set(++jobId, { fn, ms }); return jobId; };
            window.clearTimeout = id => jobs.delete(id);
            window.scene = document.querySelector('aside');
            scene.dataset.characterScene = '';
            window.animator = new CharacterAnimator(scene);
            window.sequence = [];
            const original = animator.showFrame.bind(animator);
            animator.showFrame = async (...args) => { const shown = await original(...args); if (shown) sequence.push(args[0]); return shown; };
            window.tick = () => { const [id, job] = jobs.entries().next().value; jobs.delete(id); job.fn(); return job.ms; };
            window.state = state => scene.dispatchEvent(new CustomEvent('character:state', { detail: { state } }));`);
        await until(`scene.dataset.animation === '${reduced ? 'reduced' : 'ready'}'`);
    };
    await boot();
    assert.equal(await evaluate('jobs.size'), 1);
    assert.equal(await evaluate('[...jobs.values()][0].ms'), 8000);
    await evaluate('randomValue = 1; animator.scheduleAmbient()');
    assert.equal(await evaluate('[...jobs.values()][0].ms'), 16000);
    const size = await evaluate('JSON.stringify([scene.offsetWidth, scene.offsetHeight, animator.renderer.offsetWidth, animator.renderer.offsetHeight])');
    await evaluate('sequence = []; randomValue = 0.7; tick()');
    for (const duration of [90, 70, 110, 70, 120]) {
        await until(`jobs.size === 1 && [...jobs.values()][0].ms === ${duration}`);
        assert.equal(await evaluate('JSON.stringify([scene.offsetWidth, scene.offsetHeight, animator.renderer.offsetWidth, animator.renderer.offsetHeight])'), size);
        assert.equal(await evaluate('getComputedStyle(document.querySelector(".is-visible")).transitionDuration'), '0s');
        assert.equal(await evaluate('tick()'), duration);
    }
    await until('scene.dataset.state === "idle" && jobs.size === 1');
    assert.deepEqual((await evaluate('sequence')).slice(0, 5), assets);
    assert.equal(await evaluate('getComputedStyle(animator.renderer).animationName'), 'none');
    for (const asset of new Set(assets)) assert.ok(requests.some(r => r.path.endsWith(asset) && r.status === 200));

    for (const state of ['study', 'happy', 'goal-complete']) {
        await evaluate('void animator.play("blink")');
        await until('jobs.size === 1 && [...jobs.values()][0].ms === 90');
        await evaluate('tick()');
        await until('jobs.size === 1 && [...jobs.values()][0].ms === 70');
        await evaluate(`state('${state}')`);
        await until(`scene.dataset.state === '${state}' && jobs.size === 1`);
        const count = requests.filter(r => r.path.includes('blink-')).length;
        await evaluate('void animator.runAmbient(); state("blink")');
        assert.equal(await evaluate('scene.dataset.state'), state);
        await evaluate('tick()');
        await pause(30);
        assert.equal(await evaluate('jobs.size'), 0);
        assert.equal(requests.filter(r => r.path.includes('blink-')).length, count);
        await evaluate('state("idle")');
        await until('scene.dataset.state === "idle" && jobs.size === 1');
    }

    // A delayed image must not overwrite a newer non-idle poster.
    await boot();
    slowImage = true;
    await evaluate('void animator.play("blink")');
    await until('jobs.size === 1');
    await evaluate('tick()');
    await until('jobs.size === 1');
    await evaluate('tick()');
    for (let i = 0; i < 100 && !releaseImage; i++) await pause(10);
    assert.ok(releaseImage);
    await evaluate('state("study")');
    await until('scene.dataset.state === "study" && jobs.size === 1');
    releaseImage();
    slowImage = false;
    await pause(100);
    assert.equal(await evaluate('document.querySelector(".is-visible").src.split("/").pop()'), 'haru-stage-1.png');
    assert.equal(await evaluate('scene.dataset.state'), 'study');
    await evaluate('state("idle")');
    await until('scene.dataset.state === "idle" && jobs.size === 1');

    // Hiding during a blink cancels its wait and restores the open poster.
    await evaluate('void animator.play("blink")');
    await until('jobs.size === 1');
    await evaluate('tick()');
    await until('jobs.size === 1');
    await evaluate('Object.defineProperty(document, "hidden", { configurable: true, value: true }); document.dispatchEvent(new Event("visibilitychange"))');
    await until('scene.dataset.state === "idle" && jobs.size === 0');
    assert.equal(await evaluate('document.querySelector(".is-visible").src.split("/").pop()'), 'haru-stage-1.png');
    await evaluate('Object.defineProperty(document, "hidden", { configurable: true, value: false }); document.dispatchEvent(new Event("visibilitychange"))');
    await until('jobs.size === 1');

    // Runtime preference changes cancel active playback too.
    await evaluate('void animator.play("blink")');
    await until('jobs.size === 1');
    await cdp('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
    await until('scene.dataset.animation === "reduced" && jobs.size === 0');
    assert.equal(await evaluate('document.querySelector(".is-visible").src.split("/").pop()'), 'haru-stage-1.png');
    await cdp('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
    await boot();
    const mobileSize = await evaluate('JSON.stringify([scene.offsetWidth, scene.offsetHeight, animator.renderer.offsetWidth, animator.renderer.offsetHeight])');
    await evaluate('void animator.play("blink")');
    for (const duration of [90, 70, 110, 70, 120]) {
        await until(`jobs.size === 1 && [...jobs.values()][0].ms === ${duration}`);
        assert.equal(await evaluate('JSON.stringify([scene.offsetWidth, scene.offsetHeight, animator.renderer.offsetWidth, animator.renderer.offsetHeight])'), mobileSize);
        await evaluate('tick()');
    }
    await until('scene.dataset.state === "idle" && jobs.size === 1');
    const count = requests.filter(r => r.path.includes('blink-')).length;
    await boot(true);
    await evaluate('state("blink"); animator.runAmbient(); animator.scheduleAmbient()');
    assert.equal(await evaluate('jobs.size'), 0);
    assert.equal(await evaluate('scene.dataset.state'), 'idle');
    assert.equal(requests.filter(r => r.path.includes('blink-')).length, count);
    // Durable claim is simulated by the fixture API, while all playback uses the real animator.
    await boot();
    await evaluate("scene.dataset.growthStage = 'apprentice'; animator.baseState = 'growth'; void animator.presentGrowth()");
    await until("scene.dataset.growthClaimed === 'true' && jobs.size === 1");
    await cdp('Emulation.setEmulatedMedia', {features:[{name:'prefers-reduced-motion',value:'reduce'}]});
    await until("jobs.size === 0 && scene.dataset.state === 'idle'");
    await cdp('Emulation.setEmulatedMedia', {features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
    await until("scene.dataset.state === 'idle' && jobs.size === 1");
    await boot();
    await evaluate("scene.dataset.growthStage = 'apprentice'; animator.baseState = 'growth'; void animator.presentGrowth()");
    await until("scene.dataset.growthClaimed === 'false' && scene.dataset.state === 'idle'");
    assert.equal(growthClaims, 2);
    assert.deepEqual(requests.filter(r => r.status === 404 && r.path !== '/favicon.ico'), []);
});
