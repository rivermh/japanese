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
