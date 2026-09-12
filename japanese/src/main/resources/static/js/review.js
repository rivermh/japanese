(() => {
    const checkboxes = [...document.querySelectorAll('[data-review-checkbox]')];
    const selectAll = document.querySelector('[data-select-all]');
    const submit = document.querySelector('[data-batch-submit]');

    if (!checkboxes.length || !selectAll || !submit) {
        return;
    }

    const syncSelection = () => {
        const selectedCount = checkboxes.filter((checkbox) => checkbox.checked).length;
        submit.disabled = selectedCount === 0;
        selectAll.checked = selectedCount === checkboxes.length;
        selectAll.indeterminate = selectedCount > 0 && selectedCount < checkboxes.length;
    };

    selectAll.addEventListener('change', () => {
        checkboxes.forEach((checkbox) => {
            checkbox.checked = selectAll.checked;
        });
        syncSelection();
    });
    checkboxes.forEach((checkbox) => checkbox.addEventListener('change', syncSelection));
    syncSelection();
})();
