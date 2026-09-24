package demo;

import java.util.Random;

class Bad {
    int draw(Random random, Random rnd, Random other, int bound) {
        int a = random.nextInt() % bound;
        int b = rnd.nextInt() % bound;
        int c = other.nextInt() % bound;
        return a + b + c;
    }
}
