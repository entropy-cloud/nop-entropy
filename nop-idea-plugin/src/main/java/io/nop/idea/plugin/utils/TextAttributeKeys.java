/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.utils;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class TextAttributeKeys {
    public static final TextAttributesKey FUNC =
            createTextAttributesKey("XLANG_FUNC", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey ATTR =
            createTextAttributesKey("XLANG_ATTR", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey VALUE =
            createTextAttributesKey("XLANG_VALUE", DefaultLanguageHighlighterColors.LABEL);

    public static final TextAttributesKey TAG = createTextAttributesKey("XLANG_TAG", DefaultLanguageHighlighterColors.MARKUP_TAG);
}
