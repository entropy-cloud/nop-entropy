/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class DebugValueKey {
    private String name;
    private int index = -1;
    private String ownerClass;

    public DebugValueKey() {
    }

    public static DebugValueKey build(String name, int index, String ownerClass) {
        DebugValueKey ret = new DebugValueKey();
        ret.setName(name);
        ret.setIndex(index);
        ret.setOwnerClass(ownerClass);
        return ret;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOwnerClass() {
        return ownerClass;
    }

    public void setOwnerClass(String ownerClass) {
        this.ownerClass = ownerClass;
    }
}
