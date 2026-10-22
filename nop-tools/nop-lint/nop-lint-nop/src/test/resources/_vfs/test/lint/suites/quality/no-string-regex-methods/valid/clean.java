import java.util.regex.Pattern;

public class Clean {
    private static final Pattern DASH = Pattern.compile("a+");

    String replace(String s) {
        return DASH.matcher(s).replaceAll("b");
    }

    String match(String s) {
        return DASH.matcher(s).matches() ? "ok" : "no";
    }

    String[] split(String s) {
        return DASH.split(s);
    }
}
