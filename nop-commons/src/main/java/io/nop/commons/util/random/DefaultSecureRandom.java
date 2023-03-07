/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util.random;

import java.security.SecureRandom;

public class DefaultSecureRandom implements IRandom {
    private final SecureRandom random;

    public DefaultSecureRandom() {
        this(new SecureRandom());
    }

    public DefaultSecureRandom(SecureRandom random) {
        this.random = random;
    }

    public void setSeed(long seed) {
        random.setSeed(seed);
    }

    @Override
    public int nextInt() {
        return random.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    @Override
    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    @Override
    public long nextLong() {
        return random.nextLong();
    }

    @Override
    public long nextLong(long bound) {
        return nextLong(0, bound);
    }

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }

    @Override
    public double nextDouble(double bound) {
        return random.nextDouble() * bound;
    }

    @Override
    public float nextFloat() {
        return random.nextFloat();
    }

    @Override
    public float nextFloat(float bound) {
        return random.nextFloat() * bound;
    }

    @Override
    public int nextInt(int origin, int bound) {
        if (origin == bound) {
            return bound;
        }

        return origin + random.nextInt(bound - origin);
    }

    @Override
    public long nextLong(long origin, long bound) {
        long r = nextLong();
        if (origin < bound) {
            long n = bound - origin, m = n - 1;
            if ((n & m) == 0L)
                r = (r & m) + origin;
            else if (n > 0L) {
                for (long u = r >>> 1; u + m - (r = u % n) < 0L; u = nextLong() >>> 1) { //NOPMD - suppressed EmptyControlStatement
                    // ignore
                }
                r += origin;
            } else {
                while (r < origin || r >= bound)
                    r = nextLong();
            }
        }
        return r;
    }

    @Override
    public void nextBytes(byte[] bytes) {
        random.nextBytes(bytes);
    }

    @Override
    public double nextGaussian() {
        return random.nextGaussian();
    }
}
