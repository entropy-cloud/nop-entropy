/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.messages;

import com.intellij.BundleBase;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.util.ResourceBundle;

public class NopPluginBundle {
    @NonNls
    private static final String BUNDLE = "io.nop.idea.plugin.messages.NopPluginBundle";
    private static Reference<ResourceBundle> ourBundle;

    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return message(getBundle(), key, params);
    }

    public static String key(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
        return getBundle().getString(key);
    }

    private NopPluginBundle() {
    }

    @NotNull
    private static ResourceBundle getBundle() {
        ResourceBundle bundle = SoftReference.dereference(ourBundle);
        if (bundle == null) {
            bundle = ResourceBundle.getBundle(BUNDLE);
            ourBundle = new java.lang.ref.SoftReference(bundle);
        }

        return bundle;
    }

    public static String messageOrDefault(@Nullable ResourceBundle bundle, @NotNull String key, @Nullable String defaultValue, @NotNull Object... params) {
        return BundleBase.messageOrDefault(bundle, key, defaultValue, params);
    }

    @NotNull
    public static String message(@NotNull ResourceBundle bundle, @NotNull String key, @NotNull Object... params) {
        return BundleBase.message(bundle, key, params);
    }

    @Nullable
    public static String messageOfNull(@NotNull ResourceBundle bundle, @NotNull String key, @NotNull Object... params) {
        String value = messageOrDefault(bundle, key, key, params);
        return key.equals(value) ? null : value;
    }
}
