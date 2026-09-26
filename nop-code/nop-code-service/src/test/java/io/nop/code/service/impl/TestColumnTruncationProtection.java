package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.EdgeProvenance;
import io.nop.code.core.model.IHeuristicEdgeSynthesizer;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.impl.CodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.api.core.beans.query.QueryBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static io.nop.api.core.beans.FilterBeans.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for plan 362: analyzer-produced free-text fields (call.context,
 * call.metadata) must be truncated to the ORM column precision at the persistence
 * boundary instead of aborting the whole index transaction with sqlState 22001.
 *
 * <p>Drives the real indexDirectory transaction with a stub heuristic synthesizer
 * that emits a call with an over-long context (>2000, NopCodeCall.CONTEXT
 * precision) and metadata (>4096, jsonContent precision).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestColumnTruncationProtection extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @Test
    void overLongContextAndMetadataAreTruncatedNotFatal() {
        String longContext = "x.a()"
                + "b()".repeat(700); // 2200+ chars, exceeds CONTEXT precision 2000
        String longMetadata = "{\"k\":\""
                + "v".repeat(4200) + "\"}"; // > 4096 jsonContent precision

        CodeIndexService svc = (CodeIndexService) codeIndexService;
        svc.heuristicSynthesizers.add(new IHeuristicEdgeSynthesizer() {
            @Override
            public String getSynthesizerId() {
                return "test-long-context";
            }

            @Override
            public List<CodeMethodCall> synthesize(io.nop.code.core.model.HeuristicContext context) {
                CodeMethodCall call = new CodeMethodCall();
                call.setId(UUID.randomUUID().toString());
                call.setCallerId(UUID.randomUUID().toString());
                call.setCalleeId(UUID.randomUUID().toString());
                call.setMethodName("longChain");
                call.setContext(longContext);
                call.setMetadata(longMetadata);
                // camelCase column codes (CALL_TYPE) must be resolved via the property name too
                call.setCallType("java.util.List<java.lang.Object>");
                call.setProvenance(EdgeProvenance.HEURISTIC);
                return List.of(call);
            }
        });

        String indexId = "trunc-test";
        // must not throw: before the fix the over-long insert aborted the transaction
        int fileCount = codeIndexService.indexDirectory(indexId,
                Paths.get("src/test/resources/test-project/src/main/java").toString(), "**/*.java");
        assertTrue(fileCount > 0, "indexing the test project should succeed");

        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        QueryBean q = new QueryBean();
        q.addFilter(eq("indexId", indexId));
        List<?> calls = callDao.findAllByQuery(q);

        NopCodeCall persisted = calls.stream()
                .map(NopCodeCall.class::cast)
                .filter(c -> c.getContext() != null && c.getContext().startsWith("x.a()"))
                .findFirst().orElse(null);
        assertTrue(persisted != null, "the stub's over-long-context call must be persisted");

        assertTrue(persisted.getContext().length() <= 2000,
                "context must be truncated to column precision 2000, got " + persisted.getContext().length());
        assertTrue(persisted.getMetadata() != null && persisted.getMetadata().startsWith("{\"k\""),
                "the stub's over-long metadata must be persisted");
        assertTrue(persisted.getMetadata().length() <= 4096,
                "metadata must be truncated to column precision 4096, got " + persisted.getMetadata().length());
        // CALL_TYPE precision is 20; the 32-char generic type name must be truncated
        assertTrue(persisted.getCallType() == null || persisted.getCallType().length() <= 20,
                "callType must be truncated to column precision 20, got " + persisted.getCallType());
        assertFalse(calls.isEmpty(), "calls must be persisted for the index");
    }
}
