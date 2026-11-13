/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;

/**
 * C4-008 F2 夹具：init 阶段抛出 Error——ClassHelper.invoke 解包 InvocationTargetException 后
 * 原样重抛（NopException.adapt 对 Error 直接 throw），Error 逃逸 TaskExecutionGraph.runTask 的
 * catch(Exception)，图 future 永不完成（Error 孤儿图），由 join 超时档兜底收敛。
 * 注：构造器抛 Error 会被反射层 ITE 包装降级为普通 Exception（不构成孤儿形态），
 * 故 F2 注入点选在 init 阶段。
 */
public class TestStartFailErrorBean {
    @PostConstruct
    public void init() {
        throw new Error("simulated-init-error");
    }
}
