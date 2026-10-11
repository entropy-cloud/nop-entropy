public class Basic {
    private int v;

    public boolean equals(Basic other) {
        return other.v == v;
    }
}

enum Mode {
    A;

    public boolean equals(Mode other) {
        return false;
    }
}
