/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.mfa;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 BizModel 公开方法为敏感操作（操作级 MFA，设计 §3.1）：方法执行前要求
 * 会话内二次验证（{@code mfaVerifyOperation} 验证后凭一次性票重试）。
 * <p>
 * 存在即敏感，无必需属性。构建期约束（违规即构建报错，fail-fast）：
 * <ul>
 *   <li>不得标注于 {@code @BizSubscription} 方法（订阅路径无请求-响应语义，静默绕过等于 fail-open）。</li>
 *   <li>不得与 {@code @Auth(publicAccess=true)} 同用（匿名方法无会话可验）。</li>
 * </ul>
 * 拦截判定由 executor 检查点委托 {@link IOperationMfaChecker} SPI 完成；
 * 未注册 checker 或 {@code nop.auth.operation-mfa.enabled=false} 时框架零介入。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MfaRequired {
}
