public class Basic {
    String normalize(String s) {
        if (s.matches("\\d+")) {
            return s.replaceAll("a+", "b").replaceFirst("^-", "").split(",")[0];
        }
        return s.split(",", 2)[0];
    }
}
