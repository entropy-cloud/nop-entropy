/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.data;

import io.nop.autotest.core.diff.CsvDataDiffer;
import io.nop.autotest.core.exceptions.AutoTestException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.xlang.api.XLang;
import org.apache.commons.csv.CSVFormat;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.autotest.core.AutoTestErrors.ARG_FILE_NAME;
import static io.nop.autotest.core.AutoTestErrors.ARG_FILE_PATH;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_UNKNOWN_FILE;
import static io.nop.autotest.core.data.AutoTestDataHelper.isDefaultVariant;
import static io.nop.core.type.utils.JavaGenericTypeBuilder.buildListType;

/**
 * 此类负责管理测试数据所在目录，并提供测试数据的读取和保存操作。
 * <p>
 * 测试数据的目录结构为：
 *
 * <pre>
 * /data
 *      /input
 *         /tables
 *            my_table.csv
 *         xxx.json
 *         init_vars.json5
 *         01_xxx.sql
 *         02_yyy.sql
 *      /output
 *         /tables
 *            xxx.csv
 *         xxx.json
 *         sql_check.yaml
 *
 * </pre>
 * <p>
 * 如果测试方法上标注了{@link io.nop.api.core.annotations.autotest.EnableSnapshot}注解，则启用快照数据，根据录制数据构建本地数据库，然后执行check：
 * 如果没有注解，则自动录制访问数据和执行结果，最后不执行比较步骤，直接抛出异常中断
 * <p>
 * input目录下包含如下文件：
 * <ul>
 * 1. init_vars.json5 初始化变量集合。用于初始化AutoTestVars。
 * </ul>
 * <ul>
 * 2. xxx.sql 测试用例初始化时会按顺序执行这些sql语句，对数据库进行初始化
 * </ul>
 * <ul>
 * 3. tables目录下，每个表对应一个csv文件。测试用例会在临时数据库中插入这些数据
 * </ul>
 * <ul>
 * 4. 其他json文件。一般用input函数读取。支持后缀名为json/json5/yaml
 * </ul>
 * <p>
 * output目录下包含如下文件
 * <ul>
 * 1. sql_check.yaml 它为列表结构，具有三列 desc, sql, result。 执行sql语句，将结果与result的值进行比较
 * </ul>
 * <ul>
 * 2. tables目录下，每个表对应一个csv文件。数据行具有一个附加列_chg_type，A/U/D分别表示插入/修改/删除。 测试用例会逐行比较结果文件中的内容是否与数据库中当前的情况相匹配。
 * </ul>
 * <ul>
 * 3. 其他json文件，一般用output函数来比较。支持后缀名为json/json5/yaml
 * </ul>
 */
public class AutoTestCaseData {
    private final File caseDataDir;
    private final ValueResolverCompilerRegistry registry;

    public AutoTestCaseData(File caseDataDir, ValueResolverCompilerRegistry registry) {
        this.caseDataDir = caseDataDir;
        this.registry = registry;
    }

    public File getInputDir() {
        return new File(caseDataDir, "input");
    }

    public File getOutputDir() {
        return new File(caseDataDir, "output");
    }

    public File getFile(String fileName) {
        return new File(caseDataDir, fileName);
    }

    public File requireFile(String fileName) {
        File file = getFile(fileName);
        if (!file.exists())
            throw new AutoTestException(ERR_AUTOTEST_UNKNOWN_FILE).param(ARG_FILE_NAME, fileName).param(ARG_FILE_PATH,
                    file.getAbsolutePath());
        return file;
    }

    public <T> T readDeltaJson(String fileName, Type type) {
        File file = requireFile(fileName);
        DeltaJsonOptions options = new DeltaJsonOptions();
        options.setRegistry(registry);

        Map<String, Object> vars = AutoTestVars.getVars();
        options.setEvalContext(XLang.newEvalScope(vars));
        return JsonTool.loadDeltaBeanFromResource(new FileResource(file), type, options);
    }

    public void writeDeltaJson(String fileName, Object bean) {
        Object json = AutoTestDataHelper.toJsonObject(bean);
        // 将变量的值替换为对应的变量匹配模式
        json = AutoTestVars.replaceValueByVarName(json);

        File file = getFile(fileName);
        FileHelper.writeText(file, JsonTool.serialize(json, true), null);
    }

    public byte[] readBytes(String fileName) {
        File file = requireFile(fileName);
        return FileHelper.readBytes(file);
    }

    public void writeBytes(String fileName, byte[] bytes) {
        File file = getFile(fileName);
        FileHelper.writeBytes(file, bytes);
    }

    public String readText(String fileName, String encoding) {
        File file = requireFile(fileName);
        return FileHelper.readText(file, encoding);
    }

    public void writeText(String fileName, String text, String encoding) {
        File file = getFile(fileName);
        FileHelper.writeText(file, text, encoding);
    }

    public ByteString readHex(String fileName) {
        String text = readText(fileName, null);
        return ByteString.of(StringHelper.hexToBytes(text));
    }

    public void writeHex(String fileName, byte[] bytes) {
        writeText(fileName, StringHelper.bytesToHex(bytes), null);
    }

