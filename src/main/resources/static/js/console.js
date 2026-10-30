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
    var enter = document.getElementById('enter-btn');
    if (!line) return;
    if (!el) {
        lastSuggestion = null;
        line.placeholder = 'Typ een antwoord en druk op Enter';
        if (enter) enter.classList.remove('btn-suggested');
        return;
    }
    var key = el.getAttribute('data-key');
    if (key === lastSuggestion) return;
    lastSuggestion = key;
    var answer = el.getAttribute('data-answer') || '';
    line.value = answer;
    // Een leeg onthouden antwoord (alleen Enter) is niet te zien in het veld: maak het zichtbaar.
    line.placeholder = answer === '' ? 'Onthouden antwoord: alleen Enter (druk op Enter)' : 'Typ een antwoord en druk op Enter';
    if (enter) enter.classList.toggle('btn-suggested', answer === '');
    line.focus();
    line.select();
}

function consoleChanged() { scrollConsole(); fillSuggestion(); }
document.addEventListener('DOMContentLoaded', consoleChanged);
document.addEventListener('htmx:afterSwap', consoleChanged);
