package lv.lumii.server;

import io.javalin.Javalin;
import lv.semti.morphology.analyzer.Analyzer;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HTTP API server for LVTagger. Run with --help for options and endpoints.
 */
public class Server {

    public static class WordAnalysis {
        public String word;
        public List<WordAnalysisOption> options;
    }

    public static class WordAnalysisOption {
        public String tag;
        public String simplifiedTag;
        public String lemma;
        public String guess;
    }

    /** Shared by all request threads: since morphology 2.202603.3, Analyzer is thread-safe for analysis. */
    private final Analyzer analyzer;

    public Server() throws Exception {
        analyzer = createAnalyzer();
    }

    static Analyzer createAnalyzer() throws Exception {
        Analyzer a = new Analyzer();
        a.enableDiminutive = true;
        a.enablePrefixes = true;
        a.enableGuessing = false;
        a.enableAllGuesses = false;
        a.searchCompoundWords = false;
        a.enableVocative = true;
        a.enableAnalysisCache = true;
        a.setCacheSize(100_000);
        return a;
    }

    public List<WordAnalysis> analyze(List<String> tokens) {
        return analyze(analyzer, tokens);
    }

    private static List<WordAnalysis> analyze(Analyzer analyzer, List<String> tokens) {
        List<WordAnalysis> result = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            Word w = analyzer.analyze(token);
            List<WordAnalysisOption> options = new ArrayList<>(w.wordformsCount());
            for (Wordform wf : w.wordforms) {
                WordAnalysisOption o = new WordAnalysisOption();
                o.tag = wf.getTag();
                o.lemma = wf.getValue(AttributeNames.i_Lemma);
                String guess = wf.getValue(AttributeNames.i_Guess);
                if (guess != null && !AttributeNames.v_NoGuess.equals(guess)) {
                    o.guess = guess;
                }
                wf.removeNonlexicalAttributes();
                o.simplifiedTag = wf.getTag();
                options.add(o);
            }
            WordAnalysis wa = new WordAnalysis();
            wa.word = token;
            wa.options = options;
            result.add(wa);
        }
        return result;
    }

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 7070;

    private static final String USAGE = String.join(System.lineSeparator(),
            "Usage: java -cp <jar> lv.lumii.server.Server [options]",
            "",
            "LVTagger HTTP API server.",
            "",
            "Options:",
            "  --host <addr>       bind address (default " + DEFAULT_HOST + ")",
            "  --port <n>          port to listen on (default " + DEFAULT_PORT + ")",
            "  -h, --help          show this help and exit",
            "",
            "Endpoints:",
            "  POST /analyze       body: newline-separated words (text/plain)",
            "                      response: JSON array of {word, options:[{tag, simplifiedTag, lemma, guess}]}",
            "  GET  /health        returns \"ok\"",
            "");

    public static void main(String[] args) throws Exception {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "--help":
                    System.out.print(USAGE);
                    return;
                case "--host":
                    host = requireValue(args, ++i, arg);
                    break;
                case "--port":
                    port = parseInt(requireValue(args, ++i, arg), arg);
                    break;
                default:
                    usageError("Unknown option: " + arg);
            }
        }

        Server server = new Server();

        Javalin app = Javalin.create(config -> {
            config.http.maxRequestSize = 100_000_000L;
            config.showJavalinBanner = false;
        });

        app.get("/health", ctx -> ctx.result("ok"));

        app.post("/analyze", ctx -> {
            List<String> tokens = Arrays.asList(ctx.body().split("\\R"));
            ctx.json(server.analyze(tokens));
        });

        app.start(host, port);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) usageError("Missing value for " + option);
        return args[index];
    }

    private static int parseInt(String value, String option) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            usageError("Invalid number for " + option + ": " + value);
            return -1; // unreachable
        }
    }

    private static void usageError(String message) {
        System.err.println("Error: " + message);
        System.err.println();
        System.err.print(USAGE);
        System.exit(2);
    }
}
