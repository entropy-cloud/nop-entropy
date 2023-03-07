/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.api.actor;

import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;

/**
 * 
 * @author canonical_entropy@163.com
 */
@Locale("zh-CN")
public enum WfAssignmentSelection {
	@Label("系统自动选择")
	auto,

	@Label("单选")
	single,

	@Label("多选")
	multiple,
}