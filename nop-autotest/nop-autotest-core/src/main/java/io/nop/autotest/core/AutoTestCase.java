/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.core;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.autotest.core.data.AutoTestCaseData;
import io.nop.autotest.core.data.AutoTestDataHelper;
import io.nop.autotest.core.data.AutoTestVarCollector;
import io.nop.autotest.core.data.AutoTestVars;
import io.nop.autotest.core.exceptions.AutoTestException;
import io.nop.autotest.core.exceptions.AutoTestWrapException;
import io.nop.autotest.core.execute.AutoTestCaseDataBaseInitializer;
import io.nop.autotest.core.execute.AutoTestCaseDataSaver;
import io.nop.autotest.core.execute.AutoTestCaseResultChecker;
import io.nop.autotest.core.execute.AutoTestMatchChecker;
import io.nop.autotest.core.execute.AutoTestOrmHook;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.core.unittest.VarCollector;
import io.nop.dao.DaoConfigs;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.orm.IOrmSessionFactory;
import io.nop.xlang.api.XLang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static io.nop.autotest.core.AutoTestErrors.ARG_ERROR_NAME;
import static io.nop.autotest.core.AutoTestErrors.ARG_EXPECTED;
import static io.nop.autotest.core.AutoTestErrors.ARG_FILE_NAME;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_CHECK_MATCH_FAIL;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_CHECK_OUTPUT_FAIL;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_EXPECT_ERROR;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_SNAPSHOT_FINISHED;
import static io.nop.xlang.XLangErrors.ARG_VALUE;

/**
 * 提供自动化测试支持，但是不依赖任何特定的测试框架。具体的JUnit集成由JunitAutoTestCase类提供。
 */
public class AutoTestCase extends BaseTestCase {
    static final Logger LOG = LoggerFactory.getLogger(AutoTestCase.class);

    private AutoTestCaseData caseData;
    private String variant;

    private MatchPatternCompileConfig matchConfig = new MatchPatternCompileConfig();
    private ValueResolverCompilerRegistry valueResolverRegistry = ValueResolverCompilerRegistry.DEFAULT;

    private AutoTestOrmHook ormHook;
    private IOrmSessionFactory sessionFactory;
    private IDaoProvider daoProvider;
    private IJdbcTemplate jdbcTemplate;

    private boolean checkOutput;
    private boolean localDb;
    private boolean tableInit;
    private boolean sqlInit;
    private boolean saveOutput;

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public void setSaveOutput(boolean saveOutput) {
        this.saveOutput = saveOutput;
    }

    public MatchPatternCompileConfig getMatchConfig() {
        return matchConfig;
    }

    public void setValueResolverRegistry(ValueResolverCompilerRegistry valueResolverRegistry) {
        this.valueResolverRegistry = valueResolverRegistry;
    }

    public AutoTestCaseData getCaseData() {
        return caseData;
    }

