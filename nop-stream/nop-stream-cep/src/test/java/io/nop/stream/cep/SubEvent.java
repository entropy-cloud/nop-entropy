/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep;

import java.util.Objects;

public class SubEvent extends Event {
    private double volume;

    public SubEvent(int id, String name, double volume) {
        super(id, name);
        this.volume = volume;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SubEvent subEvent = (SubEvent) o;
        return Double.compare(subEvent.volume, volume) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), volume);
    }
}
