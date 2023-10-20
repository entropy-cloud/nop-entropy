/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.spl.execute;

import com.esproc.jdbc.Server;
import com.scudata.cellset.datamodel.PgmCellSet;
import com.scudata.common.DBSession;
import com.scudata.common.Logger;
import com.scudata.common.UUID;
import com.scudata.dm.Context;
import com.scudata.dm.JobSpace;
import com.scudata.dm.JobSpaceManager;
import com.scudata.dm.Param;
import com.scudata.dm.ParamList;
import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.report.spl.SplConstants;
import io.nop.report.spl.model.SplModel;

import jakarta.annotation.PostConstruct;
import java.util.Iterator;
import java.util.Map;

import static io.nop.report.spl.SplErrors.ARG_PARAM_NAME;
import static io.nop.report.spl.SplErrors.ARG_PATH;
import static io.nop.report.spl.SplErrors.ERR_XPT_INVALID_SPL_MODEL_FILE_TYPE;
import static io.nop.report.spl.SplErrors.ERR_XPT_UNKNOWN_SPL_PARAM;
import static io.nop.report.spl.execute.SplHelper.spl2CellSet;

public class SplExecutor {

    private String configPath = "config/raqsoftConfig.xml";

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    @PostConstruct
    public void init() {
        try {
            Server.getInstance().initConfig(null, configPath);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @EvalMethod
    public Object executeForPath(IEvalScope scope, String path, Map<String, Object> params) {
        String fileType = StringHelper.fileType(path);
        if (!SplConstants.FILE_TYPES_SPL_MODEL.contains(fileType))
            throw new NopException(ERR_XPT_INVALID_SPL_MODEL_FILE_TYPE)
                    .param(ARG_PATH, path);

        SplModel model = (SplModel) ResourceComponentManager.instance().loadComponentModel(path);
        return executeForModel(scope, model, params);
    }

    @EvalMethod
    public Object executeForModel(IEvalScope scope, SplModel model, Map<String, Object> params) {
        return executeSPL(scope, model.getSource(), params);
    }

    @EvalMethod
    public Object executeSPL(IEvalScope scope, String source, Map<String, Object> params) {
        PgmCellSet pgmCellSet = spl2CellSet(source);  // dfx, sqlx 二进制文件
        Context context = new Context(); //上下文,参数..设置

        try {
            setParams(pgmCellSet, params);
            prepare(context);
            pgmCellSet.setContext(context);
            Object result = pgmCellSet.execute();
            return SplHelper.normalizeResult(result);
        } finally {
            close(context);
        }
    }

    private void setParams(PgmCellSet pgmCellSet, Map<String, Object> params) {
        if (params != null && !params.isEmpty()) {
            ParamList list = pgmCellSet.getParamList();
            params.forEach((name, value) -> {
                Param param = list.get(name);
                if (param == null)
                    throw new NopException(ERR_XPT_UNKNOWN_SPL_PARAM).param(ARG_PARAM_NAME, name);

                param.setValue(value);
            });
        }
    }

    private void prepare(Context context) {
        String uuid = UUID.randomUUID().toString();
        JobSpace jobSpace = JobSpaceManager.getSpace(uuid);
        context.setJobSpace(jobSpace);
    }

    /**
     * 这里的资源清理代码拷贝自 InternalConnection的close方法
     */
    private void close(Context ctx) {
        /* Close automatically opened connections */
        Map<String, DBSession> map = ctx.getDBSessionMap();
        if (map != null) {
            Iterator<String> iter = map.keySet().iterator();
            while (iter.hasNext()) {
                String name = iter.next().toString();
                DBSession sess = ctx.getDBSession(name);
                if (sess == null || sess.isClosed())
                    continue;
                Object o = ctx.getDBSession(name).getSession();
                if (o != null && o instanceof java.sql.Connection) {
                    try {
                        ((java.sql.Connection) o).close();
                    } catch (Exception e) {
                        Logger.warn(e.getMessage(), e);
                    }
                }
            }
        }
        /* Close the connection opened by the user through an expression */
        ParamList pl = ctx.getParamList();
        for (int i = 0; i < pl.count(); i++) {
            Object o = pl.get(i).getValue();
            if (o != null && o instanceof java.sql.Connection) {
                try {
                    ((java.sql.Connection) o).close();
                } catch (Exception e) {
                    Logger.warn(e.getMessage(), e);
                }
            }
        }
        JobSpace jobSpace = ctx.getJobSpace();
        /* Close the JobSpace */
        if (jobSpace != null) {
            jobSpace.closeResource();
            JobSpaceManager.closeSpace(jobSpace.getID());
        }
    }
}