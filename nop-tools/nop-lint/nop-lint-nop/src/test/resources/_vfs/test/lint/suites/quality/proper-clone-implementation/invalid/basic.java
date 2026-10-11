public class Basic {
    private int v;

    public Basic clone() {
        Basic copy = new Basic();
        copy.v = v;
        return copy;
    }
}

class GenericBox<T> {
    public GenericBox<T> clone() {
        GenericBox<T> copy = new GenericBox<>();
        return copy;
    }
}
