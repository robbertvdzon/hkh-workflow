// Records aanpassen: de drie knoppen bij het invoerveld (Open, Bewerk, Open in ZCBS) voor het ingevulde recordnummer.
(function () {
    var form = document.getElementById('openrec');
    if (!form) return;
    var input = form.querySelector('[name=nummer]');
    var msg = document.getElementById('openrec-msg');
    var base = form.dataset.base || '/records';

    // Het ingevulde nummer, of null met een melding.
    function number() {
        var n = input.value.trim();
        if (!/^\d{1,9}$/.test(n)) {
            msg.textContent = input.value.trim() === '' ? 'Vul een recordnummer in, bijvoorbeeld 11934.' : 'Vul één recordnummer in, bijvoorbeeld 11934.';
            input.focus();
            return null;
        }
        msg.textContent = '';
        return n;
    }

    // Bewerk is de gewone verzendknop, dus Enter doet dat ook.
    form.addEventListener('submit', function (e) { if (!number()) e.preventDefault(); });

    // Open in ZCBS: een nieuw tabblad met de pagina die de browser meteen ingelogd naar ZCBS stuurt.
    form.querySelector('[data-act=zcbs]').addEventListener('click', function () {
        var n = number();
        if (n) window.open(base + '/' + n + '/zcbs', '_blank');
    });

    // Open: vraagt eerst welk bestand het record heeft en opent het dan in de popup van de pagina (zie viewer.js).
    form.querySelector('[data-act=scan]').addEventListener('click', function () {
        var n = number();
        if (!n) return;
        msg.textContent = 'Scan zoeken…';
        fetch(base + '/media?nummer=' + encodeURIComponent(n), { headers: { Accept: 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
            .then(function (m) {
                if (m.error) { msg.textContent = m.error; return; }
                msg.textContent = '';
                var opener = document.createElement('button');
                opener.type = 'button';
                opener.hidden = true;
                opener.setAttribute('data-viewer', '');
                opener.dataset.url = m.url;
                opener.dataset.title = m.title;
                opener.dataset.kind = m.kind;
                document.body.appendChild(opener);
                opener.click();
                opener.remove();
            })
            .catch(function () { msg.textContent = 'De scan kon niet worden opgehaald. Probeer het opnieuw.'; });
    });
})();
