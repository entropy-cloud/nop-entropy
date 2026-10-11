package demo;

class Boundary {

    <T> T pick(T first) {
        if (first == null) {
            throw new Throwable("no first", null);
        }
        return first;
    }

    static class Inner {
        void nested(java.util.List<String> items) {
            for (String item : items) {
                if (item.isEmpty()) {
                    throw new Exception("empty item");
                }
            }
        }
    }

}
