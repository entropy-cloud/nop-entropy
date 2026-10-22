public class Basic {
    String normalize(String s) {
        return s.replaceAll("a+", "b").replaceFirst("^-", "");
    }
}
