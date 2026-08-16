package io.nop.record_mapping;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.record_mapping.md.MappingBasedMarkdownGenerator;
import io.nop.record_mapping.md.MappingBasedMarkdownParser;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.record_mapping.model.RecordMappingDefinitions;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslNodeLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.record_mapping.RecordMappingConstants.VAR_SOURCE;
import static io.nop.record_mapping.RecordMappingConstants.VAR_SOURCE_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingConstants.VAR_TARGET;
import static io.nop.record_mapping.RecordMappingConstants.VAR_TARGET_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_IS_MANDATORY;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_NOT_COLLECTION_TYPE;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_VALUE_NOT_IN_DICT;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_MAPPING_NOT_FOUND;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_MD_MISSING_FIELD;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_UNKNOWN_FROM_FIELD;

/**
 * nop-record-mapping 缺陷回归测试（plan 2253）
 * 每个测试先验证缺陷存在（修复前 FAIL），修复后转绿
 */
public class TestRecordMappingRegression extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testDictFieldWithNullValue() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.DictFieldTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", null);

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        Assertions.assertNull(target.get("status"));
    }

    @Test
    public void testDictFieldWithEmptyStringValue() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.DictFieldTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", "");

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        Assertions.assertNull(target.get("status"));
    }

    @Test
    public void testDictFieldWithInvalidValue() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.DictFieldTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", "9");

        Map<String, Object> target = new LinkedHashMap<>();
        NopException e = Assertions.assertThrows(NopException.class,
                () -> mapping.map(source, target, new RecordMappingContext()));
        Assertions.assertEquals(ERR_RECORD_FIELD_VALUE_NOT_IN_DICT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testFlattenToWritesToTarget() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.FlattenToTest");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("c", "b1");
        item.put("d", "b2");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("listB", Arrays.asList(item));

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        // 展平结果写入 target，前缀取 from（listB）而非 name（listA）
        Assertions.assertEquals("b1", target.get("listB-1-a"));
        Assertions.assertEquals("b2", target.get("listB-1-b"));
        // source 不被修改
        Assertions.assertNull(source.get("listA-1-a"));
        Assertions.assertNull(source.get("listB-1-a"));
    }

    @Test
    public void testFlattenRoundTrip() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.FlattenRoundTripTest");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("items-1-c", "b1");
        source.put("items-1-d", "b2");
        source.put("items-2-c", "b3");
        source.put("items-2-d", "b4");

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        // flattenFrom → flattenTo round-trip：目标上得到同构展平键（ItemPass_to_Test 为恒等映射 c→c, d→d）
        Assertions.assertEquals("b1", target.get("items-1-c"));
        Assertions.assertEquals("b2", target.get("items-1-d"));
        Assertions.assertEquals("b3", target.get("items-2-c"));
        Assertions.assertEquals("b4", target.get("items-2-d"));
        // source 不被修改
        Assertions.assertEquals("b1", source.get("items-1-c"));
        Assertions.assertNull(target.get("listA-1-a"));
    }

    @Test
    public void testDotlessMappingName() {
        // B3: 无包名前缀的 mappingName 应抛 NopException（ERR_RECORD_MAPPING_NOT_FOUND），而非 StringIndexOutOfBoundsException
        NopException e = Assertions.assertThrows(NopException.class,
                () -> RecordMappingManager.instance().getRecordMapping("Type1_to_Type2"));
        Assertions.assertEquals(ERR_RECORD_MAPPING_NOT_FOUND.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testNewItemExprReceivesTarget() {
        // B4: makeCollectionItem 应把目标 collection 传给 newItemExpr 的 target 参数
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.NewItemExprTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("items", Arrays.asList(1, 2));

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        List<?> items = (List<?>) target.get("items");
        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals(true, items.get(0));
        Assertions.assertEquals(true, items.get(1));
    }

    @Test
    public void testIgnoreWhenEmptyNestedField() {
        // B5: ignoreWhenEmpty 对嵌套 mapping 字段生效——源值为 null 时不生成空对象
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.IgnoreWhenEmptyNestedTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("nested", null);
        Map<String, Object> mandatorySource = new LinkedHashMap<>();
        mandatorySource.put("x", "y");
        source.put("mandatoryNested", mandatorySource);

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        Assertions.assertFalse(target.containsKey("nested"));
    }

    @Test
    public void testMandatoryIgnoreWhenEmptyStillErrors() {
        // B5 control: mandatory + ignoreWhenEmpty + null 仍抛 ERR_RECORD_FIELD_IS_MANDATORY
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.IgnoreWhenEmptyNestedTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("nested", null);
        source.put("mandatoryNested", null);

        Map<String, Object> target = new LinkedHashMap<>();
        NopException e = Assertions.assertThrows(NopException.class,
                () -> mapping.map(source, target, new RecordMappingContext()));
        Assertions.assertEquals(ERR_RECORD_FIELD_IS_MANDATORY.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testImpConfigReferencesCorrectTemplate() {
        // B6: imp.xml templatePath 指向存在的 template.record-mappings.xlsx；fields 列表 keyProp 为 name
        IResource resource = VirtualFileSystem.instance().getResource("/nop/record/imp/record-mappings.imp.xml");
        String text = resource.readText();
        Assertions.assertTrue(text.contains("templatePath=\"template.record-mappings.xlsx\""), text);
        Assertions.assertFalse(text.contains("keyProp=\"to\""), text);
        Assertions.assertTrue(VirtualFileSystem.instance().getResource("/nop/record/imp/template.record-mappings.xlsx").exists());
    }

    @Test
    public void testMdWhenFalseMandatoryFieldParses() {
        // B7: when=false 的 mandatory 字段在 md 中出现时解析成功（不误抛 missing-field）
        RecordMappingConfig config = RecordMappingManager.instance().getRecordMappingConfig("test.demo.MdWhenFalseTest");
        MarkdownDocument doc = MarkdownTool.instance().parseFromText(SourceLocation.fromPath("test.md"),
                "- name: abc\n- status: 1");

        Map<String, Object> target = new LinkedHashMap<>();
        new MappingBasedMarkdownParser(config).map(doc.getRootSection(), target, new RecordMappingContext());

        Assertions.assertEquals("abc", target.get("name"));
        Assertions.assertNull(target.get("status"));
    }

    @Test
    public void testMdMissingMandatoryFieldStillErrors() {
        // B7 control: md 中缺 mandatory 字段仍抛 ERR_RECORD_MD_MISSING_FIELD
        RecordMappingConfig config = RecordMappingManager.instance().getRecordMappingConfig("test.demo.MdWhenFalseTest");
        MarkdownDocument doc = MarkdownTool.instance().parseFromText(SourceLocation.fromPath("test.md"), "- status: 1");

        Map<String, Object> target = new LinkedHashMap<>();
        NopException e = Assertions.assertThrows(NopException.class,
                () -> new MappingBasedMarkdownParser(config).map(doc.getRootSection(), target, new RecordMappingContext()));
        Assertions.assertEquals(ERR_RECORD_MD_MISSING_FIELD.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMdNullOptionalFieldLineWrittenAndRoundTrips() {
        // B8: optional 简单字段 null 值写入 "- {key}: " 空行，round-trip 后映射值为 null
        RecordMappingConfig config = RecordMappingManager.instance().getRecordMappingConfig("test.demo.MdNullFieldTest");
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", "abc");
        obj.put("desc", null);

        String md = new MappingBasedMarkdownGenerator(config, obj, XLang.newEvalScope())
                .generateText(XLang.newEvalScope());
        Assertions.assertTrue(md.contains("- desc: "), md);

        MarkdownDocument doc = MarkdownTool.instance().parseFromText(SourceLocation.fromPath("test.md"), md);
        Map<String, Object> target = new LinkedHashMap<>();
        new MappingBasedMarkdownParser(config).map(doc.getRootSection(), target, new RecordMappingContext());

        Assertions.assertNull(target.get("desc"));
        Assertions.assertEquals("abc", target.get("name"));
    }

    @Test
    public void testMdNullMandatoryFieldParseErrors() {
        // B8 control: mandatory 简单字段 null 值行 parse 时报 ERR_RECORD_FIELD_IS_MANDATORY（指明字段），而非 missing-field
        RecordMappingConfig config = RecordMappingManager.instance().getRecordMappingConfig("test.demo.MdNullFieldTest");
        MarkdownDocument doc = MarkdownTool.instance().parseFromText(SourceLocation.fromPath("test.md"), "- name: ");

        Map<String, Object> target = new LinkedHashMap<>();
        NopException e = Assertions.assertThrows(NopException.class,
                () -> new MappingBasedMarkdownParser(config).map(doc.getRootSection(), target, new RecordMappingContext()));
        Assertions.assertEquals(ERR_RECORD_FIELD_IS_MANDATORY.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMdUnknownFieldStillErrorsByDefault() {
        // B9 control: 默认（ignoreUnknownFields=false）未知条目仍抛 ERR_RECORD_UNKNOWN_FROM_FIELD
        RecordMappingConfig config = RecordMappingManager.instance().getRecordMappingConfig("test.demo.MdIgnoreUnknownTest");
        MarkdownDocument doc = MarkdownTool.instance().parseFromText(SourceLocation.fromPath("test.md"),
                "- name: abc\n- unknownKey: x");

        Map<String, Object> target = new LinkedHashMap<>();
        NopException e = Assertions.assertThrows(NopException.class,
                () -> new MappingBasedMarkdownParser(config).map(doc.getRootSection(), target, new RecordMappingContext()));
        Assertions.assertEquals(ERR_RECORD_UNKNOWN_FROM_FIELD.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMdIgnoreUnknownFieldsSkipsUnknown() {
        // B9: ignoreUnknownFields=true 时未知列表项被跳过、其余字段正常映射
        RecordMappingConfig config = RecordMappingManager.instance().getRecordMappingConfig("test.demo.MdIgnoreUnknownTestLoose");
        MarkdownDocument doc = MarkdownTool.instance().parseFromText(SourceLocation.fromPath("test.md"),
                "- name: abc\n- unknownKey: x");

        Map<String, Object> target = new LinkedHashMap<>();
        new MappingBasedMarkdownParser(config).map(doc.getRootSection(), target, new RecordMappingContext());

        Assertions.assertEquals("abc", target.get("name"));
        Assertions.assertNull(target.get("unknownKey"));
    }

    @Test
    public void testPatternLeavesNoContextVarResidue() {
        // B10: pattern 映射后 context 变量（sourceFieldName/targetFieldName/source/target/捕获变量）恢复原值
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.PatternFieldTest");
        RecordMappingContext ctx = new RecordMappingContext();
        Object presetSource = new Object();
        Object presetTarget = new Object();
        ctx.setValue(VAR_SOURCE, presetSource);
        ctx.setValue(VAR_TARGET, presetTarget);
        ctx.setValue(VAR_SOURCE_FIELD_NAME, "preSourceField");
        ctx.setValue(VAR_TARGET_FIELD_NAME, "preTargetField");
        ctx.setValue("name", "preName");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("user_age", 10);
        source.put("user_john", "x");
        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, ctx);

        Assertions.assertSame(presetSource, ctx.getValue(VAR_SOURCE));
        Assertions.assertSame(presetTarget, ctx.getValue(VAR_TARGET));
        Assertions.assertEquals("preSourceField", ctx.getValue(VAR_SOURCE_FIELD_NAME));
        Assertions.assertEquals("preTargetField", ctx.getValue(VAR_TARGET_FIELD_NAME));
        Assertions.assertEquals("preName", ctx.getValue("name"));
    }

    @Test
    public void testPatternFieldDefaultValue() {
        // B11: patternField 配 defaultValue，源字段值 null → 目标得到 defaultValue
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.PatternFieldDefaultTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("user_age", null);

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        Assertions.assertEquals(0, target.get("age"));
    }

    @Test
    public void testPatternDoesNotReprocessPathField() {
        // B12: 显式字段 from="sub.field2" + patternField fromPattern="sub*" → pattern 不重复处理 sub
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.PatternWithPathFieldTest");
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("field2", "v2");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sub", sub);

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        Assertions.assertEquals("v2", ((Map<?, ?>) target.get("ext")).get("name2"));
        Assertions.assertFalse(target.containsKey("anyField"));
    }

    @Test
    public void testMapSetsRootVars() {
        // D7 control: 直接 map(source, target, ctx) → sourceRoot/targetRoot 均被设置
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.ItemPass_to_Test");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("c", "x");
        Map<String, Object> target = new LinkedHashMap<>();

        RecordMappingContext ctx = new RecordMappingContext();
        mapping.map(source, target, ctx);

        Assertions.assertSame(source, ctx.getSourceRoot());
        Assertions.assertSame(target, ctx.getTargetRoot());
    }

    @Test
    public void testMapKeepsPresetSourceRoot() {
        // D7: 预置 sourceRoot 后 map → sourceRoot 保留、targetRoot 仍被设置
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.ItemPass_to_Test");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("c", "x");
        Map<String, Object> target = new LinkedHashMap<>();

        RecordMappingContext ctx = new RecordMappingContext();
        Object preset = new Object();
        ctx.setSourceRoot(preset);
        mapping.map(source, target, ctx);

        Assertions.assertSame(preset, ctx.getSourceRoot());
        Assertions.assertSame(target, ctx.getTargetRoot());
    }

    @Test
    public void testReverseMappingPreservesFieldAttributes() {
        // D4: 反向自动生成保留 defaultValue/varName/virtual/keyProp；flattenFrom↔flattenTo 互换
        RecordMappingConfig reverse = RecordMappingManager.instance().getRecordMappingConfig("test.demo.RevGenTestTarget_to_RevGenTestSource");
        Assertions.assertNotNull(reverse);

        RecordFieldMappingConfig flatIn = reverse.getField("flatIn"); // 原 flattenFrom → 反向 flattenTo
        Assertions.assertNotNull(flatIn);
        Assertions.assertEquals("flatOut", flatIn.getFrom());
        Assertions.assertTrue(flatIn.isFlattenTo());
        Assertions.assertFalse(flatIn.isFlattenFrom());

        RecordFieldMappingConfig deep = reverse.getField("deep"); // 原 flattenTo → 反向 flattenFrom
        Assertions.assertNotNull(deep);
        Assertions.assertEquals("deepOut", deep.getFrom());
        Assertions.assertTrue(deep.isFlattenFrom());
        Assertions.assertFalse(deep.isFlattenTo());

        RecordFieldMappingConfig plain = reverse.getField("plain"); // 原 from=plain name=mapped
        Assertions.assertNotNull(plain);
        Assertions.assertEquals("mapped", plain.getFrom());
        Assertions.assertEquals("123", plain.getDefaultValue());
        Assertions.assertEquals("name", plain.getKeyProp());
        Assertions.assertEquals("v", plain.getVarName());
        Assertions.assertTrue(plain.isVirtual());
    }

    @Test
    public void testBothDirectionsExplicitNoDuplicate() {
        // D4: 双向显式定义 → 显式版保留且不被自动生成版合并（无 fa 字段混入）；marker 字段存在
        // 注：post-extends 输出按 key 合并（XDefMerge），重复定义会"混入"而非报错——fa 是仅自动生成版才有的字段
        RecordMappingDefinitions defs = (RecordMappingDefinitions) ResourceComponentManager.instance()
                .loadComponentModel("resolve-record-mappings:test/demoBoth");
        RecordMappingConfig both = defs.getMapping("BothB_to_BothA");
        Assertions.assertNotNull(both);
        Assertions.assertNull(both.getField("fa"));
        RecordFieldMappingConfig marker = both.getField("explicitMarker");
        Assertions.assertNotNull(marker);
        Assertions.assertEquals("markerField", marker.getFrom());
    }

    @Test
    public void testGeneratorRejectsNonListCollection() {
        // D6: itemMapping 字段值为 Set → NopException 而非 ClassCastException
        RecordMappingConfig config = RecordMappingManager.instance().getRecordMappingConfig("test.demo.MdSectionSetTest");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("c", "x");
        item.put("d", "y");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("items", Collections.singleton(item));

        StringWriter out = new StringWriter();
        NopException e = Assertions.assertThrows(NopException.class,
                () -> new MappingBasedMarkdownGenerator(config, source, new RecordMappingContext().getEvalScope())
                        .generateToWriter(out, new RecordMappingContext()));
        Assertions.assertEquals(ERR_RECORD_FIELD_NOT_COLLECTION_TYPE.getErrorCode(), e.getErrorCode());
    }
}