class Deep {
    int drill(int a, int b, int c, int d, int e, int f) {
        int total = 0;
        if (a > 0) {
            if (b > 0) {
                if (c > 0) {
                    if (d > 0) {
                        if (e > 0) {
                            if (f > 0) {
                                total += 1;
                            }
                        }
                    }
                }
            }
        }
        return total;
    }
}
