/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.core.lang.eval.EvalRuntime;

/**
 * REPL 取消检查通道（D6 §3.2 `executeDestroyed` 槽位）：destroyed 分支执行器的
 * deadline/取消旗标检查点。实现归 item 14（REPL execute 注入）；一期为 null（纯委托直通）。
 *
 * <p>通道实现必须基于执行线程 ThreadLocal（脚本不可见）——通用编译器下 scope 键注入
 * 可被脚本按名读写删除，不可用（D6 §3.2 取消通道裁定）。
 */
public interface ReplDeadlineChannel {
    /**
     * 检查当前执行的 deadline/取消旗标；超时或取消时抛出取消异常（Error 系，
     * 绕过脚本 catch(Exception)——见 D6 §3.2 取消异常裁定）。
     */
    void checkDeadline(EvalRuntime rt);
}
