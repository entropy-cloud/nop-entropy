/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.common.exceptions;

import io.nop.api.core.exceptions.NopException;

import static io.nop.ooxml.common.OfficeErrors.ARG_MSG;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_INVALID_FORMAT;

public class InvalidFormatException extends NopException {
    public InvalidFormatException(String message) {
        super(ERR_OOXML_INVALID_FORMAT);
        param(ARG_MSG, message);
    }
}
