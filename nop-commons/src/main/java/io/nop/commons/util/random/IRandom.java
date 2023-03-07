/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util.random;

/**
 * JDK内置的ThreadLocalRandom接口上方法与SecureRandom不兼容，而且不能设置seed, 因此通过IRandom接口屏蔽具体实现。
 */
public interface IRandom {
    int nextInt();

    int nextInt(int bound);

    boolean nextBoolean();

    long nextLong();

    long nextLong(long bound);

    double nextDouble();

    double nextDouble(double bound);

    float nextFloat();

    float nextFloat(float bound);

    default double nextDouble(double origin, double bound) {
        return origin + nextDouble(bound - origin);
    }

    default float nextFloat(float origin, float bound) {
        return origin + nextFloat(bound - origin);
    }

    int nextInt(int origin, int bound);

    long nextLong(long origin, long bound);

    void nextBytes(byte[] bytes);

    double nextGaussian();
}