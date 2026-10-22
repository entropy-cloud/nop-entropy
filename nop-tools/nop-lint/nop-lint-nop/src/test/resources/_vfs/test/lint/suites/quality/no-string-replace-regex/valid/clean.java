import java.util.regex.Pattern;

public class Clean {
    private static final Pattern DASH = Pattern.compile("a+");

    String replace(String s) {
        return DASH.matcher(s).replaceAll("b");
    }

    String split(String s) {
        return DASH.splitAsStream(s).findFirst().orElse("");
    }
}
