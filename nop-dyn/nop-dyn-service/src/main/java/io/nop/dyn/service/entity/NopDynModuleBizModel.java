/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.IResource;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.DaoErrors;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.model.DynEntityMetaToOrmModel;
import io.nop.dyn.dao.model.OrmModelToDynEntityMeta;
import io.nop.dyn.service.codegen.DynCodeGen;
import io.nop.dyn.service.codegen.GptCodeGen;
import io.nop.file.core.FileConstants;
import io.nop.orm.IOrmEntityFileStore;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.dyn.biz.INopDynModuleBiz;
import jakarta.inject.Inject;

import java.util.List;

import static io.nop.dyn.service.NopDynErrors.ARG_MODULE_NAME;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_MODULE_ID_CONFLICT;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_MODULE_NAME_EXISTS;

@BizModel("NopDynModule")
public class NopDynModuleBizModel extends CrudBizModel<NopDynModule> implements INopDynModuleBiz {

    @Inject
    IOrmEntityFileStore fileStore;

    @Inject
    DynCodeGen dynCodeGen;

    public NopDynModuleBizModel() {
        setEntityName(NopDynModule.class.getName());
    }

    @Override
    public void saveEntity(NopDynModule entity, String action, IServiceContext context) {
        if (entity != null)
            checkModuleIdNotShadowingStatic(entity.getModuleName());
        super.saveEntity(entity, action, context);
    }

