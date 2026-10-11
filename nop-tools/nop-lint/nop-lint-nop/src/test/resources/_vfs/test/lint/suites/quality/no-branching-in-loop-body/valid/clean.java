import java.util.List;

public class Clean {
    int guarded(List<String> items) {
        int hits = 0;
        for (String item : items) {
            if (item.isEmpty()) {
                continue;
            }
            if (item.equals("stop")) {
                break;
            }
            hits = hits + 1;
        }
        while (items.size() > 0) {
            if (work()) {
                return 1;
            }
        }
        return hits;
    }

    boolean work() {
        return false;
    }
}
