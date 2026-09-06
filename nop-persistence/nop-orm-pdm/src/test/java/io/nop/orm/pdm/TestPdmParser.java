/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.pdm;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static io.nop.orm.pdm.PdmModelErrors.ERR_PDM_ELEMENT_MISSING_NAME_OR_CODE;
import static io.nop.orm.pdm.PdmModelErrors.ERR_PDM_PRIMARY_KEY_NO_KEY_REF;
import static io.nop.orm.pdm.PdmModelErrors.ERR_PDM_REFERENCE_NO_JOIN_COLUMN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestPdmParser extends BaseTestCase {
    @BeforeAll
    public static void init() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        IResource resource = attachmentResource("demo.pdm");
        PdmModelParser parser = new PdmModelParser();
        OrmModel ormModel = parser.parseFromResource(resource);

        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/orm/orm.xdef");
        XNode node = new DslModelToXNodeTransformer(objMeta).transformToXNode(ormModel);
        node.dump();
        assertEquals(normalizeCRLF(attachmentXml("demo.orm.xml").xml()), normalizeCRLF(node.xml()));

        String jsonText = JsonTool.stringify(ormModel, null, "  ");
        System.out.println(jsonText);

        Map<String, Object> json = (Map<String, Object>) JsonTool.parse(JsonTool.stringify(ormModel));
        XNode node2 = new DslModelToXNodeTransformer(objMeta).transformToXNode(json);
        node2.dump();
        assertEquals(node2.xml(), node.xml());
    }

    @Test
    public void testRelation() {
        IResource resource = attachmentResource("test-relation.pdm");
        PdmModelParser parser = new PdmModelParser();
        OrmModel ormModel = parser.parseFromResource(resource);

        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/orm/orm.xdef", ormModel);
        node.dump();
        assertEquals(normalizeCRLF(attachmentXml("test-relation.orm.xml").xml()),
                normalizeCRLF(node.xml()));
    }

    @Test
    public void testParseKeysMissingKeyRef() {
        // 手工裁剪过的 PDM 可能出现 <c:PrimaryKey> 下没有 <c:Key> 子节点
        XNode tableNode = XNode.make("o:Table");
        XNode primaryKey = tableNode.makeChild("c:PrimaryKey");
        primaryKey.makeChild("a:Code").setValue("PK1");

        OrmEntityModel table = new OrmEntityModel();
        table.setTableName("tbl_x");

        PdmModelParser parser = new PdmModelParser();
        NopException e = assertThrows(NopException.class, () -> parser.parseKeys(tableNode, table));
        assertEquals(ERR_PDM_PRIMARY_KEY_NO_KEY_REF.getErrorCode(), e.getErrorCode());
    }

    // ---- 最小 PDM 构造工具（Java 11 兼容，无文本块） ----

    static OrmModel parsePdmText(String pdm) {
        IResource resource = new InMemoryTextResource("/test.pdm", pdm);
        return new PdmModelParser().parseFromResource(resource);
    }

    static String tablesPdm(String tablesXml, String viewsXml, String referencesXml) {
        return String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Model xmlns:a=\"attribute\" xmlns:c=\"collection\" xmlns:o=\"object\">",
                "<o:RootObject Id=\"o1\">",
                "<c:Children>",
                "<o:Model Id=\"o2\">",
                "<a:Name>test</a:Name>",
                "<a:Code>test</a:Code>",
                "<c:Tables>",
                tablesXml,
                "</c:Tables>",
                "<c:Views>",
                viewsXml,
                "</c:Views>",
                "<c:References>",
                referencesXml,
                "</c:References>",
                "</o:Model>",
                "</c:Children>",
                "</o:RootObject>",
                "</Model>");
    }

    static final String PARENT_TABLE = String.join("\n",
            "<o:Table Id=\"t1\">",
            "<a:Name>parent</a:Name>",
            "<a:Code>t_parent</a:Code>",
            "<c:Columns>",
            "<o:Column Id=\"c1\">",
            "<a:Name>id</a:Name>",
            "<a:Code>ID</a:Code>",
            "<a:DataType>bigint</a:DataType>",
            "</o:Column>",
            "</c:Columns>",
            "<c:Keys>",
            "<o:Key Id=\"k1\">",
            "<a:Name>Key_1</a:Name>",
            "<a:Code>Key_1</a:Code>",
            "<c:Key.Columns>",
            "<o:Column Ref=\"c1\"/>",
            "</c:Key.Columns>",
            "</o:Key>",
            "</c:Keys>",
            "<c:PrimaryKey>",
            "<o:Key Ref=\"k1\"/>",
            "</c:PrimaryKey>",
            "</o:Table>");

    static final String CHILD_TABLE = String.join("\n",
            "<o:Table Id=\"t2\">",
            "<a:Name>child</a:Name>",
            "<a:Code>t_child</a:Code>",
            "<c:Columns>",
            "<o:Column Id=\"c2\">",
            "<a:Name>id</a:Name>",
            "<a:Code>ID</a:Code>",
            "<a:DataType>bigint</a:DataType>",
            "</o:Column>",
            "<o:Column Id=\"c3\">",
            "<a:Name>pid</a:Name>",
            "<a:Code>PID</a:Code>",
            "<a:DataType>bigint</a:DataType>",
            "</o:Column>",
            "</c:Columns>",
            "<c:Keys>",
            "<o:Key Id=\"k2\">",
            "<a:Name>Key_1</a:Name>",
            "<a:Code>Key_1</a:Code>",
            "<c:Key.Columns>",
            "<o:Column Ref=\"c2\"/>",
            "</c:Key.Columns>",
            "</o:Key>",
            "</c:Keys>",
            "<c:PrimaryKey>",
            "<o:Key Ref=\"k2\"/>",
            "</c:PrimaryKey>",
            "</o:Table>");

    static String referenceWithJoins(String joinsXml) {
        return String.join("\n",
                "<o:Reference Id=\"r1\">",
                "<a:Name>Ref_1</a:Name>",
                "<a:Code>Ref_1</a:Code>",
                "<c:ParentTable>",
                "<o:Table Ref=\"t1\"/>",
                "</c:ParentTable>",
                "<c:ChildTable>",
                "<o:Table Ref=\"t2\"/>",
                "</c:ChildTable>",
                "<c:Joins>",
                joinsXml,
                "</c:Joins>",
                "</o:Reference>");
    }

    static final String MALFORMED_JOIN = String.join("\n",
            "<o:ReferenceJoin Id=\"j1\">",
            "<a:Name>j1</a:Name>",
            "</o:ReferenceJoin>");

    static final String VALID_JOIN = String.join("\n",
            "<o:ReferenceJoin Id=\"j2\">",
            "<c:Object1>",
            "<o:Column Ref=\"c1\"/>",
            "</c:Object1>",
            "<c:Object2>",
            "<o:Column Ref=\"c3\"/>",
            "</c:Object2>",
            "</o:ReferenceJoin>");

    @Test
    public void testAddJoinMalformedJoinSkipped() {
        // 一条 join 缺少 Object 节点时只跳过该条，不能丢弃其余合法 join
        OrmModel model = parsePdmText(tablesPdm(PARENT_TABLE + "\n" + CHILD_TABLE, "",
                referenceWithJoins(MALFORMED_JOIN + "\n" + VALID_JOIN)));

        // 修复前：循环内 return 导致合法 join 也被丢弃，随后 isAllPrimaryCol(null) NPE
        IEntityModel child = model.getEntityModelByTableName("t_child");
        assertEquals(1, child.getRelations().size());
        assertEquals(1, child.getRelations().get(0).getJoin().size());
    }

    @Test
    public void testAllJoinsMalformedThrowsWithContext() {
        // 整个引用没有任何可解析的 join 条件时，给出带表名的明确错误而不是裸 NPE/IndexOutOfBounds
        NopException e = assertThrows(NopException.class, () ->
                parsePdmText(tablesPdm(PARENT_TABLE + "\n" + CHILD_TABLE, "", referenceWithJoins(MALFORMED_JOIN))));
        assertEquals(ERR_PDM_REFERENCE_NO_JOIN_COLUMN.getErrorCode(), e.getErrorCode());
        assertEquals("t_child", e.getParam("childTableName"));
        assertEquals("t_parent", e.getParam("parentTableName"));
    }

    @Test
    public void testParseTableMissingNameThrows() {
        String tableNoName = PARENT_TABLE.replace("<a:Name>parent</a:Name>\n", "");
        NopException e = assertThrows(NopException.class,
                () -> parsePdmText(tablesPdm(tableNoName + "\n" + CHILD_TABLE, "", "")));
        assertEquals(ERR_PDM_ELEMENT_MISSING_NAME_OR_CODE.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testParseTableMissingCodeThrows() {
        String tableNoCode = PARENT_TABLE.replace("<a:Code>t_parent</a:Code>\n", "");
        NopException e = assertThrows(NopException.class,
                () -> parsePdmText(tablesPdm(tableNoCode + "\n" + CHILD_TABLE, "", "")));
        assertEquals(ERR_PDM_ELEMENT_MISSING_NAME_OR_CODE.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testColumnCodeUppercaseUsesLocaleRoot() {
        // tr 等 Locale 下 'i'.toUpperCase() 会得到 'İ'，污染生成到 orm.xml 的列 code
        String tableWithLowerColCode = String.join("\n",
                "<o:Table Id=\"t1\">",
                "<a:Name>parent</a:Name>",
                "<a:Code>t_case</a:Code>",
                "<c:Columns>",
                "<o:Column Id=\"c1\">",
                "<a:Name>input</a:Name>",
                "<a:Code>input</a:Code>",
                "<a:DataType>varchar(50)</a:DataType>",
                "</o:Column>",
                "</c:Columns>",
                "<c:Keys>",
                "<o:Key Id=\"k1\">",
                "<a:Name>Key_1</a:Name>",
                "<a:Code>Key_1</a:Code>",
                "<c:Key.Columns>",
                "<o:Column Ref=\"c1\"/>",
                "</c:Key.Columns>",
                "</o:Key>",
                "</c:Keys>",
                "<c:PrimaryKey>",
                "<o:Key Ref=\"k1\"/>",
                "</c:PrimaryKey>",
                "</o:Table>");

        Locale oldLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            OrmModel model = parsePdmText(tablesPdm(tableWithLowerColCode, "", ""));
            // 修复前 toUpperCase() 用默认 Locale，'input' 会变成 'İNPUT'
            assertEquals("INPUT", model.getEntityModelByTableName("t_case").getColumns().get(0).getCode());
        } finally {
            Locale.setDefault(oldLocale);
        }
    }

    @Test
    public void testReferenceToViewNoPkSkipped() {
        // 指向无主键视图的引用应随视图剔除而被跳过，而不是在 ORM 模型初始化时报未知实体。
        // 注意：视图必须位于 <c:Packages> 下才会被 parseViews 解析
        String viewNoPk = String.join("\n",
                "<o:View Id=\"v1\">",
                "<a:Name>view1</a:Name>",
                "<a:Code>v_view1</a:Code>",
                "<c:Columns>",
                "<o:Column Id=\"vc1\">",
                "<a:Name>data</a:Name>",
                "<a:Code>DATA</a:Code>",
                "<a:DataType>varchar(50)</a:DataType>",
                "</o:Column>",
                "</c:Columns>",
                "</o:View>");
        String refToView = String.join("\n",
                "<o:Reference Id=\"r1\">",
                "<a:Name>Ref_1</a:Name>",
                "<a:Code>Ref_1</a:Code>",
                "<c:ParentTable>",
                "<o:Table Ref=\"v1\"/>",
                "</c:ParentTable>",
                "<c:ChildTable>",
                "<o:Table Ref=\"t1\"/>",
                "</c:ChildTable>",
                "<c:Joins>",
                "<o:ReferenceJoin Id=\"j1\">",
                "<c:Object1>",
                "<o:Column Ref=\"vc1\"/>",
                "</c:Object1>",
                "<c:Object2>",
                "<o:Column Ref=\"c1\"/>",
                "</c:Object2>",
                "</o:ReferenceJoin>",
                "</c:Joins>",
                "</o:Reference>");
        String pdm = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Model xmlns:a=\"attribute\" xmlns:c=\"collection\" xmlns:o=\"object\">",
                "<o:RootObject Id=\"o1\">",
                "<c:Children>",
                "<o:Model Id=\"o2\">",
                "<a:Name>test</a:Name>",
                "<a:Code>test</a:Code>",
                "<c:Packages>",
                "<o:Package Id=\"p1\">",
                "<a:Name>pkg1</a:Name>",
                "<a:Code>pkg1</a:Code>",
                "<c:Tables>",
                PARENT_TABLE,
                "</c:Tables>",
                "<c:Views>",
                viewNoPk,
                "</c:Views>",
                "<c:References>",
                refToView,
                "</c:References>",
                "</o:Package>",
                "</c:Packages>",
                "</o:Model>",
                "</c:Children>",
                "</o:RootObject>",
                "</Model>");

        OrmModel model = parsePdmText(pdm);
        // 修复前：视图从 entities 移除但 tables 残留，引用仍生成，
        // 模型初始化报与根因脱节的 ERR_ORM_MODEL_REF_UNKNOWN_ENTITY
        assertNull(model.getEntityModelByTableName("v_view1"));
        IEntityModel parent = model.getEntityModelByTableName("t_parent");
        assertEquals(0, parent.getRelations().size());
    }
}