    /**
     * 模块级删除缓存失效（I-04/C6-101，plan 2525 裁定⑨；形态照抄 NopDynFunctionMetaBizModel
     * afterEntityChange 先例）。模块级删除涉及 codeCache 模块移除 / VFS in-memory 层 /
     * bizObjectManager 三面——全量 reloadModel 是既有唯一同步三面的通道（按模块精确失效
     * 无法原子覆盖共享全局态）。已删模型在 reload 完成前的在途请求按旧模型完成（生成/加载
     * 通道既有语义，窗口声明落 nop-dyn.md）。
     */
    @io.nop.api.core.annotations.biz.BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopDynModule entity, @Name("action") String action,
                                     IServiceContext context) {
        super.afterEntityChange(entity, action, context);

        if (BizConstants.METHOD_DELETE.equals(action)) {
            dynCodeGen.removeDynModule(entity.getNopModuleId());
            dynCodeGen.reloadModel();
        }
    }

    @BizMutation
    public NopDynModule importExcel(@Name("importFile") String importFile,
                                    @Optional @Name("removeNotExisting") boolean removeNotExisting,
                                    IServiceContext context) {
        String fileId = fileStore.decodeFileId(importFile);
        // 总是处理上传的临时文件
        String objId = FileConstants.TEMP_BIZ_OBJ_ID;
        IResource resource = fileStore.getFileResource(fileId, getBizObjName(), objId, NopDynDaoConstants.PROP_IMPORT_FILE);

        OrmModel ormModel = (OrmModel) ExcelReportHelper.loadXlsxObject(OrmModelConstants.ORM_IMPL_PATH, resource);

        IEntityDao<NopDynModule> dao = dao();
        NopDynModule entity = dao.newEntity();
        entity.setStatus(NopDynDaoConstants.MODULE_STATUS_UNPUBLISHED);
        entity.setModuleName((String) ormModel.prop_get(OrmModelConstants.EXT_APP_NAME));
        checkModuleIdNotShadowingStatic(entity.getModuleName());
        entity.setDisplayName(entity.getModuleName());
        entity.setBasePackageName((String) ormModel.prop_get(OrmModelConstants.EXT_BASE_PACKAGE_NAME));
        entity.setMavenGroupId((String) ormModel.prop_get(OrmModelConstants.EXT_MAVEN_GROUP_ID));
        entity.setModuleVersion(1);

        new OrmModelToDynEntityMeta(removeNotExisting).transformModule(ormModel, entity);

        fileStore.detachFile(fileId, getBizObjName(), objId, NopDynDaoConstants.PROP_IMPORT_FILE);
        checkDataAuth(BizConstants.METHOD_SAVE, entity, context);

        dao.saveEntity(entity);
        return entity;
    }

    @BizQuery
    public WebContentBean exportExcel(@Optional @Name("ids") List<String> ids, @Optional @Name("id") String id, IServiceContext context) {
        if (StringHelper.isEmpty(id))
            id = CollectionHelper.first(ids);

        NopDynModule entity = get(id, false, context);

        OrmModel model = new DynEntityMetaToOrmModel(true).transformModule(entity);

        String fileName = entity.getModuleName() + ".orm.xlsx";

        return ExcelReportHelper.downloadXlsx(fileName, OrmModelConstants.ORM_IMPL_PATH, model, 5);
    }

    /**
     * P1-3.7-001（plan 2385）：动态模块 moduleName 遮蔽防线——
     * ①`nop-` 前缀为平台保留前缀（静态平台模块全集均为 nop-子名 命名模式）；
     * ②moduleName 对应的 moduleId 不得与已注册静态模块 id 冲突。
     * 动态模块资源经 VFS in-memory 第一优先层加载，与静态模块同路径即整体遮蔽
     * （xbiz/xmeta/orm），导致口令哈希直读/XPL 注入/ORM 定义替换。
     */
    public static void checkModuleIdNotShadowingStatic(String moduleName) {
        if (StringHelper.isEmpty(moduleName))
            return;
        if (moduleName.startsWith("nop-"))
            throw new NopException(ERR_DYN_MODULE_ID_CONFLICT)
                    .param(ARG_MODULE_NAME, moduleName)
                    .param("moduleId", moduleName.replace('-', '/'));

        // 仅校验静态注册模块（modules 集合）——动态模块自身的重复由表内去重负责，
        // 若含动态集合会把既有动态模块的二次 generateByAI 误报为冲突
        String moduleId = moduleName.replace('-', '/');
        IResource moduleMarker = VirtualFileSystem.instance()
                .getResource("/" + moduleId + "/_module");
        if (moduleMarker != null && moduleMarker.exists()) {
            throw new NopException(ERR_DYN_MODULE_ID_CONFLICT)
                    .param(ARG_MODULE_NAME, moduleName)
                    .param("moduleId", moduleId);
        }
    }

    @BizMutation
    public void generateByAI(@Name("response") String response,
                             @Optional @Name("moduleName") String moduleName,
                             IServiceContext context) {
        if (StringHelper.isEmpty(moduleName))
            moduleName = "app-demo";

        checkModuleIdNotShadowingStatic(moduleName);

        OrmModel ormModel = new GptCodeGen().generateOrmModel(response);
        ormModel.init();
        IEntityDao<NopDynModule> dao = dao();

        // moduleName没有唯一索引约束，重复调用会创建并发布多个同名模块，这里显式去重
        NopDynModule example = dao.newEntity();
        example.setModuleName(moduleName);
        if (dao.findFirstByExample(example) != null)
            throw new NopException(ERR_DYN_MODULE_NAME_EXISTS).param(ARG_MODULE_NAME, moduleName);

        NopDynModule entity = dao.newEntity();
        entity.setStatus(NopDynDaoConstants.MODULE_STATUS_UNPUBLISHED);
        entity.setModuleName(moduleName);
        entity.setDisplayName(entity.getModuleName());
        entity.setBasePackageName((String) ormModel.prop_get(OrmModelConstants.EXT_BASE_PACKAGE_NAME));
        entity.setMavenGroupId((String) ormModel.prop_get(OrmModelConstants.EXT_MAVEN_GROUP_ID));
        entity.setModuleVersion(1);

        new OrmModelToDynEntityMeta(true).transformModule(ormModel, entity);
        entity.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        // 与importExcel保持一致的数据权限检查
        checkDataAuth(BizConstants.METHOD_SAVE, entity, context);
        try {
            dao.saveEntity(entity);
            dao.flushSession();
        } catch (Exception e) {
            // 唯一索引违反转译（I-03/C1-008，plan 2525 裁定⑧）：并发双建的输家得到与顺序路径
            // 一致的语义化错误（沿 addDefaultSequence 的 ERR_SQL_DUPLICATE_KEY 识别先例），
            // 禁裸DB异常暴露与静默重试吞掉。应用层 findFirstByExample 检查（上方）为顺序路径
            // 友好失败，唯一索引为跨节点并发权威防护——两层并存
            NopException ne = e instanceof NopException ? (NopException) e : null;
            if (ne == null || !DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(ne.getErrorCode()))
                throw e;
            throw new NopException(ERR_DYN_MODULE_NAME_EXISTS).param(ARG_MODULE_NAME, moduleName);
        }

        dynCodeGen.generateForModule(entity);
        dynCodeGen.reloadModel();
    }
}
