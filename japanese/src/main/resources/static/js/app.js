/* Progressive enhancement only: navigation and lessons work without JavaScript. */
document.querySelectorAll('[data-lesson-target]').forEach(link => {
    link.addEventListener('click', () => {
        const lesson = document.getElementById(link.dataset.lessonTarget);
        if (lesson) lesson.open = true;
    });
});
document.querySelectorAll('[data-lesson]').forEach(lesson => {
    const update = () => {
        const link = document.querySelector(`[data-lesson-target="${lesson.id}"]`);
        if (link) link.setAttribute('aria-current', String(lesson.open));
    };
    lesson.addEventListener('toggle', update);
    update();
});

// Search shares the home route, but navigation follows the user's current section.
if (location.pathname === '/') {
    const updateNavigation = () => {
        const target = location.hash === '#library' ? '/#library' : '/';
        document.querySelectorAll('.primary-nav a').forEach(link => {
            if (link.getAttribute('href') === target) link.setAttribute('aria-current', 'page');
            else link.removeAttribute('aria-current');
        });
    };
    window.addEventListener('hashchange', updateNavigation);
    updateNavigation();
}

const onboardingForm = document.querySelector('[data-onboarding-form]');
if (onboardingForm) {
    const steps = [...onboardingForm.querySelectorAll('[data-step]')];
    const markers = [...onboardingForm.querySelectorAll('.onboarding-progress i')];
    const previous = onboardingForm.querySelector('.onboarding-prev');
    const next = onboardingForm.querySelector('.onboarding-next');
    let current = 1;
    const render = () => {
        onboardingForm.dataset.currentStep = String(current);
        steps.forEach(step => step.classList.toggle('is-current', Number(step.dataset.step) === current));
        markers.forEach((marker, index) => marker.classList.toggle('active', index < current));
        previous.hidden = current === 1;
        steps[current - 1]?.querySelector('input')?.focus({preventScroll:true});
    };
    onboardingForm.classList.add('is-enhanced');
    next.addEventListener('click', () => { if (current < steps.length) { current += 1; render(); window.scrollTo({top:0,behavior:'smooth'}); } });
    previous.addEventListener('click', () => { if (current > 1) { current -= 1; render(); } });
    render();
}

document.querySelectorAll('form[method="post"]').forEach(form => {
    form.addEventListener('submit', () => {
        form.querySelectorAll('button[type="submit"]').forEach(button => {
            button.disabled = true;
            button.setAttribute('aria-busy', 'true');
        });
    });
});

document.querySelectorAll('.dictionary-filter-form details').forEach(filters => {
    const desktop = window.matchMedia('(min-width: 761px)');
    const sync = () => {
        if (desktop.matches) filters.open = true;
    };
    sync();
    desktop.addEventListener?.('change', sync);
});
