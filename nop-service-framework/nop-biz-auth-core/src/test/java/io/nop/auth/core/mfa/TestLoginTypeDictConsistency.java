/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa;

import io.nop.auth.api.AuthApiConstants;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 consistency test: the {@code auth/login-type} dictionary must stay
 * aligned with {@link AuthApiConstants} login-type constants (design §二 已知不一致).
 * Guards against the historical SSO 4/10 drift recurring.
 */
public class TestLoginTypeDictConsistency {

    @SuppressWarnings("unchecked")
    private Set<Integer> loadDictValues() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/_vfs/dict/auth/login-type.dict.yaml")) {
            assertNotNull(in, "login-type.dict.yaml must be on the classpath");
            Map<String, Object> root = new Yaml().load(in);
            assertNotNull(root);
            List<Map<String, Object>> options = (List<Map<String, Object>>) root.get("options");
            assertNotNull(options);
            Set<Integer> values = new HashSet<>();
            for (Map<String, Object> opt : options) {
                Object v = opt.get("value");
                assertTrue(v instanceof Number, "option value must be numeric: " + opt);
                values.add(((Number) v).intValue());
            }
            return values;
        }
    }

    @Test
    void dictContainsAllLoginTypeConstantsAndSsoIsFour() throws Exception {
        Set<Integer> values = loadDictValues();

        // all constants declared in AuthApiConstants must be present
        assertTrue(values.contains(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD), "missing 1");
        assertTrue(values.contains(AuthApiConstants.LOGIN_TYPE_EMAIL_PASSWORD), "missing 2");
        assertTrue(values.contains(AuthApiConstants.LOGIN_TYPE_PHONE_PASSWORD), "missing 3");
        assertTrue(values.contains(AuthApiConstants.LOGIN_TYPE_SSO), "missing 4 (SSO)");
        assertTrue(values.contains(AuthApiConstants.LOGIN_TYPE_PHONE_SMS), "missing 5 (PHONE_SMS)");

        // SSO must be 4, not the historical drift value 10
        assertEquals(4, AuthApiConstants.LOGIN_TYPE_SSO);
        assertTrue(values.contains(4), "SSO option value must be 4");

        // the historical drift value 10 must NOT be present anymore
        assertFalse(values.contains(10), "SSO=10 drift must be removed; SSO is 4 per AuthApiConstants");

        assertNull(null); // noop to keep static-import used
    }
}
