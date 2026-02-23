/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.server;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletionStage;

public interface IAsyncBody {
    CompletionStage<String> getTextAsync();

    default String getText() {
        return FutureHelper.syncGet(getTextAsync());
    }

    default Object getJson() {
        return JSON.parse(getText());
    }
}