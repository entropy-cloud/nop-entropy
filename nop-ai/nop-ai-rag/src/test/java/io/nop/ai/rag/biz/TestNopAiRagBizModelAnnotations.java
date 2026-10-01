package io.nop.ai.rag.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.directive.Auth;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G3-07-01 regression at the annotation level: the bizObjName must be a legal GraphQL Name
 * (no '/' prefix — the historical {@code @BizModel("/NopAiRag")} produced an illegal GraphQL
 * operation name) and the write action {@code ingestDocument} must carry {@code @Auth} metadata
 * (auth == null means public access per {@code GraphQLActionAuthChecker.isAllowAccess}).
 *
 * <p><b>Assertion level adjudication (plan 2283, F-2283-6)</b>: the nop-ai-rag test infrastructure
 * has no IoC container / GraphQL pipeline; these tests assert annotation/tool-class level facts via
 * reflection and deliberately do NOT set up a GraphQL pipeline.
 */
public class TestNopAiRagBizModelAnnotations {

    /** GraphQL Name charset: [_A-Za-z][_0-9A-Za-z]* — '/' is not a legal name character. */
    private static final Pattern GRAPHQL_NAME_PATTERN = Pattern.compile("^[_A-Za-z][_0-9A-Za-z]*$");

    /**
     * bizObjName must be a legal GraphQL identifier: non-empty, no '/' prefix, matching the
     * GraphQL Name charset. Guards against regression to the historical "/NopAiRag" form.
     */
    @Test
    public void bizObjNameIsLegalGraphQLIdentifier() {
        BizModel bizModel = NopAiRagBizModel.class.getAnnotation(BizModel.class);
        assertNotNull(bizModel, "@BizModel annotation must be present on NopAiRagBizModel");
        String bizObjName = bizModel.value();
        assertNotNull(bizObjName, "bizObjName must not be null");
        assertFalse(bizObjName.trim().isEmpty(), "bizObjName must not be empty");
        assertFalse(bizObjName.startsWith("/"),
                "bizObjName must not carry a '/' prefix (G3-07-01), got: " + bizObjName);
        assertTrue(GRAPHQL_NAME_PATTERN.matcher(bizObjName).matches(),
                "bizObjName must match GraphQL Name charset [_A-Za-z][_0-9A-Za-z]*, got: " + bizObjName);
    }

    /**
     * The write action ingestDocument must declare @Auth metadata with an explicit permission or
     * role requirement, and must not be marked publicAccess. G3-07-01: before the fix the action
     * had no @Auth, leaving RAG index writes open to any authenticated caller (search-result
     * poisoning surface).
     */
    @Test
    public void ingestDocumentCarriesAuthMetadata() throws Exception {
        Method ingest = NopAiRagBizModel.class.getMethod("ingestDocument",
                String.class, String.class, String.class);
        assertNotNull(ingest.getAnnotation(BizMutation.class),
                "ingestDocument must remain a @BizMutation (write operation)");
        Auth auth = ingest.getAnnotation(Auth.class);
        assertNotNull(auth,
                "write action ingestDocument must carry @Auth metadata (G3-07-01)");
        assertTrue(!auth.permissions().isEmpty() || !auth.roles().isEmpty(),
                "@Auth on ingestDocument must declare permissions or roles, got: "
                        + "permissions='" + auth.permissions() + "' roles='" + auth.roles() + "'");
        assertFalse(auth.publicAccess(),
                "write action ingestDocument must not be marked publicAccess");
    }
}
