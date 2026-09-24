class Calm {
    int score(int a, int b) {
        int total = 0;
        if (a > 0) {
            total += 1;
        } else {
            total -= 1;
        }
        while (total > 100) {
            total -= 100;
        }
        return total;
    }
}
