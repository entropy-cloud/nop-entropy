/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.json.JSON;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.parse.JsonParser;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.utils.GenericTypeHelper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsonTool {

    @Test
    public void testParse() {
        JSON.registerProvider(JsonTool.instance());

        Object o = JSON.parseNonStrict(null, "{a:1}");
        String str = JSON.stringify(o);
        assertEquals("{\"a\":1}", str);
        assertEquals(o, JSON.parse(str));
    }

    @Test
    public void testJsonIgnore() {
        String text = "{$type:'eq',value:'ss'}";
        TreeBean bean = (TreeBean) JsonTool.parseBeanFromText(text, TreeBean.class);
        assertEquals("ss", bean.getAttr("value"));
    }

    @Test
    public void testSerialize() {
        JSON.registerProvider(JsonTool.instance());
        String str = JSON.stringify("<c:script>\r\na+b\n</c:script>");
        System.out.println(str);
        assertEquals("\"<c:script>\\r\\na+b\\n</c:script>\"", str);
        assertEquals("<c:script>\r\na+b\n</c:script>", JsonTool.parse(str));
    }

    @Test
    public void testTreeBean() {
        TreeBean bean = new TreeBean();
        bean.setTagName("a");
        bean.setAttr("b", 1);
        bean.setContentValue("x");
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(TreeBean.class);
        assertTrue(!beanModel.getPropertyModel("childCount").isSerializable());
        assertEquals("{\"$body\":\"x\",\"$type\":\"a\",\"b\":1}", JsonTool.stringify(bean));
    }

    @Test
    public void testGeneric() {
        ApiRequest<?> request = (ApiRequest<?>) JsonTool.parseBeanFromText("{\"data\":{}}", ApiRequest.class);
        assertTrue(request.getData() instanceof Map);
    }

    @Test
    public void testYaml() {
        JObject obj = new JObject();
        obj.put("a", null);
        obj.put("b", "null");
        String yaml = JsonTool.serializeToYaml(obj);
        assertEquals("a: null\nb: 'null'\n", yaml);

        Map<String, Object> map = (Map<String, Object>) JsonTool.parseYaml(null, yaml);
        assertEquals("{\"a\":null,\"b\":\"null\"}", JsonTool.serialize(map, false));
    }

    @Test
    public void testFilterBean() {
        TreeBean filter = new TreeBean();
        filter.setTagName("filter");
        filter.addChild(FilterBeans.eq("status", 1));
        assertEquals("{\"$body\":[{\"$type\":\"eq\",\"name\":\"status\",\"value\":1}],\"$type\":\"filter\"}", JsonTool.serialize(filter, false));
    }

    @Test
    public void testApiRequest() {
        FieldSelectionBean selection = FieldSelectionBean.fromProp("a", "b");
        ApiRequest<String> req = new ApiRequest<>();
        req.setSelection(selection);
        req.setData("s");
        String str = JsonTool.stringify(req);
        System.out.println(str);

        ApiRequest<String> req2 = (ApiRequest<String>) JsonTool.parseBeanFromText(str, GenericTypeHelper.buildRequestType(PredefinedGenericTypes.STRING_TYPE));
        assertEquals("s", req2.getData());
        assertEquals(2, req.getSelection().getFields().size());
    }

    @Test
    public void testTimestamp() {
        String str = JsonTool.stringify(new Timestamp(System.currentTimeMillis()));
        System.out.println(str);
        assertTrue(str.length() > 19);

        assertEquals(3, JsonTool.beanToJsonObject(3));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEscape() {
        String json = "{\"url\":\"http:\\/\\/www.baidu.com\",\"a\":\"\\n\\t\\r1\\f\"}";
        Map<String, Object> map = (Map<String, Object>) JsonTool.parse(json);
        assertEquals("http://www.baidu.com", map.get("url"));
    }

    @Test
    public void testLocalDateTime() {
        LocalDateTime dateTime = LocalDateTime.now();
        String str = JsonTool.serialize(dateTime, true);
        System.out.println(str);
        assertEquals(StringHelper.quote(DateHelper.formatDateTime(dateTime, "yyyy-MM-dd HH:mm:ss")), str);
    }

    @Test
    public void testJsonIgnore2() {
        String text = "{name:'3',value:2}";

        MyBean bean = (MyBean) JsonTool.parseBeanFromText(text, MyBean.class);
        assertEquals(0, bean.getValue());
    }

    @Test
    public void testParseArray() {
        String str = "[\n" +
                "  {\n" +
                "    \"name\": \"BaseAgent\",\n" +
                "    \"summary\": \"基础代理类，用于提供AI代理的基本结构和功能。\",\n" +
                "    \"functions\": []\n" +
                "  }\n" +
                "]";
        JsonTool.parse(str);
    }


    @Test
    public void testLooseJson() {
        String str = "{\n" +
                "        \"\"name\"\": \"\"parseResponse\"\",\n" +
                "        \"\"summary\"\": \"\"解析传入的字符串响应，提取其中的 JSON 内容并返回解析后的对象。\"\"\n" +
                "      }";

        Map<String, Object> json = (Map<String, Object>) new JsonParser().looseSyntax(true).parseFromText(null, str);
        System.out.println(json);
        assertTrue(json.get("name") != null);
    }


    @Test
    public void testLooseSyntax2() {
        String text = "      {\n" +
                "        \"None\",\n" +
                "        \"summary\": \"xxx\"\n" +
                "      }";
        Map<String, Object> json = (Map<String, Object>) new JsonParser().looseSyntax(true).parseFromText(null, text);
        System.out.println(json);
        assertTrue(json.get("summary") != null);
    }


    @DataBean
    public static class MyBean {
        private String name;
        private int value;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonIgnore
        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    @Test
    public void testYamlCommentPreservation() {
        // 测试 YAML 解析和序列化时保留 comment
        String yamlText = "# 这是对象注释\n" +
                "a: 1\n" +
                "b: \"hello\"\n";

        // 解析 YAML，保留 comment
        JsonParseOptions options = new JsonParseOptions();
        options.setYaml(true);
        options.setKeepComment(true);
        options.setKeepLocation(true);

        JObject obj = (JObject) JsonTool.instance().parseFromText(null, yamlText, options);

        // 验证解析后的 JObject 有 comment
        assertTrue(obj.getComment() != null, "JObject comment should not be null");
        assertTrue(obj.getComment().contains("对象注释"));

        // 序列化回 YAML，应该保留 comment
        String serialized = JsonTool.serializeToYaml(obj);

        // 验证序列化后的 YAML 包含 comment
        assertTrue(serialized.contains("对象注释"),
                "序列化后的 YAML 应该包含对象注释，实际内容: " + serialized);
    }

    @Test
    public void testYamlNestedCommentPreservation() {
        // 测试嵌套结构中 comment 的保留
        // 构建嵌套结构
        JObject innerObj = new JObject();
        innerObj.setComment("内部对象注释");
        innerObj.put("x", 1);
        innerObj.put("y", 2);

        JArray innerArr = new JArray();
        innerArr.setComment("内部数组注释");
        innerArr.add("item1");
        innerArr.add("item2");

        JObject outerObj = new JObject();
        outerObj.setComment("外部对象注释");
        outerObj.put("innerObj", innerObj);
        outerObj.put("innerArr", innerArr);

        // 序列化
        String yaml = JsonTool.serializeToYaml(outerObj);

        // 验证所有注释都被保留
        assertTrue(yaml.contains("外部对象注释"), "应该包含外部对象注释");
        assertTrue(yaml.contains("内部对象注释"), "应该包含内部对象注释");
        assertTrue(yaml.contains("内部数组注释"), "应该包含内部数组注释");

        // 再解析回来验证
        JsonParseOptions options = new JsonParseOptions();
        options.setYaml(true);
        options.setKeepComment(true);
        options.setKeepLocation(true);

        JObject parsed = (JObject) JsonTool.instance().parseFromText(null, yaml, options);
        assertTrue(parsed.getComment() != null && parsed.getComment().contains("外部对象注释"),
                "解析后应保留外部对象注释");
    }

    @Test
    public void testSnakeYamlCommentBehavior() {
        // 测试 SnakeYAML 是否会把文档开头的注释放到根节点的 blockComments 中
        org.yaml.snakeyaml.LoaderOptions loaderOptions = new org.yaml.snakeyaml.LoaderOptions();
        loaderOptions.setProcessComments(true);
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(loaderOptions);

        // 测试不同位置的注释
        String yamlText = "# 文档开头注释\n" +
                "# 第二行注释\n" +
                "a: 1\n" +
                "# b前面空行的注释\n" +
                "\n" +
                "b: 2\n";

        org.yaml.snakeyaml.nodes.Node node = yaml.compose(new java.io.StringReader(yamlText));

        System.out.println("=== SnakeYAML Comment Behavior Test ===");
        System.out.println("Root node type: " + node.getNodeId());
        System.out.println("Root node blockComments: " + node.getBlockComments());
        System.out.println("Root node endComments: " + node.getEndComments());

        if (node instanceof org.yaml.snakeyaml.nodes.MappingNode) {
            org.yaml.snakeyaml.nodes.MappingNode map = (org.yaml.snakeyaml.nodes.MappingNode) node;
            for (org.yaml.snakeyaml.nodes.NodeTuple tuple : map.getValue()) {
                org.yaml.snakeyaml.nodes.Node keyNode = tuple.getKeyNode();
                org.yaml.snakeyaml.nodes.Node valueNode = tuple.getValueNode();
                String key = ((org.yaml.snakeyaml.nodes.ScalarNode) keyNode).getValue();
                System.out.println("Key '" + key + "':");
                System.out.println("  keyNode.blockComments: " + keyNode.getBlockComments());
                System.out.println("  valueNode.blockComments: " + valueNode.getBlockComments());
                System.out.println("  valueNode.endComments: " + valueNode.getEndComments());
            }
        }
        System.out.println("========================================");

        // 结论：SnakeYAML 不会把文档开头的注释放到根节点的 blockComments 中
        // 所以 extractLeadingComments() 是必要的
    }

    @Test
    public void testComplexYamlCommentRoundTrip() {
        // 测试复杂嵌套结构的 comment 解析和序列化
        String complexYaml = 
                "# 这是文档级别的注释\n" +
                "# 描述整个配置文件\n" +
                "server:\n" +
                "  host: localhost\n" +
                "  port: 8080\n" +
                "\n" +
                "database:\n" +
                "  # 数据库连接配置\n" +
                "  connection:\n" +
                "    url: jdbc:mysql://localhost:3306/mydb\n" +
                "    username: admin\n" +
                "    password: secret\n" +
                "  # 连接池配置\n" +
                "  pool:\n" +
                "    minSize: 5\n" +
                "    maxSize: 20\n" +
                "\n" +
                "# 功能开关配置\n" +
                "features:\n" +
                "  - name: feature1\n" +
                "    enabled: true\n" +
                "  - name: feature2\n" +
                "    enabled: false\n";

        // 解析 YAML
        JsonParseOptions options = new JsonParseOptions();
        options.setYaml(true);
        options.setKeepComment(true);
        options.setKeepLocation(true);

        JObject parsed = (JObject) JsonTool.instance().parseFromText(null, complexYaml, options);

        // 验证注释被正确解析
        // 根对象注释
        assertTrue(parsed.getComment() != null, "根对象应有注释");
        assertTrue(parsed.getComment().contains("文档级别的注释"), 
                "根对象注释应包含'文档级别的注释'");

        // 嵌套对象注释
        JObject database = (JObject) parsed.get("database");
        assertTrue(database != null, "database 应存在");
        
        JObject connection = (JObject) database.get("connection");
        assertTrue(connection != null, "connection 应存在");
        assertTrue(connection.getComment() != null, "connection 应有注释");
        assertTrue(connection.getComment().contains("数据库连接配置"),
                "connection 注释应包含'数据库连接配置'");

        JObject pool = (JObject) database.get("pool");
        assertTrue(pool != null, "pool 应存在");
        assertTrue(pool.getComment() != null, "pool 应有注释");
        assertTrue(pool.getComment().contains("连接池配置"),
                "pool 注释应包含'连接池配置'");

        // 数组注释
        JArray features = (JArray) parsed.get("features");
        assertTrue(features != null, "features 应存在");
        assertTrue(features.getComment() != null, "features 应有注释");
        assertTrue(features.getComment().contains("功能开关配置"),
                "features 注释应包含'功能开关配置'");

        // 序列化回 YAML
        String serialized = JsonTool.serializeToYaml(parsed);

        // 验证序列化后的 YAML 包含关键注释
        assertTrue(serialized.contains("文档级别的注释"), "应包含文档级别注释");
        assertTrue(serialized.contains("数据库连接配置"), "应包含数据库连接配置注释");
        assertTrue(serialized.contains("连接池配置"), "应包含连接池配置注释");
        assertTrue(serialized.contains("功能开关配置"), "应包含功能开关配置注释");

        // 验证数据正确性
        JObject server = (JObject) parsed.get("server");
        assertEquals("localhost", server.get("host"));
        assertEquals(8080, server.get("port"));
        
        assertEquals("jdbc:mysql://localhost:3306/mydb", connection.get("url"));
        assertEquals("admin", connection.get("username"));
        assertEquals("secret", connection.get("password"));

        // Round-trip 验证：再次解析序列化后的 YAML
        JObject reParsed = (JObject) JsonTool.instance().parseFromText(null, serialized, options);
        
        // 验证 round-trip 后注释仍然保留
        assertTrue(reParsed.getComment() != null, "round-trip 后根对象应有注释");
        assertTrue(reParsed.getComment().contains("文档级别的注释"),
                "round-trip 后应保留文档级别注释");

        // 验证 round-trip 后数据正确
        JObject reServer = (JObject) reParsed.get("server");
        assertEquals("localhost", reServer.get("host"));
        assertEquals(8080, reServer.get("port"));
    }

    @Test
    public void testYamlCommentRoundTrip() {
        // 测试完整的 round-trip：解析 -> 序列化 -> 再解析
        String originalYaml = "# 主注释\n" +
                "name: test\n" +
                "value: 123\n";

        // 第一次解析
        JsonParseOptions options = new JsonParseOptions();
        options.setYaml(true);
        options.setKeepComment(true);
        options.setKeepLocation(true);

        JObject obj1 = (JObject) JsonTool.instance().parseFromText(null, originalYaml, options);
        assertEquals("主注释", obj1.getComment().trim());
        assertEquals("test", obj1.get("name"));

        // 序列化
        String serialized = JsonTool.serializeToYaml(obj1);
        assertTrue(serialized.contains("主注释"));

        // 再次解析
        JObject obj2 = (JObject) JsonTool.instance().parseFromText(null, serialized, options);
        assertTrue(obj2.getComment() != null);
        assertTrue(obj2.getComment().contains("主注释"));
        assertEquals("test", obj2.get("name"));
        assertEquals(123, obj2.get("value"));
    }

    @Test
    public void testYamlArrayComment() {
        // 测试 JArray 的 comment 保留
        String yamlText = "# 数组注释\n" +
                "- a\n" +
                "- b\n" +
                "- c\n";

        JsonParseOptions options = new JsonParseOptions();
        options.setYaml(true);
        options.setKeepComment(true);
        options.setKeepLocation(true);

        JArray arr = (JArray) JsonTool.instance().parseFromText(null, yamlText, options);

        // 验证解析后的 JArray 有 comment
        assertTrue(arr.getComment() != null);
        assertTrue(arr.getComment().contains("数组注释"));
        assertEquals(3, arr.size());

        // 序列化回 YAML
        String serialized = JsonTool.serializeToYaml(arr);

        // 验证序列化后的 YAML 包含 comment
        assertTrue(serialized.contains("数组注释"),
                "序列化后的 YAML 应该包含数组注释，实际内容: " + serialized);
    }
}
