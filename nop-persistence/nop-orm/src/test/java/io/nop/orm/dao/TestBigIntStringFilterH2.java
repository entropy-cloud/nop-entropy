/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

/**
 * 方案 B 过滤参数绑定回归——缺省 H2（CI 无 Docker 也可跑）。
 * H2 有 varchar→bigint 隐式转换，E2E 修复前后都可执行；核心断言是参数绑定类型
 * （见 {@link AbstractBigIntStringFilterCase}）。
 */
public class TestBigIntStringFilterH2 extends AbstractBigIntStringFilterCase {
}
