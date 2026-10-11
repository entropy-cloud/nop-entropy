public class Clean {
    private int v;

    @Override
    public boolean equals(Object o) {
        return o instanceof Clean && ((Clean) o).v == v;
    }

    boolean withObjectBaseline(int v2) {
        return v2 == v;
    }

    boolean primitiveForm(int x) {
        return equals(x);
    }
}
