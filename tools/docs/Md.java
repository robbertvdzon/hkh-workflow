import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Zet een Markdown-bestand om in een HTML-pagina die Chrome als pdf kan afdrukken (zie bouw-pdf.sh).
 * Gebruik: java -cp commonmark.jar:commonmark-ext-gfm-tables.jar Md.java invoer.md uitvoer.html stijl.css
 * Links naar andere .md-bestanden worden links naar .pdf; een plaatje met een beschrijving wordt een figuur met onderschrift.
 */
public class Md {
    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]);
        String markdown = Files.readString(in, StandardCharsets.UTF_8);
        List<org.commonmark.Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).build();
        Node document = parser.parse(markdown);
        String body = renderer.render(document);

        body = addHeadingIds(body);
        body = body.replaceAll("href=\"([^\"#:]+)\\.md((?:#[^\"]*)?)\"", "href=\"$1.pdf$2\"");
        body = figures(body);

        String title = markdown.lines().filter(l -> l.startsWith("# ")).findFirst().map(l -> l.substring(2)).orElse(in.getFileName().toString());
        String css = Files.readString(Path.of(args[2]), StandardCharsets.UTF_8);
        String html = "<!doctype html><html lang=\"nl\"><head><meta charset=\"utf-8\"><title>" + escape(title) + "</title><style>" + css + "</style></head><body class=\"" + (in.getFileName().toString().startsWith("quickstart") ? "quickstart" : "handleiding") + "\">" + body + "</body></html>";
        Files.writeString(Path.of(args[1]), html, StandardCharsets.UTF_8);
    }

    /** Geeft elke kop een id zoals GitHub dat doet, zodat de inhoudsopgave in het document werkt. */
    static String addHeadingIds(String html) {
        Pattern p = Pattern.compile("<h([1-6])>(.*?)</h\\1>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String text = m.group(2).replaceAll("<[^>]+>", "");
            String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\- ]", "").replace(' ', '-');
            m.appendReplacement(out, Matcher.quoteReplacement("<h" + m.group(1) + " id=\"" + slug + "\">" + m.group(2) + "</h" + m.group(1) + ">"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Een plaatje staat in een alinea; maak er een figuur van met de beschrijving als onderschrift. */
    static String figures(String html) {
        Pattern p = Pattern.compile("<p>\\s*<img src=\"([^\"]+)\" alt=\"([^\"]*)\"\\s*/?>\\s*</p>");
        return p.matcher(html).replaceAll(r -> "<figure><img src=\"" + Matcher.quoteReplacement(r.group(1)) + "\" alt=\"" + Matcher.quoteReplacement(r.group(2)) + "\"><figcaption>" + Matcher.quoteReplacement(r.group(2)) + "</figcaption></figure>");
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
