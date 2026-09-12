class CharacterAnimator {
    constructor(scene) {
        this.scene = scene;
        this.renderer = scene.querySelector('[data-character-renderer]');
        this.primaryFrame = this.renderer?.querySelector('[data-character-frame]');
        this.stageKey = scene.dataset.stage;
        this.assetDirectory = scene.dataset.assetDirectory;
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
            if (!response.ok) return;
            this.manifest = await response.json();
            this.stage = this.manifest.stages?.[this.stageKey];
            if (!this.stage?.idle) return;
            this.secondaryFrame = this.primaryFrame.cloneNode(false);
            this.secondaryFrame.removeAttribute('fetchpriority');
            this.secondaryFrame.alt = '';
            this.secondaryFrame.setAttribute('aria-hidden', 'true');
            this.secondaryFrame.classList.remove('is-visible');
            this.renderer.append(this.secondaryFrame);
            this.installImageFallback(this.secondaryFrame);
            this.preloadStageAssets();
            this.bindStateEvents();
            this.bindActionLinks();
            this.bindLifecycle();
            const initialState = this.scene.dataset.state;
            if (!this.reducedMotion.matches && initialState !== 'idle' && this.hasState(initialState)) {
                window.setTimeout(() => this.play(initialState), 450);
            } else {
                this.scene.dataset.state = 'idle';
                this.scene.dataset.animation = 'ready';
                this.scheduleAmbient();
            }
        } catch (_) {
            this.scene.dataset.animation = 'static';
        }
    }

    bindStateEvents() {
        this.scene.addEventListener('character:state', event => {
            const state = event.detail?.state;
            if (state === 'idle') this.showIdle();
            else if (this.hasState(state)) this.play(state, event.detail?.returnToIdle);
        });
    }

    bindActionLinks() {
        document.querySelectorAll('[data-character-action]').forEach(link => {
            link.addEventListener('click', event => {
                const state = link.dataset.characterAction;
                if (this.reducedMotion.matches || !this.hasState(state) || !link.href) return;
                event.preventDefault();
                this.play(state, false);
                const delay = Number(link.dataset.characterActionDelay || 600);
                window.setTimeout(() => window.location.assign(link.href), Math.min(Math.max(delay, 0), 1200));
            });
        });
    }

    bindLifecycle() {
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) this.clearTimer();
            else if (this.scene.dataset.state === 'idle') this.scheduleAmbient();
        });
        this.reducedMotion.addEventListener?.('change', event => {
            if (event.matches) this.showIdle();
            else this.scheduleAmbient();
        });
    }

    hasState(state) {
        return Boolean(this.stage.states?.[state]?.frames?.length);
    }

    async play(state, returnToIdleOverride) {
        if (this.reducedMotion.matches || !this.hasState(state)) return false;
        const definition = this.stage.states[state];
        const currentRun = ++this.runId;
        this.clearTimer();
        this.scene.dataset.state = state;
        this.scene.dataset.animation = 'playing';
        this.scene.dispatchEvent(new CustomEvent('character:statechange', { detail: { state } }));
        for (const frame of definition.frames) {
            if (currentRun !== this.runId) return false;
            try {
                await this.showFrame(frame.asset);
            } catch (_) {
                ++this.runId;
                this.clearTimer();
                this.scene.dataset.state = 'idle';
                this.scene.dataset.animation = 'static';
                this.showFrame(this.stage.idle).catch(() => this.showFallback());
                return false;
            }
            await this.wait(frame.durationMs || 160);
        }
        const shouldReturn = returnToIdleOverride ?? definition.returnToIdle ?? true;
        if (currentRun === this.runId && shouldReturn) this.showIdle();
        return true;
    }

    showIdle() {
        ++this.runId;
        this.clearTimer();
        this.scene.dataset.state = 'idle';
        this.scene.dataset.animation = 'ready';
        this.showFrame(this.stage.idle);
        this.scene.dispatchEvent(new CustomEvent('character:statechange', { detail: { state: 'idle' } }));
        this.scheduleAmbient();
    }

    async showFrame(asset) {
        const source = this.assetUrl(asset);
        const incoming = this.primaryFrame.classList.contains('is-visible') ? this.secondaryFrame : this.primaryFrame;
        const outgoing = incoming === this.primaryFrame ? this.secondaryFrame : this.primaryFrame;
        if (incoming.src !== source) {
            await this.loadImage(source);
            incoming.src = source;
        }
        incoming.classList.add('is-visible');
        outgoing.classList.remove('is-visible');
    }

    scheduleAmbient() {
        this.clearTimer();
        if (this.reducedMotion.matches || document.hidden || !this.stage.ambient?.length) return;
        const range = this.stage.ambientDelayMs || { min: 10000, max: 18000 };
        const delay = Math.round(range.min + Math.random() * Math.max(0, range.max - range.min));
        this.timer = window.setTimeout(() => {
            const options = this.stage.ambient;
            const total = options.reduce((sum, option) => sum + (option.weight || 1), 0);
            let pick = Math.random() * total;
            const selected = options.find(option => (pick -= option.weight || 1) <= 0) || options[0];
            this.play(selected.state);
        }, delay);
    }

    preloadStageAssets() {
        const assets = new Set([this.stage.idle]);
        Object.values(this.stage.states || {}).forEach(state => state.frames?.forEach(frame => assets.add(frame.asset)));
        assets.forEach(asset => this.loadImage(this.assetUrl(asset)).catch(() => {}));
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
        return new Promise(resolve => window.setTimeout(resolve, milliseconds));
    }

    clearTimer() {
        if (this.timer !== null) window.clearTimeout(this.timer);
        this.timer = null;
    }

    installImageFallback(image) {
        image.addEventListener('error', () => this.showFallback(), { once: true });
    }

    showFallback() {
        if (!this.renderer?.isConnected) return;
        this.clearTimer();
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
