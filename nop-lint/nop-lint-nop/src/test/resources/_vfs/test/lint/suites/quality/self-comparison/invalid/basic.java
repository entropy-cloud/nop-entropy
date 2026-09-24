package demo;

class Bad {
    int order(Integer a) {
        return a.compareTo(a);
    }
}
