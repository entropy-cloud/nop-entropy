package io.nop.ai.tools.file;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileTool 契约守护：13 个操作全部注册到 GraphQL schema，且 query/mutation 类别正确。
 * 防止"注解只放接口、实现类丢失注解"导致的静默失效（具体类不合并接口方法注解，
 * 生效面在实现类方法上）。
 */
@NopTestConfig(testBeansFile = "/nop/ai/beans/ai-tools-defaults.beans.xml")
public class TestFileToolBizRegistration extends JunitBaseTestCase {

    private static final List<String> QUERIES = List.of(
            "FileTool__readFiles", "FileTool__readFilePart", "FileTool__glob", "FileTool__globGrep",
            "FileTool__grep", "FileTool__grepFiles", "FileTool__loadDslSchema",
            "FileTool__loadDslSchemaForFileType", "FileTool__loadDslFile");

    private static final List<String> MUTATIONS = List.of(
            "FileTool__saveFile", "FileTool__saveFiles", "FileTool__mergeFile", "FileTool__saveDslFile");

    @Inject
    IGraphQLEngine engine;

    @Test
    public void testAllFileToolOperationsRegistered() {
        for (String name : QUERIES) {
            assertNotNull(engine.getOperationDefinition(GraphQLOperationType.query, name),
                    "query operation must be registered: " + name);
        }
        for (String name : MUTATIONS) {
            assertNotNull(engine.getOperationDefinition(GraphQLOperationType.mutation, name),
                    "mutation operation must be registered: " + name);
        }
        assertEquals(13, QUERIES.size() + MUTATIONS.size());
    }

    @Test
    public void testSaveFilesArgNameIsFileContents() {
        // AI-4 回归守护：@Name("String") 缺陷曾使调用方必须以 "String" 作为参数名
        GraphQLFieldDefinition def = engine.getOperationDefinition(GraphQLOperationType.mutation, "FileTool__saveFiles");
        assertNotNull(def);
        List<String> argNames = def.getArguments().stream()
                .map(a -> a.getName()).collect(Collectors.toList());
        assertTrue(argNames.contains("fileContents"), "argument must be named fileContents, got: " + argNames);
        assertFalse(argNames.contains("String"), "legacy wrong arg name String must be gone: " + argNames);
    }
}
