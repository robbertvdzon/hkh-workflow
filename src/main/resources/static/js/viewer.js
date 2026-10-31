// Bestanden in een popup (afbeelding, pdf, tekst) en de afmeting of het aantal pagina's van de bestanden in de lijst met scans.
(function () {
    var dialog = document.getElementById('viewer');
    if (dialog) {
        var body = document.getElementById('viewer-body');
        var title = document.getElementById('viewer-title');
        var download = document.getElementById('viewer-download');
        var withParam = function (url, key, value) { return url + (url.indexOf('?') < 0 ? '?' : '&') + key + '=' + value; };
        var show = function (url, name, kind) {
            title.textContent = name;
            body.textContent = '';
            var el;
            if (kind === 'image') {
                el = document.createElement('img');
                el.src = url;
                el.alt = name;
            } else {
                el = document.createElement('iframe');
                el.src = url;
                el.title = name;
            }
            body.appendChild(el);
            download.href = withParam(url, 'download', '1');
            if (!dialog.open) dialog.showModal();
        };
        document.addEventListener('click', function (e) {
            var button = e.target.closest ? e.target.closest('[data-viewer]') : null;
            if (!button) return;
            e.preventDefault();
            show(button.dataset.url, button.dataset.title || '', button.dataset.kind || 'image');
        });
        document.getElementById('viewer-close').addEventListener('click', function () { dialog.close(); });
        // Een klik naast het venster (op de achtergrond) sluit het ook.
        dialog.addEventListener('click', function (e) { if (e.target === dialog) dialog.close(); });
        dialog.addEventListener('close', function () { body.textContent = ''; });
    }

    // De afmeting of het aantal pagina's komt los van de lijst binnen, want een pdf openen om te tellen kost tijd op een netwerkschijf.
    var table = document.querySelector('[data-detail-url]');
    if (table) {
        fetch(table.dataset.detailUrl, { headers: { Accept: 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
            .then(function (details) {
                document.querySelectorAll('[data-detail]').forEach(function (cell) { cell.textContent = details[cell.dataset.detail] || '–'; });
            })
            .catch(function () {
                document.querySelectorAll('[data-detail]').forEach(function (cell) { cell.textContent = '?'; });
            });
    }
})();
