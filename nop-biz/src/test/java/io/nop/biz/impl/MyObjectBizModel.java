/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.impl;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;

@BizModel("MyObject")
public class MyObjectBizModel {
    @BizQuery
    public MyObject get(@Name("id") String id) {
        MyObject ret = new MyObject();
        ret.setName("ret_" + id);
        return ret;
    }

    @BizLoader
    public String extValue(@ContextSource MyObject obj) {
        return "ext_" + obj.getName();
    }

    @BizObjName("MyObject")
    public static class MyObject {
        private String name;
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
