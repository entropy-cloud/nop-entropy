/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

/**
 * 流式缓冲溢出错误（plan 2509 R2-06 裁定⑤(a)：越窗缓冲硬上界同 bufferSize，溢出
 * fail-loud = 不可重试终态）。宿主收到本类型错误必须直接断流（onError 转发 + 上游
 * cancel 回收），不得进入失败分类/重试/重订阅决策（慢客户端溢出若按可切换失败处理
 * 将重新调用上游、污染熔断记账，与 fail-loud 相悖）。
 */
public class StreamingBufferOverflowException extends RuntimeException {

    private static final long serialVersionUID = -8294643851325096457L;

    public StreamingBufferOverflowException(String message) {
        super(message);
    }
}
