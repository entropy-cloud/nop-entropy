package demo;

import java.util.Random;

class Clean {
    int draw(Random random, int bound, String key) {
        int unbiased = random.nextInt(bound);
        int hash = key.hashCode() % bound;
        return unbiased + hash;
    }
}
