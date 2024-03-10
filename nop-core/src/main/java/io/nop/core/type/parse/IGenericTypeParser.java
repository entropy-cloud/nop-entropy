/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.io.stream.CharSequenceReader;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.List;

import static io.nop.core.CoreErrors.ERR_TYPE_TYPE_STRING_NOT_END_PROPERLY;

/**
 * 解析泛型类型定义，目前不支持递归类型
 */
public interface IGenericTypeParser {
    IGenericTypeParser intern(boolean intern);

    IGenericTypeParser rawTypeResolver(IRawTypeResolver resolver);

    default IGenericType parseFromText(SourceLocation loc, String text) {
        IGenericType predefined = PredefinedGenericTypes.getPredefinedType(text);
        if (predefined != null)
            return predefined;

        TextScanner sc = TextScanner.fromReader(loc, new CharSequenceReader(text));
        IGenericType type = parseGenericType(sc);
        if (!sc.isEnd())
            throw sc.newError(ERR_TYPE_TYPE_STRING_NOT_END_PROPERLY);
        return type;
    }

    List<IGenericType> parseGenericTypeList(SourceLocation loc, String text);

    IGenericType parseGenericType(TextScanner sc);
}