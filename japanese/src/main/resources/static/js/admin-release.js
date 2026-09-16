/* Admin presentation only. Publication decisions remain server-authoritative. */
(function (root) {
    'use strict';
    const CLEAN = 'RELEASABLE_ONLY';
    const MANUAL = 'ALLOW_EXPLICIT_MANUAL_OVERRIDES';
    const staleCodes = ['STALE_PREVIEW', 'GATE_VERSION_MISMATCH', 'TARGET_SET_CHANGED', 'TARGET_ORDER_INVALID'];
    function errorMessage(code) {
        if (staleCodes.includes(code)) return '콘텐츠 상태 또는 Gate 기준이 preview 이후 변경되었습니다. 새 Dry-run을 실행해주세요.';
        const messages = {
            BATCH_ROLLBACK_STATE_CONFLICT: 'Batch 실행 이후 일부 콘텐츠 상태가 변경되어 안전하게 rollback할 수 없습니다. 전체 rollback이 거부되었습니다.',
            BATCH_ALREADY_ROLLED_BACK: '이미 rollback된 Batch입니다. 이력을 새로 확인해주세요.',
            PUBLICATION_BLOCKED: '공개 차단 항목이 있습니다. 새 Dry-run에서 원인을 확인하세요.',
            MANUAL_OVERRIDE_REQUIRED: '개별 manual issue 확인이 필요합니다.',
            MANUAL_OVERRIDE_INCOMPLETE: '모든 manual issue를 개별 확인하고 사유를 입력하세요.',
            BATCH_TARGET_ALREADY_PUBLISHED: '이미 공개된 대상이 포함되어 있습니다. 새 Dry-run을 실행하세요.',
            BATCH_TARGET_REVIEW_STATUS_INVALID: '실행할 수 없는 검토 상태입니다. 새 Dry-run을 실행하세요.',
            AUTH_REQUIRED: '관리자 세션을 확인하고 다시 로그인하세요.',
            NETWORK_ERROR: '응답을 확인하지 못했습니다. 실행 이력에서 결과를 확인한 뒤 재시도하세요.'
        };
        return messages[code] || '요청이 거부되었습니다. 아래 오류 코드를 확인하세요.';
    }
    function executionState(preview, mode, overrides, note, confirmed, manualConfirmed) {
        if (!preview || !preview.targetIds.length) return '먼저 대상이 있는 Dry-run을 실행하세요.';
        if (preview.decisionCounts.blocked > 0) return 'BLOCKED 대상이 포함되어 전체 실행할 수 없습니다.';
        if (preview.filter.published !== false) return '비공개 대상으로 새 Dry-run을 실행하세요.';
        if (preview.filter.reviewStatus === 'REJECTED') return '거절된 콘텐츠는 실행 대상이 될 수 없습니다.';
        if (mode !== CLEAN && mode !== MANUAL) return '실행 방식을 확인하세요.';
        if (preview.decisionCounts.manualReviewRequired > 0) {
            if (mode !== MANUAL) return 'MANUAL REVIEW 대상이 있어 RELEASABLE_ONLY 실행이 불가능합니다.';
            if (preview.manualTargets.length !== preview.decisionCounts.manualReviewRequired) return '개별 검토 정보가 부족합니다. 새 Dry-run이 필요합니다.';
            for (const target of preview.manualTargets) {
                const override = overrides.find(o => o.contentItemId === target.content.contentItemId);
                const required = target.issues.map(i => i.code).sort();
                if (!override || !override.reason.trim() || override.reason.length > 1000
                    || JSON.stringify([...override.acknowledgedIssueCodes].sort()) !== JSON.stringify(required)) {
                    return '각 콘텐츠의 모든 manual issue를 확인하고 사유를 입력하세요.';
                }
            }
        }
        if (!note.trim() || note.length > 1000) return '실행 사유를 입력하세요 (최대 1000자).';
        if (!confirmed) return 'Preview 대상과 상태 확인이 필요합니다.';
        if (mode === MANUAL && !manualConfirmed) return '개별 manual issue 확인 완료를 체크하세요.';
        return '';
    }
    function executionRequest(preview, mode, overrides, note, confirmed, manualConfirmed) {
        const reason = executionState(preview, mode, overrides, note, confirmed, manualConfirmed);
        if (reason) throw new Error(reason);
        return {targetIds: [...preview.targetIds], digest: preview.digest, gateVersion: preview.gateVersion,
            mode, note: note.trim(), manualOverrides: mode === MANUAL ? overrides : []};
    }
    // Synchronous guard closes the window before the first asynchronous fetch.
    function singleFlight(task) {
        let busy = false;
        return async function (...args) {
            if (args[0] && typeof args[0].preventDefault === 'function') args[0].preventDefault();
            if (busy) return;
            busy = true;
            try { return await task(...args); } finally { busy = false; }
        };
    }
    const exported = {executionState, executionRequest, errorMessage, singleFlight};
    if (typeof module !== 'undefined' && module.exports) module.exports = exported;
    if (typeof document === 'undefined') return;
    const byId = id => document.getElementById(id);
    const page = document.querySelector('[data-release-page], [data-rollback-page]');
    if (!page) return;
    const csrf = byId('release-csrf');
    const message = byId('release-message');
    function announce(text, failure = false) {
        message.textContent = text;
        message.classList.toggle('release-error', failure);
        if (failure) message.focus();
    }
    async function post(url, data) {
        let response;
        try {
            response = await fetch(url, {method: 'POST', credentials: 'same-origin',
                headers: {'Content-Type': 'application/json', [csrf.dataset.header]: csrf.value},
                body: JSON.stringify(data)});
        } catch (_) { throw {code: 'NETWORK_ERROR'}; }
        if (response.redirected || response.status === 401 || response.status === 403) throw {code: 'AUTH_REQUIRED'};
        let body;
        try { body = await response.json(); } catch (_) { throw {code: 'NETWORK_ERROR'}; }
        if (!response.ok) throw {code: body.code || body.error || 'REQUEST_REJECTED'};
        return body;
    }
    function node(tag, text, parent) {
        const el = document.createElement(tag);
        if (text !== undefined) el.textContent = text;
        if (parent) parent.appendChild(el);
        return el;
    }
    function table(parent, headers) {
        const wrap = node('div', undefined, parent); wrap.className = 'release-table'; wrap.tabIndex = 0;
        wrap.setAttribute('role', 'region'); wrap.setAttribute('aria-label', headers.join(' / '));
        const t = node('table', undefined, wrap), tr = node('tr', undefined, node('thead', undefined, t));
        headers.forEach(h => { const th = node('th', h, tr); th.scope = 'col'; });
        return node('tbody', undefined, t);
    }
    function contentLink(cell, id, title) {
        const link = node('a', title || String(id), cell);
        link.href = '/admin/contents/' + encodeURIComponent(id);
        link.target = '_blank'; link.rel = 'noopener';
        link.setAttribute('aria-label', (title || String(id)) + ' 관리자 상세 (새 탭)');
    }
    if (page.hasAttribute('data-release-page')) {
        const filter = byId('release-filter'), button = byId('execute-button');
        let preview = null, busy = false;
        function overrides() {
            return [...byId('manual-items').querySelectorAll('fieldset')].map(field => ({
                contentItemId: Number(field.dataset.id),
                acknowledgedIssueCodes: [...field.querySelectorAll('input:checked')].map(i => i.value),
                reason: field.querySelector('textarea').value
            }));
        }
        function refresh() {
            const mode = byId('release-mode').value;
            byId('manual-review').hidden = mode !== MANUAL;
            byId('manual-confirm-label').hidden = mode !== MANUAL;
            const reason = executionState(preview, mode, overrides(), byId('execution-note').value,
                byId('confirm-preview').checked, byId('confirm-manual').checked);
            button.disabled = busy || !!reason;
            byId('execution-eligibility').textContent = reason || '확인 완료 · 실행 시 서버에서 전체 대상을 다시 검증합니다.';
            if (preview) byId('execution-identity').textContent = '대상 ' + preview.totalTargetCount
                + '건 · 공개 예정 ' + (reason ? '실행 조건 확인 필요' : preview.totalTargetCount + '건')
                + ' · manual override ' + (mode === MANUAL ? preview.decisionCounts.manualReviewRequired : 0)
                + '건 · Gate ' + preview.gateVersion + ' · digest ' + preview.digest.slice(0, 16);
        }
        function invalidate() {
            preview = null; byId('release-preview').hidden = true;
            byId('release-execute').reset(); byId('manual-items').replaceChildren();
            button.disabled = true;
        }
        filter.addEventListener('input', () => {
            if (preview) { invalidate(); announce('필터가 변경되었습니다. 새 Dry-run을 실행하세요.'); }
        });
        function render(result) {
            preview = result;
            byId('release-mode').value = CLEAN;
            byId('release-execute').reset();
            byId('release-preview').hidden = false; byId('retry-preview').hidden = true;
            const summary = byId('release-summary'); summary.replaceChildren();
            node('p', '대상 ' + result.totalTargetCount + '건 · 생성 ' + result.generatedAt + ' · Gate ' + result.gateVersion, summary);
            const counts = node('p', 'RELEASABLE 공개 가능 ' + result.decisionCounts.releasable
                + ' · MANUAL_REVIEW_REQUIRED 개별 검토 ' + result.decisionCounts.manualReviewRequired
                + ' · BLOCKED 공개 차단 ' + result.decisionCounts.blocked, summary);
            if (result.decisionCounts.blocked) counts.className = 'release-error';
            node('p', 'Duplicate candidate: ' + result.duplicateCandidateCount + '건 (자동 공개 불가)', summary);
            byId('preview-digest').textContent = result.digest;
            const rights = byId('rights-counts'); rights.replaceChildren();
            Object.entries(result.sourceRightsCounts).forEach(([key, count]) => node('li', key + ': ' + count + '건', rights));
            const issues = byId('issue-counts'); issues.replaceChildren();
            ['PUBLICATION_BLOCKER','MANUAL_REVIEW','INFORMATIONAL'].forEach(classification => {
                node('h4', classification, issues); const list = node('ul', undefined, issues);
                Object.entries(result.issueCounts).filter(([code, count]) => count && result.issueDetails[code]?.classification === classification)
                    .forEach(([code, count]) => node('li', code + ': ' + count + '건 — ' + result.issueDetails[code].message, list));
                if (!list.children.length) node('li', '없음', list);
            });
            const samples = byId('release-samples'); samples.replaceChildren();
            [['General sample', result.samples], ['Blocked sample', result.blockedSamples], ['Manual Review sample', result.manualReviewSamples]].forEach(([title, rows]) => {
                node('h3', title, samples);
                if (!rows.length) { node('p', '해당 샘플 없음', samples); return; }
                const body = table(samples, ['ID / 표현·패턴','유형 / JLPT','판정','Issue codes','Source / rights']);
                rows.forEach(row => {
                    const tr = node('tr', undefined, body), first = node('td', undefined, tr);
                    contentLink(first, row.contentItemId, row.contentItemId + ' · ' + row.expressionOrPattern);
                    [row.type + ' / ' + row.jlpt, row.decision, row.issueCodes.join(', '),
                        (row.sourceRef || '출처 없음') + ' / ' + (row.sourceRightsStatus || 'MISSING_OR_UNREGISTERED')]
                        .forEach(value => node('td', value, tr));
                });
            });
            const manual = byId('manual-items'); manual.replaceChildren();
            result.manualTargets.forEach(target => {
                const field = node('fieldset', undefined, manual); field.dataset.id = target.content.contentItemId;
                const legend = node('legend', undefined, field);
                contentLink(legend, target.content.contentItemId, target.content.contentItemId + ' · ' + target.content.expressionOrPattern);
                target.issues.forEach(issue => {
                    const label = node('label', undefined, field); label.className = 'release-check';
                    const input = node('input', undefined, label); input.type = 'checkbox'; input.value = issue.code;
                    label.appendChild(document.createTextNode(issue.code + ' — ' + issue.message));
                });
                const label = node('label', '이 콘텐츠의 개별 확인 사유 (필수)', field);
                const reason = node('textarea', undefined, label); reason.maxLength = 1000;
            });
            refresh();
        }
        filter.addEventListener('submit', singleFlight(async event => {
            event.preventDefault(); if (!filter.reportValidity() || busy) return;
            invalidate(); busy = true; byId('preview-button').disabled = true; filter.setAttribute('aria-busy','true');
            const fields = new FormData(filter);
            const request = Object.fromEntries([...fields].map(([k,v]) => [k, v || null]));
            request.published = fields.get('published') === '' ? null : fields.get('published') === 'true';
            // Freeze controls while this particular filter request is in flight.
            [...filter.elements].forEach(el => el.disabled = true);
            announce('Dry-run 검토 중…');
            try { render(await post('/api/v1/admin/content-release/dry-run', request)); announce('Dry-run 완료. 결과와 실행 조건을 확인하세요.'); }
            catch (e) { announce(errorMessage(e.code) + ' [' + e.code + ']', true); }
            finally {
                busy = false; filter.removeAttribute('aria-busy');
                [...filter.elements].forEach(el => el.disabled = false); refresh();
            }
        }));
        page.addEventListener('input', refresh);
        page.addEventListener('change', refresh);
        byId('release-execute').addEventListener('submit', singleFlight(async event => {
            event.preventDefault(); if (busy) return;
            let request;
            try { request = executionRequest(preview, byId('release-mode').value, overrides(),
                byId('execution-note').value, byId('confirm-preview').checked, byId('confirm-manual').checked); }
            catch (e) { announce(e.message, true); return; }
            busy = true; button.disabled = true; button.textContent = '실행 중…';
            page.setAttribute('aria-busy', 'true');
            announce('확인한 대상의 상태를 재검증하고 있습니다…');
            try {
                const batch = await post('/api/v1/admin/content-release/batches', request);
                location.assign('/admin/content-release/batches/' + batch.id);
            } catch (e) {
                invalidate(); byId('retry-preview').hidden = false;
                announce(errorMessage(e.code) + ' [' + e.code + ']', true);
            } finally {
                busy = false; button.textContent = '확인한 대상 공개 실행'; page.removeAttribute('aria-busy'); refresh();
            }
        }));
    } else {
        const form = byId('rollback-form');
        if (!form) return;
        form.addEventListener('submit', singleFlight(async event => {
            event.preventDefault(); if (!form.reportValidity()) return;
            const reason = byId('rollback-reason').value.trim();
            if (!reason) { announce('Rollback 사유를 입력하세요.', true); return; }
            const button = byId('rollback-button'); button.disabled = true;
            form.setAttribute('aria-busy','true'); announce('현재 상태를 확인하고 rollback 중…');
            try {
                await post('/api/v1/admin/content-release/batches/' + page.dataset.batchId + '/rollback', {reason});
                location.reload();
            } catch (e) { announce(errorMessage(e.code) + ' [' + e.code + ']', true); }
            finally { button.disabled = false; form.removeAttribute('aria-busy'); }
        }));
    }
})(typeof globalThis === 'undefined' ? this : globalThis);
