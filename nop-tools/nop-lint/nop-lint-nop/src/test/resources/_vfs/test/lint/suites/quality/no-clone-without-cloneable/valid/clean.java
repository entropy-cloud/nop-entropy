public class Clean implements Cloneable {
    private int v;

    @Override
    public Clean clone() {
        try {
            return (Clean) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    static Object clone(String overload) {
        return overload;
    }
}