    public List<File> getFiles(String dir, String postfix) {
        List<File> ret = new ArrayList<>();
        File[] files = new File(caseDataDir, dir).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(postfix)) {
                    ret.add(file);
                }
            }
        }
        // 确保按文件名称顺序排序
        Collections.sort(ret);
        return ret;
    }

    public File getInitVarsFile() {
        return new File(caseDataDir, "input/init_vars.json5");
    }

    public Map<String, Object> getInitVars() {
        File file = getInitVarsFile();
        if (!file.exists()) {
            return new LinkedHashMap<>();
        }
        return JsonTool.parseBeanFromResource(new FileResource(file), Map.class);
    }

    public String getInputFileName(String fileName, String variant) {
        if (isDefaultVariant(variant))
            return "input/" + fileName;
        String path = "variants/" + variant + "/input/" + fileName;
        if (getFile(path).exists())
            return path;
        return "input/" + fileName;
    }

    public String getOutputFileName(String fileName, String variant) {
        if (isDefaultVariant(variant))
            return "output/" + fileName;
        return "variants/" + variant + "/output/" + fileName;
    }

    private File getSqlCheckFile(String variant) {
        return getOutputFile("sql_check.yaml", variant);
    }

    public List<SqlCheck> getSqlChecks(String variant) {
        File file = getSqlCheckFile(variant);
        if (!file.exists())
            return Collections.emptyList();
        return JsonTool.parseBeanFromResource(new FileResource(file), buildListType(SqlCheck.class));
    }

    public File getInputFile(String fileName, String variant) {
        return getFile(getInputFileName(fileName, variant));
    }

    public File getOutputFile(String fileName, String variant) {
        return getFile(getOutputFileName(fileName, variant));
    }

    public Object readOutputJson(String fileName, String variant) {
        File file = getOutputFile(fileName, variant);
        return JsonTool.parseBeanFromResource(new FileResource(file), Object.class);
    }

    public File getInputTableFile(String tableName, String variant) {
        return getInputFile("tables/" + tableName + ".csv", variant);
    }

    public File getOutputTableFile(String tableName, String variant) {
        return getOutputFile("tables/" + tableName + ".csv", variant);
    }

    public List<File> getInputTableFiles(String variant) {
        if (isDefaultVariant(variant))
            return getInputTableFiles();

        List<File> files = getInputTableFiles();
        List<File> varFiles = getFiles("input/" + variant + "/tables", ".csv");
        return mergeFiles(files, varFiles);
    }

    public List<File> getOutputTableFiles(String variant) {
        if (isDefaultVariant(variant))
            return getOutputTableFiles();

        List<File> files = getOutputTableFiles();
        List<File> varFiles = getFiles("input/" + variant + "/tables", ".csv");
        return mergeFiles(files, varFiles);
    }

    private List<File> getInputTableFiles() {
        return getFiles("input/tables", ".csv");
    }

    private List<File> getOutputTableFiles() {
        return getFiles("output/tables", ".csv");
    }

    public List<File> getInputSqlFiles(String variant) {
        if (isDefaultVariant(variant))
            return getInputSqlFiles();

        List<File> files = getInputSqlFiles();
        List<File> varFiles = getFiles("input/" + variant, ".sql");
        return mergeFiles(files, varFiles);
    }

    public List<File> getInitSqlFiles(String variant) {
        if (isDefaultVariant(variant))
            return getInitSqlFiles();

        List<File> files = getInitSqlFiles();
        List<File> varFiles = getFiles("init/" + variant, ".sql");
        return mergeFiles(files, varFiles);
    }

    private List<File> getInitSqlFiles() {
        return getFiles("init", ".sql");
    }

    private List<File> getInputSqlFiles() {
        return getFiles("input", ".sql");
    }

    static List<File> mergeFiles(List<File> filesA, List<File> filesB) {
        Map<String, File> map = new TreeMap<>();
        for (File file : filesA) {
            map.put(file.getName(), file);
        }
        for (File file : filesB) {
            map.put(file.getName(), file);
        }
        return new ArrayList<>(map.values());
    }

    public List<String> getVariants(boolean includeDefault) {
        List<String> ret = new ArrayList<>();
        if (includeDefault)
            ret.add("_default");

        File variantsDir = new File(this.caseDataDir, "variants");
        if (variantsDir.exists()) {
            String[] names = variantsDir.list();
            if (names != null) {
                Arrays.sort(names);
                ret.addAll(Arrays.asList(names));
            }
        }
        return ret;
    }

    private List<Map<String, Object>> readTableData(String fileName) {
        File file = getFile(fileName);
        if (!file.exists())
            return new ArrayList<>();
        return CsvHelper.readCsv(new FileResource(file), null, CSVFormat.DEFAULT);
    }

    public List<Map<String, Object>> readInputTableData(List<String> idCols, String tableName, String variant) {

        List<Map<String, Object>> base = readTableData("input/tables/" + tableName + ".csv");
        if (isDefaultVariant(variant))
            return base;

        List<Map<String, Object>> ext = readTableData("variants/" + variant + "/input/tables/" + tableName + ".csv");
        return CsvDataDiffer.INSTANCE.mergeList(idCols, base, ext);
    }
}