package io.nop.xlang.xdsl.action;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：BizActionGenHelper（WI0 快照 0% 靶点，64 行）。
 * 用测试用 fake IActionModel 驱动，验证 action 模型到 XPL biz action 节点的生成语义。
 */
public class TestBizActionGenHelper extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static SchemaImpl stringSchema() {
        SchemaImpl schema = new SchemaImpl();
        schema.setStdDomain("string");
        return schema;
    }

    private static FakeInput input(String name, boolean mandatory) {
        return new FakeInput(name, mandatory);
    }

    /**
     * getInputNames / getOutputNames 按 input/output 子节点的 name 属性提取。
     */
    @Test
    public void testGetInputAndOutputNames() {
        XNode node = XNode.make("action");
        // makeChild 会复用同名已有子节点，这里显式构造多个 input/output 子节点
        XNode input1 = XNode.make("input");
        input1.setAttr("name", "arg1");
        XNode input2 = XNode.make("input");
        input2.setAttr("name", "arg2");
        XNode output = XNode.make("output");
        output.setAttr("name", "RESULT");
        XNode other = XNode.make("other");
        other.setAttr("name", "notCounted");
        node.appendChild(input1);
        node.appendChild(input2);
        node.appendChild(output);
        node.appendChild(other);

        assertEquals(Arrays.asList("arg1", "arg2"), BizActionGenHelper.getInputNames(node));
        assertEquals(Collections.singletonList("RESULT"), BizActionGenHelper.getOutputNames(node));
    }

    /**
     * buildActionArgs：普通 input 生成 arg 节点并计入返回名单；
     * svcCtx 参数标记 kind=ServiceContext 且不进入返回名单；末尾追加 _selection 参数。
     */
    @Test
    public void testBuildActionArgs() {
        XNode node = XNode.make("action");
        FakeInputModel ctxInput = new FakeInputModel(CoreConstants.VAR_SVC_CTX, false, null);
        FakeInputModel normal = new FakeInputModel("userId", true, stringSchema());

        List<String> names = BizActionGenHelper.buildActionArgs(node,
                new FakeActionModel(Arrays.asList(normal, ctxInput), null));

        assertEquals(Collections.singletonList("userId"), names, "svcCtx 不得计入参数名单");
        assertEquals(3, node.getChildCount());

        XNode arg0 = node.child(0);
        assertEquals("arg", arg0.getTagName());
        assertEquals("userId", arg0.attrText("name"));
        assertEquals("true", arg0.attrText("mandatory"));

        XNode ctxArg = node.child(1);
        assertEquals(CoreConstants.VAR_SVC_CTX, ctxArg.attrText("name"));
        assertEquals("ServiceContext", ctxArg.attrText("kind"));

        XNode selectionArg = node.child(2);
        assertEquals("_selection", selectionArg.attrText("name"));
        assertEquals("FieldSelection", selectionArg.attrText("kind"));
    }

    /**
     * 模型已带 svcCtx 输入时不重复追加 ServiceContext 参数。
     */
    @Test
    public void testBuildActionArgsNoDuplicateSvcCtx() {
        XNode node = XNode.make("action");
        FakeInputModel ctxInput = new FakeInputModel(CoreConstants.VAR_SVC_CTX, false, null);

        BizActionGenHelper.buildActionArgs(node, new FakeActionModel(Collections.singletonList(ctxInput), null));

        long ctxCount = node.getChildren().stream()
                .filter(c -> CoreConstants.VAR_SVC_CTX.equals(c.attrText("name")))
                .count();
        assertEquals(1, ctxCount, "svcCtx 参数不得重复追加");
    }

    /**
     * buildBizActionFromActionModel：
     * - 缺省 displayName/description 从模型补齐；
     * - 原 arg 子节点被移除后按模型重建；
     * - RESULT 输出的 schema 直接作为 return 内容；
     * - source 子节点内容来自 sourceBuilder，useResult=true。
     */
    @Test
    public void testBuildBizActionFromActionModelWithResult() {
        XNode actionNode = XNode.make("action");
        actionNode.setAttr("name", "doIt");
        actionNode.makeChild("arg").setAttr("name", "old");

        FakeOutput resultOutput = new FakeOutput("RESULT", stringSchema());
        FakeInputModel input = new FakeInputModel("userId", true, stringSchema());
        FakeActionModel model = new FakeActionModel(Collections.singletonList(input),
                Collections.singletonList(resultOutput));
        model.displayName = "Do It";
        model.description = "desc";

        XNode ret = BizActionGenHelper.buildBizActionFromActionModel(actionNode, model,
                (argNames, useResult) -> {
                    assertEquals(Collections.singletonList("userId"), argNames);
                    org.junit.jupiter.api.Assertions.assertTrue(useResult, "存在 RESULT 输出时 useResult 必须为 true");
                    return "generated-source";
                });

        assertEquals("Do It", ret.attrText("displayName"), "displayName 缺省从模型补齐");
        assertEquals("desc", ret.attrText("description"));
        assertNull(ret.childByAttr("name", "old"), "原有 arg 子节点必须被移除后按模型重建");
        assertEquals("userId", ret.childByTag("arg").attrText("name"), "arg 按模型输入重建");

        XNode returnNode = ret.childByTag("return");
        assertNotNull(returnNode);
        XNode resultSchema = returnNode.childByTag("schema");
        assertNotNull(resultSchema, "RESULT schema 节点应挂到 return 下");
        assertEquals("string", resultSchema.attrText("stdDomain"));

        XNode source = ret.childByTag("source");
        assertEquals("generated-source", source.contentText());
    }

    /**
     * 无 RESULT 输出时使用聚合的 outputSchemaNode，且 useResult=false。
     */
    @Test
    public void testBuildBizActionFromActionModelWithoutResult() {
        XNode actionNode = XNode.make("action");

        FakeOutput plainOutput = new FakeOutput("value", stringSchema());
        FakeActionModel model = new FakeActionModel(null, Collections.singletonList(plainOutput));

        XNode ret = BizActionGenHelper.buildBizActionFromActionModel(actionNode, model,
                (argNames, useResult) -> {
                    assertTrue(argNames.isEmpty(), "无 inputs 时 argNames 为空表");
                    org.junit.jupiter.api.Assertions.assertFalse(useResult, "无 RESULT 输出时 useResult 必须为 false");
                    return "src";
                });

        XNode returnNode = ret.childByTag("return");
        assertNotNull(returnNode);
        assertNotNull(returnNode.childByTag("schema"), "无 RESULT 时应使用聚合 schema 节点");
        assertEquals("src", ret.childByTag("source").contentText());
    }

    static class FakeInput implements IActionInputModel {
        private final String name;
        private final boolean mandatory;

        FakeInput(String name, boolean mandatory) {
            this.name = name;
            this.mandatory = mandatory;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public boolean isMandatory() {
            return mandatory;
        }

        @Override
        public ISchema getSchema() {
            return null;
        }

        @Override
        public Object prop_get(String propName) {
            return null;
        }

        @Override
        public boolean prop_has(String propName) {
            return false;
        }
    }

    static class FakeInputModel extends FakeInput {
        private final ISchema schema;

        FakeInputModel(String name, boolean mandatory, ISchema schema) {
            super(name, mandatory);
            this.schema = schema;
        }

        @Override
        public ISchema getSchema() {
            return schema;
        }
    }

    static class FakeOutput implements IActionOutputModel {
        private final String name;
        private final ISchema schema;

        FakeOutput(String name, ISchema schema) {
            this.name = name;
            this.schema = schema;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public io.nop.core.type.IGenericType getType() {
            return schema == null ? null : schema.getType();
        }

        @Override
        public ISchema getSchema() {
            return schema;
        }

        @Override
        public Object prop_get(String propName) {
            return null;
        }

        @Override
        public boolean prop_has(String propName) {
            return false;
        }
    }

    static class FakeActionModel implements IActionModel {
        final List<? extends IActionInputModel> inputs;
        final List<? extends IActionOutputModel> outputs;
        String displayName;
        String description;

        FakeActionModel(List<? extends IActionInputModel> inputs,
                        List<? extends IActionOutputModel> outputs) {
            this.inputs = inputs;
            this.outputs = outputs;
        }

        @Override
        public String getName() {
            return "fake";
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public List<? extends IActionInputModel> getInputs() {
            return inputs;
        }

        @Override
        public List<? extends IActionOutputModel> getOutputs() {
            return outputs;
        }
    }
}
