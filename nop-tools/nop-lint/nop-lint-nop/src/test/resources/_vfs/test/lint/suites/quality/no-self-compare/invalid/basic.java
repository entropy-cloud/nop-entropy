package demo;

class Bad {
    boolean check(int a, int b) {
        if (a == a) {
            return true;
        }
        return b != b;
    }
}
