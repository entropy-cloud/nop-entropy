/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical-entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration;

import io.nop.api.core.exceptions.ErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ErrorCode.define(errorCode, message, argNames...) has exactly one varargs
 * overload. Most constants in DbMigrationErrors were previously written in a
 * three-argument form that bound the module name as errorCode, the constant
 * name as message and the human-readable description as argNames[0], so every
 * error degenerated to the same error code "io.nop.db.migration".
 */
class TestDbMigrationErrors {

    @Test
    void testAllErrorCodesUseDistinctMachineReadableCodes() throws Exception {
        List<String> violations = new ArrayList<>();
        List<String> seenCodes = new ArrayList<>();

        for (Field field : DbMigrationErrors.class.getDeclaredFields()) {
            if (field.getType() != ErrorCode.class) {
                continue;
            }
            ErrorCode code = (ErrorCode) field.get(null);

            // the legacy misdefinition used the module name as the error code
            if (!code.getErrorCode().startsWith("nop.err.db-migration.")) {
                violations.add(field.getName() + " has errorCode=" + code.getErrorCode());
            }
            // the legacy misdefinition bound the constant name as the description
            if (code.getDescription() == null || code.getDescription().equals(field.getName())) {
                violations.add(field.getName() + " has no human-readable description");
            }
            seenCodes.add(code.getErrorCode());
        }

        assertTrue(violations.isEmpty(), "all error codes must be defined properly: " + violations);
        assertTrue(seenCodes.stream().distinct().count() == seenCodes.size(),
            "error codes must be distinct so failures can be located by code: " + seenCodes);
    }

    @Test
    void testArgNamesAreDeclaredParamsNotDescriptions() throws Exception {
        for (Field field : DbMigrationErrors.class.getDeclaredFields()) {
            if (field.getType() != ErrorCode.class) {
                continue;
            }
            // the legacy three-arg form leaked description sentences into argNames
            for (String argName : ((ErrorCode) field.get(null)).getArgNames()) {
                assertFalse(argName.contains(" ") || argName.contains(":"),
                    field.getName() + " has a non-identifier arg name: " + argName);
            }
        }
    }
}
