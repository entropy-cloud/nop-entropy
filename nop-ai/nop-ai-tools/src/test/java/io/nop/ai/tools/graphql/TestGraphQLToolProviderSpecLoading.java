package io.nop.ai.tools.graphql;

import io.nop.ai.core.api.tool.ToolSpecification;
import io.nop.ai.core.api.tool.ToolSpecificationLoader;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FileTool 工具描述文件命名与加载约定回归（P2 模块卫生 round-3 裁定）：
 * <ul>
 *   <li>{@code FileTool_grepFiles.tool.json}（单下划线）曾与操作名 {@code FileTool__grepFiles}（双下划线）
 *       不符，{@link ToolSpecificationLoader} 拼 {@code /nop/ai/tools/{toolName}.tool.json} 永不命中，
 *       GraphQLToolProvider 静默回退 schema 派生描述——改名后必须命中 JSON 描述（英文），而非中文
 *       {@code @Description}。</li>
 *   <li>4 个死 {@code .task.json}（loadDslSchema/loadDslSchemaForFileType/loadDslFile/saveDslFile）无任何
 *       loader，已转为 {@code .tool.json} 接线——此处守护其可加载且描述非空。</li>
 * </ul>
 */
@NopTestConfig(testBeansFile = "/nop/ai/beans/ai-tools-defaults.beans.xml")
public class TestGraphQLToolProviderSpecLoading extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine engine;

    @Test
    public void testGrepFilesSpecLoadedAfterRename() {
        ToolSpecification spec = ToolSpecificationLoader.loadSpecification("FileTool__grepFiles");
        assertNotNull(spec, "FileTool__grepFiles.tool.json must be loadable after double-underscore rename");
        assertEquals("Search for lines matching a regular expression in multiple files",
                spec.getDescription(), "description must come from the .tool.json (not schema-derived fallback)");
    }

    @Test
    public void testFormerTaskJsonNowLoadableAsToolJson() {
        assertNotNull(ToolSpecificationLoader.loadSpecification("FileTool__loadDslSchema"),
                "FileTool__loadDslSchema.tool.json must be loadable");
        assertNotNull(ToolSpecificationLoader.loadSpecification("FileTool__loadDslSchemaForFileType"),
                "FileTool__loadDslSchemaForFileType.tool.json must be loadable");
        assertNotNull(ToolSpecificationLoader.loadSpecification("FileTool__loadDslFile"),
                "FileTool__loadDslFile.tool.json must be loadable");
        assertNotNull(ToolSpecificationLoader.loadSpecification("FileTool__saveDslFile"),
                "FileTool__saveDslFile.tool.json must be loadable");
    }

    @Test
    public void testGraphQLToolProviderUsesJsonDescriptionNotFallback() {
        // 端到端：GraphQLToolProvider.buildTool 经 loadToolSpec 命中 .tool.json 时
        // description 取 JSON（英文），静默回退路径则取 @Description（中文）。
        GraphQLToolProvider provider = new GraphQLToolProvider(engine);
        assertEquals("Search for lines matching a regular expression in multiple files",
                provider.getTool("FileTool__grepFiles").getDescription(),
                "provider must load the .tool.json description instead of schema-derived fallback");
    }

    @Test
    public void testNoToolJsonForUnknownOperation() {
        // 反例：无对应 .tool.json 的操作返回 null spec（不误伤既有回退语义）
        assertNull(ToolSpecificationLoader.loadSpecification("FileTool__noSuchOperation"));
    }
}