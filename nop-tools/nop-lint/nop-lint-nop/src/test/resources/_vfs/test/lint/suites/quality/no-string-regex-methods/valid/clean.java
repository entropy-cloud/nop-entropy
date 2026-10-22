import java.util.regex.Pattern;

public class Clean {
    private static final Pattern DASH = Pattern.compile("a+");

    String replace(String s) {
        return DASH.matcher(s).replaceAll("b");
    }

    String[] splitVarPattern(String s) {
        var p = Pattern.compile("a+");
        return p.split(s);
    }

    String[] splitPatternField(String s) {
        return DASH.split(s);
    }
}
