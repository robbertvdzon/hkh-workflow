// De console van een programma dat draait (pagina Nieuwe bestanden en de technische pagina).

// Console automatisch naar beneden scrollen.
function scrollConsole() {
    var el = document.getElementById('console-out');
    if (el) el.scrollTop = el.scrollHeight;
}

// Het onthouden antwoord op de huidige vraag alvast in het invoerveld zetten, één keer per vraag.
var lastSuggestion = null;
function fillSuggestion() {
    var el = document.getElementById('suggest'), line = document.getElementById('line');
    if (!el || !line) return;
    var key = el.getAttribute('data-key');
    if (key === lastSuggestion) return;
    lastSuggestion = key;
    line.value = el.getAttribute('data-answer') || '';
    line.focus();
    line.select();
}

function consoleChanged() { scrollConsole(); fillSuggestion(); }
document.addEventListener('DOMContentLoaded', consoleChanged);
document.addEventListener('htmx:afterSwap', consoleChanged);
