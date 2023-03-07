/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.lattice;

public class BoolLattice implements ILattice<Boolean> {
    Boolean value;

    public BoolLattice() {
        value = bot();
    }

    public BoolLattice(Boolean value) {
        this.value = value == null ? bot() : value;
    }

    @Override
    public Boolean bot() {
        return Boolean.FALSE;
    }

    @Override
    public Boolean value() {
        return value;
    }

    @Override
    public void merge(Boolean e) {
        if (e == null)
            e = bot();

        this.value |= e;
    }

    @Override
    public void assign(Boolean e) {
        if (e == null) {
            e = bot();
        }
        value = e;
    }

    @Override
    public ILattice<Boolean> cloneInstance() {
        return new BoolLattice(value);
    }
}
