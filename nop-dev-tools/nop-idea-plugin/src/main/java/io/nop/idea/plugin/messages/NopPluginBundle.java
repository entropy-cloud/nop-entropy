/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.messages;

import java.util.function.Supplier;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class NopPluginBundle {
    public static final @NonNls String BUNDLE = "messages.NopPluginBundle";

    private static final DynamicBundle INSTANCE = new DynamicBundle(NopPluginBundle.class, BUNDLE);

    public static @NotNull @Nls String message(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params
    ) {
        return INSTANCE.getMessage(key, params);
    }

    public static @NotNull Supplier<@Nls String> messagePointer(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params
    ) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
