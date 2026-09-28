public class Clean {
    boolean ok(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        int x = 1;
        if (x == 2) {
            return false;
        }
        String c = "a" + "b";
        return c != null && a.hashCode() != b.hashCode();
    }
}
