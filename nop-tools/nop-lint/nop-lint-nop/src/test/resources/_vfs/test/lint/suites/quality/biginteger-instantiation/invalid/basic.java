package demo;

import java.math.BigInteger;

class Bad {
    BigInteger make() {
        BigInteger a = new BigInteger("0");
        BigInteger b = new BigInteger("1");
        BigInteger c = new BigInteger("10");
        return a.add(b).add(c);
    }
}
