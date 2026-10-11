package demo;

class Clean {
    boolean check(String name, String other) {
        if (name != null) {
            return name.equals(other);
        }
        return other == null;
    }
}
