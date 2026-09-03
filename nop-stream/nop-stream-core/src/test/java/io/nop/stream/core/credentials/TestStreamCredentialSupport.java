/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.credentials;

import java.sql.Timestamp;

import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-14, D4): the {@code credential:{id}#{field}} reference syntax and
 * its fail-closed resolution contract (missing provider / unknown credential /
 * malformed reference all raise typed errors — never a silent empty string).
 */
public class TestStreamCredentialSupport {

    private static final ICredentialProvider PROVIDER = new ICredentialProvider() {
        @Override
        public io.nop.credential.api.CredentialData getCredential(String credentialId) {
            throw missing();
        }

        @Override
        public Object getCredentialData(String credentialId, String field) {
            if ("mysql-prod".equals(credentialId) && "password".equals(field)) {
                return "secret-value";
            }
            throw missing();
        }

        @Override
        public TestResult testCredential(String credentialId) {
            return new TestResult(false, "test not implemented", new Timestamp(0L));
        }

        @Override
        public MaskedCredential mask(String credentialId) {
            throw missing();
        }

        @Override
        public void registerUsage(String credentialId, String consumerRef) {
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }

        private static NopException missing() {
            NopException ex = new NopException(io.nop.api.core.ApiErrors.ERR_WRAP_EXCEPTION);
            ex.description("credential does not exist or has been soft-deleted");
            return ex;
        }
    };

    @Test
    public void recognizesReferenceSyntax() {
        assertTrue(StreamCredentialSupport.isCredentialReference("credential:mysql-prod#password"));
        // Prefix detection: "credential:" alone counts as a reference attempt — its
        // malformedness is rejected by parse() with a typed error.
        assertTrue(StreamCredentialSupport.isCredentialReference("credential:"));
        assertFalse(StreamCredentialSupport.isCredentialReference("mysql-prod#password"));
        assertFalse(StreamCredentialSupport.isCredentialReference(null));
        assertFalse(StreamCredentialSupport.isCredentialReference(""));
    }

    @Test
    public void parsesValidReference() {
        StreamCredentialSupport.CredentialReference ref =
                StreamCredentialSupport.parse("credential:mysql-prod#password", "databasePassword");
        assertEquals("mysql-prod", ref.credentialId());
        assertEquals("password", ref.field());
    }

    @Test
    public void malformedReferencesFailFastWithTypedError() {
        for (String bad : new String[]{"credential:no-hash", "credential:#field",
                "credential:id#", "not-a-ref"}) {
            StreamException ex = assertThrows(StreamException.class,
                    () -> StreamCredentialSupport.parse(bad, "databasePassword"),
                    "must reject: " + bad);
            assertEquals("nop.err.stream.credential-ref-invalid", ex.getErrorCode());
        }
    }

    @Test
    public void resolvesThroughProvider() {
        assertEquals("secret-value", StreamCredentialSupport.resolve(
                "credential:mysql-prod#password", "databasePassword", PROVIDER));
    }

    @Test
    public void nullProviderFailsClosed() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamCredentialSupport.resolve("credential:mysql-prod#password",
                        "databasePassword", null));
        assertEquals("nop.err.stream.credential-provider-missing", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("fail-closed"), ex.getMessage());
    }

    @Test
    public void unknownCredentialFailsClosed() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamCredentialSupport.resolve("credential:ghost#password",
                        "databasePassword", PROVIDER));
        assertEquals("nop.err.stream.credential-unresolved", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("ghost"), ex.getMessage());
    }

    @Test
    public void unsetFieldResolvesToNullWithoutSilentSubstitution() {
        // A configured-but-unset credential field resolves to null (the caller decides
        // whether that is acceptable) — the provider was still consulted successfully.
        ICredentialProvider nullField = new ICredentialProvider() {
            @Override
            public io.nop.credential.api.CredentialData getCredential(String credentialId) {
                throw new UnsupportedOperationException("not part of this test path");
            }

            @Override
            public Object getCredentialData(String credentialId, String field) {
                return null;
            }

            @Override
            public TestResult testCredential(String credentialId) {
                throw new UnsupportedOperationException("not part of this test path");
            }

            @Override
            public MaskedCredential mask(String credentialId) {
                throw new UnsupportedOperationException("not part of this test path");
            }

            @Override
            public void registerUsage(String credentialId, String consumerRef) {
            }

            @Override
            public void unregisterUsage(String credentialId, String consumerRef) {
            }
        };
        assertNull(StreamCredentialSupport.resolve("credential:x#f", "databasePassword", nullField));
    }
}
