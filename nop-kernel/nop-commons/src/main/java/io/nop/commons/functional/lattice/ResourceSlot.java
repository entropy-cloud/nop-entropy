/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.lattice;

/**
 * 资源槽
 */
public class ResourceSlot extends MapLattice<String, Integer> {

    public void define(String name, LatticeMergeOp op) {
        this.assign(name, op, 0);
    }

    public void assign(String name, LatticeMergeOp op, int value) {
        assign(name, new IntLattice(op, value));
    }

    public void put(String name, LatticeMergeOp op, int value) {
        put(name, new IntLattice(op, value));
    }
}