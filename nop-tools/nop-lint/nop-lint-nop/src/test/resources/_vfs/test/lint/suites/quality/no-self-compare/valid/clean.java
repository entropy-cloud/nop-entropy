package demo;

class Clean {
    boolean check(int a, int b) {
        if (a == b) {
            return true;
        }
        return a != b + 1;
    }
}
