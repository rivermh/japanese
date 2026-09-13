class CharacterAnimator {
    constructor(scene) {
        this.scene = scene;
        this.renderer = scene.querySelector('[data-character-renderer]');
        this.primaryFrame = this.renderer?.querySelector('[data-character-frame]');
        this.stageKey = scene.dataset.stage;
        this.assetDirectory = scene.dataset.assetDirectory;
        this.baseState = scene.dataset.state || 'idle';
        this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        this.timer = null;
        this.runId = 0;
        this.manifest = null;
        this.stage = null;

        if (!this.renderer || !this.primaryFrame) return;
        this.installImageFallback(this.primaryFrame);
        if (this.primaryFrame.complete && this.primaryFrame.naturalWidth === 0) this.showFallback();
        else this.initialize();
    }

    async initialize() {
        try {
            const response = await fetch(this.scene.dataset.animationManifest, { credentials: 'same-origin' });
            if (!response.ok) throw new Error(`manifest ${response.status}`);
            this.manifest = await response.json();
            this.stage = this.manifest.stages?.[this.stageKey];
            if (!this.stage?.idle) throw new Error('stage is unavailable');
            const crossfadeMs = this.manifest.frameDefaults?.crossfadeMs;
            if (Number.isFinite(crossfadeMs)) {
                this.scene.style.setProperty('--character-crossfade-ms', `${Math.max(0, crossfadeMs)}ms`);
            }

            this.secondaryFrame = this.primaryFrame.cloneNode(false);
            this.secondaryFrame.removeAttribute('fetchpriority');
            this.secondaryFrame.alt = '';
            this.secondaryFrame.setAttribute('aria-hidden', 'true');
            this.secondaryFrame.classList.remove('is-visible');
            this.renderer.append(this.secondaryFrame);
            this.installImageFallback(this.secondaryFrame);
            this.bindStateEvents();
            this.bindActionLinks();
            this.bindLifecycle();
            this.scene.dataset.animation = 'ready';

            if (this.baseState === 'growth') {
                await this.presentGrowth();
                return;
            }

            if (this.baseState === 'idle') {
                this.showIdle();
            } else {
                await this.play(this.baseState);
            }
        } catch (_) {
            this.scene.dataset.animation = 'static';
        }
    }

    bindStateEvents() {
        this.scene.addEventListener('character:state', event => {
            const state = event.detail?.state;
            if (!state) return;
            if (state === 'growth') {
                this.baseState = state;
                this.presentGrowth();
                return;
            }
            if (state === 'blink') {
                this.play(state);
                return;
            }
            this.baseState = state;
            if (state === 'idle') this.showIdle();
            else this.play(state, event.detail?.returnToIdle);
        });
    }

    async presentGrowth() {
        // Reserve the durable event before animation: refresh and concurrent devices cannot replay it.
        // The separate notice remains until explicitly acknowledged, including if navigation interrupts playback.
        if (document.hidden) return this.showStaticState('growth');
        if (this.growthClaimInFlight) return;
        this.growthClaimInFlight = true;
        const token = document.querySelector('meta[name="_csrf"]')?.content
            || document.querySelector('input[name="_csrf"]')?.value;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
        const stageKey = this.scene.dataset.growthStage;
        try {
            const response = await fetch('/api/v1/characters/growth/present', {
                method: 'POST', credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded', [header]: token || '' },
                body: new URLSearchParams({ stageKey })
            });
            if (!response.ok) throw new Error('growth claim failed');
            const { claimed } = await response.json();
            this.scene.dataset.growthClaimed = String(claimed);
            if (claimed && !this.reducedMotion.matches && !document.hidden) await this.play('growth', true);
            else this.showIdle();
        } catch (_) {
            this.showIdle();
        } finally {
            this.growthClaimInFlight = false;
        }
    }

    bindActionLinks() {
        document.querySelectorAll('[data-character-action]').forEach(link => {
            link.addEventListener('click', event => {
                const state = link.dataset.characterAction;
                if (this.reducedMotion.matches || !link.href || !this.canAnimate(state)) return;
                event.preventDefault();
                this.baseState = state;
                this.play(state, false);
                const delay = Number(link.dataset.characterActionDelay || 600);
                window.setTimeout(() => window.location.assign(link.href), Math.min(Math.max(delay, 0), 1200));
            });
        });
    }

    bindLifecycle() {
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                this.clearTimer();
                this.clearMotion();
                if (this.scene.dataset.growthClaimed === 'true') this.baseState = 'idle';
                this.showStaticState(this.baseState);
            } else if (this.baseState === 'growth' && !this.scene.dataset.growthClaimed) {
                this.presentGrowth();
            } else if (this.baseState === 'idle') {
                this.showIdle();
            } else {
                this.showStaticState(this.baseState);
            }
        });
        this.reducedMotion.addEventListener?.('change', event => {
            this.clearTimer();
            this.clearMotion();
            if (this.scene.dataset.growthClaimed === 'true') this.baseState = 'idle';
            if (event.matches) this.showStaticState(this.baseState);
            else if (this.baseState === 'idle') this.showIdle();
            else this.play(this.baseState, false);
        });
    }

    stateDefinition(state) {
        return this.stage.states?.[state];
    }

    hasFrames(state) {
        return Boolean(this.stateDefinition(state)?.frames?.length);
    }

    canAnimate(state) {
        const definition = this.stateDefinition(state);
        return Boolean(definition && (this.hasFrames(state)
            || (definition.motion && definition.assetStatus !== 'required')));
    }

    async play(state, returnToIdleOverride) {
        if (state === 'blink' && (this.baseState !== 'idle' || this.scene.dataset.state !== 'idle'
            || this.reducedMotion.matches || document.hidden)) return false;
        const definition = this.stateDefinition(state);
        if (!definition) return false;
        this.clearTimer();
        this.clearMotion();
        this.scene.dataset.state = state;
        this.scene.dispatchEvent(new CustomEvent('character:statechange', { detail: { state } }));

        if (this.reducedMotion.matches) {
            await this.showStaticState(state);
            return true;
        }
        if (!this.hasFrames(state)) {
            if (!await this.showStaticState(state)) return false;
            if (definition.motion && definition.assetStatus !== 'required') {
                this.scene.dataset.animation = 'playing';
                const shouldReturn = returnToIdleOverride ?? definition.returnToIdle ?? true;
                this.playMotion(definition.motion, () => {
                    if (shouldReturn) this.showIdle();
                    else this.scene.dataset.animation = 'ready';
                });
                return true;
            }
            this.scene.dataset.animation = definition.assetStatus === 'required' ? 'asset-required' : 'asset-fallback';
            return false;
        }

        const currentRun = ++this.runId;
        this.scene.dataset.animation = 'playing';
        for (const frame of definition.frames) {
            if (currentRun !== this.runId) return false;
            try {
                if (!await this.showFrame(frame.asset, currentRun)) return false;
            } catch (_) {
                if (currentRun === this.runId) this.handlePlaybackFailure();
                return false;
            }
            if (currentRun !== this.runId) return false;
            await this.wait(frame.durationMs || this.manifest.frameDefaults?.durationMs || 140);
        }
        const shouldReturn = returnToIdleOverride ?? definition.returnToIdle ?? true;
        if (currentRun === this.runId && shouldReturn) this.showIdle();
        return true;
    }

    async showStaticState(state) {
        const currentRun = ++this.runId;
        const poster = this.posterAsset(state);
        try {
            if (!await this.showFrame(poster, currentRun)) return false;
        } catch (_) {
            if (currentRun === this.runId) this.showFallback();
            return false;
        }
        if (currentRun !== this.runId) return false;
        this.scene.dataset.state = state;
        this.scene.dataset.animation = this.reducedMotion.matches ? 'reduced' : 'static';
        return true;
    }

    showIdle() {
        this.baseState = 'idle';
        this.clearTimer();
        this.clearMotion();
        const idleRun = this.runId + 1;
        this.showStaticState('idle').then(shown => {
            if (shown && idleRun === this.runId && !this.reducedMotion.matches) {
                this.scene.dataset.animation = this.hasEnteredIdle ? 'idle' : 'ready';
                this.hasEnteredIdle = true;
                this.scheduleAmbient();
            }
        });
        this.scene.dispatchEvent(new CustomEvent('character:statechange', { detail: { state: 'idle' } }));
    }

    async showFrame(asset, currentRun = this.runId) {
        if (!asset) throw new Error('frame asset is unavailable');
        const source = this.assetUrl(asset);
        const incoming = this.primaryFrame.classList.contains('is-visible') ? this.secondaryFrame : this.primaryFrame;
        const outgoing = incoming === this.primaryFrame ? this.secondaryFrame : this.primaryFrame;
        if (incoming.src !== source) {
            await this.loadImage(source);
            if (currentRun !== this.runId) return false;
            incoming.src = source;
        }
        incoming.classList.add('is-visible');
        outgoing.classList.remove('is-visible');
        return true;
    }

    scheduleAmbient() {
        if (this.reducedMotion.matches || document.hidden || this.baseState !== 'idle'
            || this.scene.dataset.state !== 'idle' || !this.stage.ambient?.length) return;
        this.clearTimer();
        const range = this.stage.ambientDelayMs || { min: 10000, max: 18000 };
        const delay = Math.round(range.min + Math.random() * Math.max(0, range.max - range.min));
        this.timer = window.setTimeout(() => this.runAmbient(), delay);
    }

    runAmbient() {
        if (this.reducedMotion.matches || document.hidden || this.baseState !== 'idle'
            || this.scene.dataset.state !== 'idle') return;
        this.clearTimer();
        const options = this.stage.ambient || [];
        const playable = options.filter(option => option.motion || this.hasFrames(option.state));
        if (!playable.length) return;
        const total = playable.reduce((sum, option) => sum + (option.weight || 1), 0);
        let pick = Math.random() * total;
        const selected = playable.find(option => (pick -= option.weight || 1) <= 0) || playable[0];
        if (selected.motion) this.playMotion(selected.motion);
        else this.play(selected.state);
    }

    playMotion(name, onComplete) {
        const definition = this.manifest.motionPresets?.[name];
        if (!definition || this.reducedMotion.matches) return;
        this.clearMotion();
        this.scene.dataset.motion = name;
        this.timer = window.setTimeout(() => {
            this.clearMotion();
            if (onComplete) onComplete();
            else this.scheduleAmbient();
        }, definition.durationMs || 3000);
    }

    clearMotion() {
        delete this.scene.dataset.motion;
    }

    idleAsset() {
        return typeof this.stage.idle === 'string' ? this.stage.idle : this.stage.idle.asset;
    }

    posterAsset(state, visited = new Set()) {
        if (this.reducedMotion.matches) return this.idleAsset();
        if (!state || visited.has(state)) return this.idleAsset();
        visited.add(state);
        const definition = this.stateDefinition(state);
        if (definition?.poster) return definition.poster;
        if (definition?.fallback) return this.posterAsset(definition.fallback, visited);
        return this.idleAsset();
    }

    handlePlaybackFailure() {
        ++this.runId;
        this.clearTimer();
        this.clearMotion();
        this.scene.dataset.animation = 'static';
        this.showFrame(this.idleAsset()).catch(() => this.showFallback());
    }

    loadImage(source) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = resolve;
            image.onerror = reject;
            image.src = source;
        });
    }

    assetUrl(asset) {
        return new URL(`${this.assetDirectory}/${asset}`, window.location.origin).href;
    }

    wait(milliseconds) {
        return new Promise(resolve => {
            this.resolveWait = resolve;
            this.timer = window.setTimeout(() => {
                this.timer = null;
                this.resolveWait = null;
                resolve();
            }, milliseconds);
        });
    }

    clearTimer() {
        ++this.runId;
        if (this.timer !== null) window.clearTimeout(this.timer);
        this.timer = null;
        this.resolveWait?.();
        this.resolveWait = null;
    }

    installImageFallback(image) {
        image.addEventListener('error', () => this.showFallback(), { once: true });
    }

    showFallback() {
        if (!this.renderer?.isConnected) return;
        this.clearTimer();
        this.clearMotion();
        const label = document.createElement('div');
        label.className = 'character-fallback';
        const mark = document.createElement('span');
        mark.lang = 'ja';
        mark.textContent = '学';
        const name = document.createElement('strong');
        name.textContent = this.scene.dataset.character || '학습 파트너';
        label.append(mark, name);
        this.renderer.replaceChildren(label);
        this.scene.dataset.animation = 'unavailable';
    }
}

document.querySelectorAll('[data-character-scene]').forEach(scene => new CharacterAnimator(scene));
