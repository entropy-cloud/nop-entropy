class Calm {
    int score(int a, int b) {
        int total = 0;
        if (a > 0) {
            total += 1;
        }
        for (int i = 0; i < b; i++) {
            total += i;
        }
        return total;
    }
}
