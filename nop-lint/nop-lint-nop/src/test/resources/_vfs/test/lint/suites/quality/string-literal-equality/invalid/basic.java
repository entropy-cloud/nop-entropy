public class Basic {
    boolean check(String a) {
        if (a == "x") {
            return true;
        }
        if ("y" != a) {
            return false;
        }
        return a == "z" ? true : false;
    }
}
