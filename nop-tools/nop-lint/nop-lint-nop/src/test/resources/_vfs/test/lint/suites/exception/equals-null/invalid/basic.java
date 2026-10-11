package demo;

class Bad {
    boolean check(String name) {
        if (name.equals(null)) {
            return true;
        }
        return false;
    }
}
