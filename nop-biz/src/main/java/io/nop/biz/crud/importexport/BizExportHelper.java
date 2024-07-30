package io.nop.biz.crud.importexport;

import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.engine.GraphQLActionAuthChecker;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.impl.ObjSelectionMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_PROP_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_NOT_EXPORTABLE_FIELD;
import static io.nop.biz.crud.BizObjMetaHelper.getPropInfo;

public class BizExportHelper {
    public static boolean isAllowExport(IObjPropMeta propMeta, IServiceContext context) {
        // 必须是published且exportable
        if (!propMeta.isPublished() || !propMeta.isExportable())
            return false;

        if (!GraphQLActionAuthChecker.isAllowAccess(propMeta.getReadAuth(), context))
            return false;

        return true;
    }

    public static void checkExportable(String bizObjName, IObjMeta objMeta,
                                       FieldSelectionBean exportFields, IServiceContext context) {
        if (!exportFields.hasField())
            return;

        if (objMeta == null) {
            throw new NopException(ERR_BIZ_NOT_EXPORTABLE_FIELD)
                    .param(ARG_PROP_NAME, exportFields.getFirstSourceField())
                    .param(ARG_BIZ_OBJ_NAME, bizObjName);
        }

        Set<String> sourceFields = exportFields.getAllSourceFields();

        ObjSelectionMeta selectionMeta = objMeta.getSelection(BizConstants.SELECTION_EXPORTABLE);
        if (selectionMeta != null) {
            FieldSelectionBean selection = selectionMeta.getMapping();
            if (selection == null)
                throw new NopException(ERR_BIZ_NOT_EXPORTABLE_FIELD)
                        .param(ARG_PROP_NAME, exportFields.getFirstSourceField())
                        .param(ARG_BIZ_OBJ_NAME, bizObjName);

            Set<String> allExportableFields = selection.getAllSourceFields();
            if (!allExportableFields.containsAll(sourceFields)) {
                sourceFields.removeAll(allExportableFields);

                throw new NopException(ERR_BIZ_NOT_EXPORTABLE_FIELD)
                        .param(ARG_PROP_NAME, CollectionHelper.first(sourceFields))
                        .param(ARG_BIZ_OBJ_NAME, bizObjName);
            }
        }

        for (String field : sourceFields) {
            IObjPropMeta propMeta = objMeta.getProp(field);
            if (propMeta == null || !isAllowExport(propMeta, context))
                throw new NopException(ERR_BIZ_NOT_EXPORTABLE_FIELD)
                        .param(ARG_PROP_NAME, field)
                        .param(ARG_BIZ_OBJ_NAME, bizObjName);
        }
    }

    public static List<DictOptionBean> getExportableFields(String bizObjName,
                                                           IObjMeta objMeta, IServiceContext context) {
        if (objMeta == null)
            return Collections.emptyList();

        String locale = ContextProvider.currentLocale();

        ObjSelectionMeta selectionMeta = objMeta.getSelection(BizConstants.SELECTION_EXPORTABLE);
        if (selectionMeta != null) {
            FieldSelectionBean selection = selectionMeta.getMapping();
            if (selection == null)
                return Collections.emptyList();

            return getExportableFields(objMeta, selection, locale, bizObjName, context);
        }

        List<DictOptionBean> fields = new ArrayList<>();
        for (IObjPropMeta propMeta : objMeta.getProps()) {
            if (!isAllowExport(propMeta, context))
                continue;

            DictOptionBean option = getPropInfo(propMeta, locale, bizObjName);
            fields.add(option);
        }

        return fields;
    }

    private static List<DictOptionBean> getExportableFields(IObjMeta objMeta,
                                                            FieldSelectionBean selection,
                                                            String locale, String bizObjName,
                                                            IServiceContext context) {
        List<DictOptionBean> ret = new ArrayList<>();
        selection.forEachField(null, (alias, name) -> {
            IObjPropMeta propMeta = objMeta.getProp(name);
            if (propMeta == null)
                return;

            if (!isAllowExport(propMeta, context))
                return;

            ret.add(getPropInfo(propMeta, locale, bizObjName));
        });
        return ret;
    }
}
