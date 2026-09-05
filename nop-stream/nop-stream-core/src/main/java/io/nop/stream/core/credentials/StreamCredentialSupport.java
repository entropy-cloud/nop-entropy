/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.credentials;

import io.nop.credential.api.ICredentialProvider;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CREDENTIAL_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_FIELD_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_FIELD_VALUE;

/**
 * Item 20 (P-REQ-14, pre-submit-validation-design.md D4): the {@code credential:}
 * reference syntax and its fail-closed resolution through the platform's single
 * decryption point {@link ICredentialProvider} (kms-vault and other backends stay
 * transparent behind that interface).
 *
 * <p>Syntax: {@code credential:{credentialId}#{field}} — e.g.
 * {@code credential:mysql-prod#password}. The REFERENCE STRING persists inside the
 * Serializable connector configuration (so checkpoint/Java-serialization paths never
 * carry plaintext and a cross-JVM restore can re-resolve); decryption happens only
 * on the engine-side transient path (a local decrypted copy handed to the engine).
 *
 * <p>Fail-closed semantics: a reference with no available provider, or a credential
 * that does not exist / has been soft-deleted, raises a typed
 * {@link StreamException} — never a silent empty string.
 */
public final class StreamCredentialSupport {

    public static final String REFERENCE_PREFIX = "credential:";

    private StreamCredentialSupport() {
    }

    /** True when the value is a credential reference ({@code credential:{id}#{field}}). */
    public static boolean isCredentialReference(String value) {
        return value != null && value.startsWith(REFERENCE_PREFIX);
    }

    /** One parsed {@code credential:{credentialId}#{field}} reference. */
    public record CredentialReference(String credentialId, String field) {
    }

    /**
     * Parses a reference string; malformed references raise
     * {@code ERR_STREAM_CREDENTIAL_REF_INVALID} naming the field and the offending
     * value (fail-fast, never null-return).
     */
    public static CredentialReference parse(String fieldValue, String fieldName) {
        if (!isCredentialReference(fieldValue)) {
            throw refInvalid(fieldValue, fieldName, "missing '" + REFERENCE_PREFIX + "' prefix");
        }
        String body = fieldValue.substring(REFERENCE_PREFIX.length());
        int hash = body.indexOf('#');
        if (hash <= 0 || hash >= body.length() - 1) {
            throw refInvalid(fieldValue, fieldName,
                    "expected syntax " + REFERENCE_PREFIX + "{credentialId}#{field}");
        }
        String credentialId = body.substring(0, hash);
        String field = body.substring(hash + 1);
        if (credentialId.isBlank() || field.isBlank()) {
            throw refInvalid(fieldValue, fieldName,
                    "credentialId and field must both be non-empty");
        }
        return new CredentialReference(credentialId, field);
    }

    /**
     * Resolves one reference to its decrypted plaintext via the provider.
     * Fail-closed: a null provider (reference present but provider not injected) or
     * a provider failure (credential missing / soft-deleted) raises a typed error.
     * Returns the decrypted value (may be null when the credential field is unset).
     */
    public static String resolve(String fieldValue, String fieldName, ICredentialProvider provider) {
        CredentialReference ref = parse(fieldValue, fieldName);
        if (provider == null) {
            StreamException ex = new StreamException(NopStreamErrors.ERR_STREAM_CREDENTIAL_PROVIDER_MISSING);
            ex.param(ARG_FIELD_NAME, fieldName);
            ex.param(ARG_FIELD_VALUE, fieldValue);
            throw ex;
        }
        try {
            Object data = provider.getCredentialData(ref.credentialId(), ref.field());
            return data != null ? data.toString() : null;
        } catch (RuntimeException e) {
            StreamException ex = new StreamException(NopStreamErrors.ERR_STREAM_CREDENTIAL_UNRESOLVED, e);
            ex.param(ARG_CREDENTIAL_ID, ref.credentialId());
            ex.param(ARG_FIELD_NAME, fieldName);
            ex.param(ARG_FIELD_VALUE, fieldValue);
            ex.param(NopStreamErrors.ARG_DETAIL, e.getMessage() != null ? e.getMessage()
                    : String.valueOf(e));
            throw ex;
        }
    }

    private static StreamException refInvalid(String fieldValue, String fieldName, String detail) {
        StreamException ex = new StreamException(NopStreamErrors.ERR_STREAM_CREDENTIAL_REF_INVALID);
        ex.param(ARG_FIELD_NAME, fieldName);
        ex.param(ARG_FIELD_VALUE, fieldValue);
        ex.param(ARG_CREDENTIAL_ID, "-");
        ex.param(NopStreamErrors.ARG_DETAIL, detail);
        return ex;
    }
}