    public void initCaseDataDir(File caseDataDir) {
        caseData = new AutoTestCaseData(caseDataDir, valueResolverRegistry);
        caseData.getInputDir().mkdirs();
        caseData.getOutputDir().mkdirs();
        try {
            new File(caseDataDir, "autotest.yaml").createNewFile();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    protected IDaoProvider daoProvider() {
        return daoProvider;
    }

    protected IJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    protected IOrmSessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void initDao() {
        daoProvider = initDaoProvider();
        jdbcTemplate = initJdbcTemplate();
        sessionFactory = initSessionFactory();

        Map<String, Object> vars = caseData.getInitVars();
        AutoTestVars.setVars(vars);
        VarCollector.registerInstance(new AutoTestVarCollector());

        if (localDb || tableInit || sqlInit) {
            LOG.info("\n========= autotest restore data from snapshot ============");
            if (daoProvider != null) {
                new AutoTestCaseDataBaseInitializer(variant, localDb, tableInit, sqlInit, caseData, daoProvider,
                        jdbcTemplate).initialize();
            }
        }

        ormHook = new AutoTestOrmHook();
        sessionFactory.addDaoListener(ormHook);
        sessionFactory.addInterceptor(ormHook);

        IContext context = ContextProvider.getOrCreateContext();
        context.setUserId("autotest");
        context.setUserName("autotest-name");
        context.setUserRefNo("autotest-ref");
        IUserContext.set(null);
    }

    protected void configLocalDb() {
        if (localDb) {
            setTestConfig(DaoConfigs.CFG_DATASOURCE_DRIVER_CLASS_NAME, "org.h2.Driver");
            setTestConfig(DaoConfigs.CFG_DATASOURCE_USERNAME, "sa");
            setTestConfig(DaoConfigs.CFG_DATASOURCE_PASSWORD, "");
            setTestConfig(DaoConfigs.CFG_DATASOURCE_JDBC_URL, "jdbc:h2:mem:" + StringHelper.generateUUID());
        }
    }

    protected IDaoProvider initDaoProvider() {
        return (IDaoProvider) BeanContainer.tryGetBean("nopDaoProvider");
    }

    protected IJdbcTemplate initJdbcTemplate() {
        return (IJdbcTemplate) BeanContainer.tryGetBean("nopJdbcTemplate");
    }

    protected IOrmSessionFactory initSessionFactory() {
        return (IOrmSessionFactory) BeanContainer.tryGetBean("nopOrmSessionFactory");
    }

    protected void initBeans() {
        if (!BeanContainer.isInitialized())
            return;

        clearLazyActions();
        IBeanContainer container = BeanContainer.instance();
        container.restart();

        if (!container.supportInjectTo())
            return;

        container.injectTo(this);
    }

    public void complete(boolean success) {
        try {
            AutoTestVars.dumpVars();
            VarCollector.registerInstance(null);

            if (sessionFactory != null && ormHook != null) {
                sessionFactory.removeDaoListener(ormHook);
                sessionFactory.removeInterceptor(ormHook);
            }

            // 如果执行过程失败了，抛出了异常，则没有必要再执行结果检查
            if (success) {
                if (saveOutput) {
                    new AutoTestCaseDataSaver(variant, caseData, ormHook).saveCollectedData();
                    throw new NopException(ERR_AUTOTEST_SNAPSHOT_FINISHED);
                } else if (checkOutput) {
                    LOG.info("\n============ autotest run snapshot check =========================");

                    new AutoTestCaseResultChecker(variant, caseData, daoProvider, jdbcTemplate, matchConfig).check();
                }
            }
        } finally {
            AutoTestVars.clear();
        }
        LOG.info("nop.autotest.completed:case={},success={}",this.getClass().getName(),success);
    }

    public boolean isCheckOutput() {
        return checkOutput;
    }

    public void setCheckOutput(boolean checkOutput) {
        this.checkOutput = checkOutput;
    }

    public boolean isLocalDb() {
        return localDb;
    }

    /**
     * 在执行快照测试时是否使用本地数据库。如果使用，则会自动根据input目录下的实体数据建表并插入初始化数据
     *
     * @param localDb
     */
    public void setLocalDb(boolean localDb) {
        this.localDb = localDb;
    }

    public void setTableInit(boolean tableInit) {
        this.tableInit = tableInit;
    }

    public void setSqlInit(boolean sqlInit) {
        this.sqlInit = sqlInit;
    }

    /**
     * 根据文件名，在input目录下读取json格式的数据文件。
     *
     * @param fileName   input目录下的数据文件名
     * @param resultType 将读取到的数据转化为resultType类型
     * @return 读取到的结果数据对象
     */
    public <T> T input(String fileName, Type resultType) {
        String path = caseData.getInputFileName(fileName, variant);
        if (saveOutput) {
            File file = caseData.getFile(path);
            FileHelper.assureFileExists(file);
        }
        return caseData.readDeltaJson(path, resultType);
    }

    public <T> ApiRequest<T> request(String fileName, Type bodyType) {
        IGenericType requestType = GenericTypeHelper.buildRequestType(BeanTool.getGenericType(bodyType));
        return input(fileName, requestType);
    }

    /**
     * 将结果数据对象通过JSON序列化转化成文本之后，保存到output目录下的数据文件中
     *
     * @param fileName output目录下的数据文件名
     * @param result   结果数据对象，不能包含循环引用，可以被json序列化。如果是Throwable类型，会被自动转换为ExceptionData类型保存
     */
    public void output(String fileName, Object result) {
        if (saveOutput) {
            caseData.writeDeltaJson(caseData.getOutputFileName(fileName, variant), result);
        } else {
            IEvalScope scope = XLang.newEvalScope(AutoTestVars.getVars());
            Object value = null;
            try {
                Object json = caseData.readOutputJson(fileName, variant);
                value = AutoTestDataHelper.toJsonObject(result);
                AutoTestMatchChecker.checkMatch(matchConfig, json, value, scope);
            } catch (Exception e) {
                LOG.error("nop.autotest.output-check-fail:fileName={},result={}", fileName,
                        JsonTool.serialize(value, true));
                throw new AutoTestException(ERR_AUTOTEST_CHECK_OUTPUT_FAIL, e).param(ARG_FILE_NAME, fileName);
            }
        }
    }

    public byte[] inputBytes(String fileName) {
        return caseData.readBytes(caseData.getInputFileName(fileName, variant));
    }

    public IResource inputResource(String fileName) {
        File file = caseData.getFile(caseData.getInputFileName(fileName, variant));
        return new FileResource(file);
    }

    public void outputBytes(String fileName, byte[] bytes) {
        String path = caseData.getOutputFileName(fileName, variant);
        if (saveOutput) {
            caseData.writeBytes(path, bytes);
        } else {
            byte[] expected = caseData.readBytes(path);
            if (!Arrays.equals(expected, bytes)) {
                throw new AutoTestException(ERR_AUTOTEST_EXPECT_ERROR).param(ARG_FILE_NAME, fileName)
                        .param(ARG_EXPECTED, StringHelper.bytesToHex(expected))
                        .param(ARG_VALUE, StringHelper.bytesToHex(bytes));
            }
        }
    }

    public String inputText(String fileName) {
        return caseData.readText(caseData.getInputFileName(fileName, variant), null);
    }

    public void outputText(String fileName, String text) {
        String path = caseData.getOutputFileName(fileName, variant);
        if (saveOutput) {
            caseData.writeText(path, text, null);
        } else {
            String expected = caseData.readText(path, null);
            checkEquals(ERR_AUTOTEST_CHECK_MATCH_FAIL, fileName, normalizeCRLF(expected), normalizeCRLF(text));
        }
    }

    protected void checkEquals(ErrorCode errorCode, String fileName, Object expected, Object value) {
        if (!Objects.equals(expected, value))
            throw new AutoTestException(errorCode).param(ARG_FILE_NAME, fileName)
                    .param(ARG_EXPECTED, expected).param(ARG_VALUE, value);
    }


    public ByteString inputHex(String fileName) {
        return caseData.readHex(caseData.getInputFileName(fileName, variant));
    }

    public void outputHex(String fileName, ByteString bytes) {
        outputText(caseData.getOutputFileName(fileName, variant), StringHelper.bytesToHex(bytes.toByteArray()));
    }

    /**
     * 期待执行task之后抛出异常
     *
     * @param errorName 将异常信息保存到errorName对应的文件中
     * @param task      待执行的任务
     */
    public void error(String errorName, Runnable task) {
        try {
            task.run();
            throw new AutoTestException(ERR_AUTOTEST_EXPECT_ERROR).param(ARG_ERROR_NAME, errorName);
        } catch (AutoTestWrapException e) {
            throw NopException.adapt(e.getCause());
        } catch (AutoTestException e) {
            throw e;
        } catch (Throwable e) {
            output(errorName, e);
        }
    }

    /**
     * 设置上下文环境中的变量。后续通过input函数读取的所有数据中的对应变量将被替换为这里指定的值。
     *
     * @param name  变量名
     * @param value 变量值
     */
    public void addVar(String name, Object value) {
        AutoTestVars.addVar(name, value);
    }

    public void setVar(String name, Object value) {
        AutoTestVars.setVar(name, value);
    }
}