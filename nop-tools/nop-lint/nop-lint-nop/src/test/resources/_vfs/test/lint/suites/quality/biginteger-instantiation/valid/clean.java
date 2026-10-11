package demo;

import java.math.BigInteger;

class Clean {
    BigInteger make(String raw) {
        BigInteger real = new BigInteger("12345");
        BigInteger viaFactory = BigInteger.valueOf(1);
        return real.add(viaFactory).add(new BigInteger(raw));
    }
}
