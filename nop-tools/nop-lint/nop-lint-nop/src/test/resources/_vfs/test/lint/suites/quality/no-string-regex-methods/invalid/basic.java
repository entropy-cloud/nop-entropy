public class Basic {
    String normalize(String s) {
        if (s.matches("\\d+")) {
            return s.replaceAll("a+", "b").replaceFirst("^-", "").split(",")[0];
        }
        return s.split(",", 2)[0];
    }

    String[] viaReturnType(String s) {
        return sameFileString().split(",");
    }

    String sameFileString() {
        return s2();
    }

    String s2() {
        return "x";
    }
}
