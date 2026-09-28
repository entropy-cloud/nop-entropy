public class Basic {
    int scan(List<String> items) {
        for (String item : items) {
            return 0;
        }
        while (items.size() > 0) {
            break;
        }
        do {
            continue;
        } while (items.size() > 0);
        for (int i = 0; i < 3; i++) {
            return i;
        }
        return -1;
    }
}
