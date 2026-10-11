public class Basic {
    private int v;

    public Basic clone() {
        Basic copy = new Basic();
        copy.v = v;
        return copy;
    }

    public Object cloneOnlyThrow() {
        throw new CloneNotSupportedException();
    }

    protected Object clone2() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
}
