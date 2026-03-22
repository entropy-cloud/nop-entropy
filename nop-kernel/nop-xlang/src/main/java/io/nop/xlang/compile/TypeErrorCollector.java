/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.type.IGenericType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_ACTUAL;
import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ARG_NAME;
import static io.nop.xlang.XLangErrors.ARG_TYPE;
import static io.nop.xlang.XLangErrors.ERR_TYPE_INFER_ASSIGN_TYPE_MISMATCH;
import static io.nop.xlang.XLangErrors.ERR_TYPE_INFER_INCOMPATIBLE_TYPES;
import static io.nop.xlang.XLangErrors.ERR_TYPE_INFER_UNDEFINED_VARIABLE;

public class TypeErrorCollector {
    private final List<TypeError> errors = new ArrayList<>();
    private final List<TypeError> warnings = new ArrayList<>();

    public void error(SourceLocation loc, NopException error) {
        errors.add(new TypeError(loc, error));
    }

    public void warning(SourceLocation loc, NopException warning) {
        warnings.add(new TypeError(loc, warning));
    }

    public void typeMismatch(SourceLocation loc, IGenericType expected, IGenericType actual) {
        NopException error = new NopException(ERR_TYPE_INFER_ASSIGN_TYPE_MISMATCH)
                .param(ARG_EXPECTED, expected != null ? expected.getTypeName() : "unknown")
                .param(ARG_ACTUAL, actual != null ? actual.getTypeName() : "unknown");
        errors.add(new TypeError(loc, error));
    }

    public void undefinedVariable(SourceLocation loc, String name) {
        NopException error = new NopException(ERR_TYPE_INFER_UNDEFINED_VARIABLE)
                .param(ARG_NAME, name);
        errors.add(new TypeError(loc, error));
    }

    public void incompatibleTypes(SourceLocation loc, String message) {
        NopException error = new NopException(ERR_TYPE_INFER_INCOMPATIBLE_TYPES)
                .param(ARG_TYPE, message);
        errors.add(new TypeError(loc, error));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public List<TypeError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<TypeError> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void clear() {
        errors.clear();
        warnings.clear();
    }

    public void addAll(TypeErrorCollector other) {
        if (other != null) {
            errors.addAll(other.errors);
            warnings.addAll(other.warnings);
        }
    }

    public static class TypeError {
        private final SourceLocation location;
        private final NopException error;

        public TypeError(SourceLocation location, NopException error) {
            this.location = location;
            this.error = error;
        }

        public SourceLocation getLocation() {
            return location;
        }

        public NopException getError() {
            return error;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (location != null) {
                sb.append(location.toString()).append(": ");
            }
            if (error != null) {
                sb.append(error.getMessage());
            }
            return sb.toString();
        }
    }
}
