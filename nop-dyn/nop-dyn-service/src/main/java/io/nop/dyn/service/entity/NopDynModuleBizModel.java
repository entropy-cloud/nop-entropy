
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.WebContentBean;
import io.nop.biz.BizConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;
import io.nop.dao.api.IEntityDao;
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
import jakarta.inject.Inject;

import java.util.List;

@BizModel("NopDynModule")
public class NopDynModuleBizModel extends CrudBizModel<NopDynModule> {

    @Inject
    IOrmEntityFileStore fileStore;

    @Inject
    DynCodeGen dynCodeGen;

    public NopDynModuleBizModel() {
        setEntityName(NopDynModule.class.getName());
    }

    @BizMutation
    public NopDynModule importExcel(@Name("importFile") String importFile, IServiceContext context) {
        String fileId = fileStore.decodeFileId(importFile);
        // 总是处理上传的临时文件
        String objId = FileConstants.TEMP_BIZ_OBJ_ID;
        IResource resource = fileStore.getFileResource(fileId, getBizObjName(), objId, NopDynDaoConstants.PROP_IMPORT_FILE);

        OrmModel ormModel = (OrmModel) ExcelReportHelper.loadXlsxObject(OrmModelConstants.ORM_IMPL_PATH, resource);

        IEntityDao<NopDynModule> dao = dao();
        NopDynModule entity = dao.newEntity();
        entity.setStatus(NopDynDaoConstants.APP_STATUS_UNPUBLISHED);
        entity.setModuleName((String) ormModel.prop_get(OrmModelConstants.EXT_APP_NAME));
        entity.setDisplayName(entity.getModuleName());
        entity.setBasePackageName((String) ormModel.prop_get(OrmModelConstants.EXT_BASE_PACKAGE_NAME));
        entity.setMavenGroupId((String) ormModel.prop_get(OrmModelConstants.EXT_MAVEN_GROUP_ID));
        entity.setModuleVersion(1);

        new OrmModelToDynEntityMeta().transformModule(ormModel, entity);

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

    @BizMutation
    public void generateByAI(@Name("response") String response) {
        OrmModel ormModel = new GptCodeGen().generateOrmModel(response);
        IEntityDao<NopDynModule> dao = dao();
        NopDynModule entity = dao.newEntity();
        entity.setStatus(NopDynDaoConstants.APP_STATUS_UNPUBLISHED);
        entity.setModuleName("app-demo");
        entity.setDisplayName(entity.getModuleName());
        entity.setBasePackageName((String) ormModel.prop_get(OrmModelConstants.EXT_BASE_PACKAGE_NAME));
        entity.setMavenGroupId((String) ormModel.prop_get(OrmModelConstants.EXT_MAVEN_GROUP_ID));
        entity.setModuleVersion(1);

        new OrmModelToDynEntityMeta().transformModule(ormModel, entity);
        entity.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        dao.saveEntity(entity);

        dynCodeGen.generateForModule(entity);
        dynCodeGen.reloadModel();
    }
}
