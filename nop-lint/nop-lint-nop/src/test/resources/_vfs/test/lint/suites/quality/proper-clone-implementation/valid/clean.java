public class Clean {
    private int v;

    public Clean clone() {
        Clean copy;
        try {
            copy = (Clean) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        return copy;
    }
}

final class Frozen {
    private int v;

    public Frozen clone() {
        return new Frozen();
    }
}
