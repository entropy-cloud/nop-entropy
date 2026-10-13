package io.nop.ai.tools.mcp;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * WI15/plan 2452 registration guard (Rule #23): after AiFileTool moved from
 * nop-ai-mcp-server into nop-ai-tools, its VFS bean registration
 * (ai-mcp-server-defaults.beans.xml) must have moved with it and kept the
 * correct FQN — otherwise the spring-mcp-server MCP tool exit
 * (getBizObject("AiTool")) silently breaks. Follows the
 * TestFileToolBizRegistration precedent.
 */
@NopTestConfig(testBeansFile = "/nop/ai/beans/ai-mcp-server-defaults.beans.xml")
public class TestAiFileToolBizRegistration extends JunitBaseTestCase {

    private static final List<String> QUERIES = List.of(
            "AiTool__loadNopFileXDef", "AiTool__loadNopFile");
    private static final List<String> MUTATIONS = List.of(
            "AiTool__saveNopFile");

    @Inject
    IGraphQLEngine engine;

    @Inject
    AiFileTool aiFileTool;

    @Test
    public void testAiFileToolBeanResolvable() {
        assertNotNull(aiFileTool, "nopAiFileTool bean must be resolvable from the migrated VFS beans file");
    }

    @Test
    public void testAiFileToolOperationsRegistered() {
        for (String name : QUERIES) {
            assertNotNull(engine.getOperationDefinition(GraphQLOperationType.query, name),
                    "query operation must be registered: " + name);
        }
        for (String name : MUTATIONS) {
            assertNotNull(engine.getOperationDefinition(GraphQLOperationType.mutation, name),
                    "mutation operation must be registered: " + name);
        }
        assertEquals(3, QUERIES.size() + MUTATIONS.size());
    }
}
