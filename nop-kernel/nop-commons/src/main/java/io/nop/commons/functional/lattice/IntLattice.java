/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.lattice;

public class IntLattice implements ILattice<Integer> {
    private final LatticeMergeOp op;
    private Integer value;

    public IntLattice(LatticeMergeOp op, Integer value) {
        this.op = op;
        this.value = value;
    }

    public IntLattice(LatticeMergeOp op) {
        this(op, 0);
    }

    @Override
    public Integer bot() {
        return 0;
    }

    @Override
    public Integer value() {
        return value;
    }

    @Override
    public void merge(Integer e) {
        if (e == null)
            e = 0;

        value = merge(op, value, e);
    }

    Integer merge(LatticeMergeOp op, Integer v1, Integer v2) {
        switch (op) {
            case MIN:
                return Math.min(v1, v2);
            case MAX:
                return Math.max(v1, v2);
            case SUM:
                return v1 + v2;
            case OLD:
                return v1;
            case NEW:
                return v2;
            default:
                return v1 + v2;
        }
    }

    @Override
    public void assign(Integer e) {
        if (e == null)
            e = 0;
        this.value = e;
    }

    @Override
    public ILattice<Integer> cloneInstance() {
        return new IntLattice(op, value);
    }
}