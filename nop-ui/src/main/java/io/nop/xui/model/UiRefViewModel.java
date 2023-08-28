/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xui.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xui.XuiConstants;
import io.nop.xui.model._gen._UiRefViewModel;

import static io.nop.xui.XuiErrors.ARG_VIEW_PATH;
import static io.nop.xui.XuiErrors.ERR_XUI_REF_VIEW_MUST_HAS_PAGE_OR_GRID_OR_FORM_ATTR;
import static io.nop.xui.XuiErrors.ERR_XUI_REF_VIEW_NOT_EXISTS;
import static io.nop.xui.XuiErrors.ERR_XUI_REF_VIEW_PAGE_GRID_FORM_ONLY_ALLOW_ONE_NON_EMPTY;

public class UiRefViewModel extends _UiRefViewModel implements INeedInit {
    public UiRefViewModel() {

    }

    @Override
    public void init() {
        validate();
    }

    public void validate() {
        String path = getPath();

        if (StringHelper.isEmpty(path)) {
            String resourcePath = resourcePath();
            String fileType = StringHelper.fileType(resourcePath);
            if (XuiConstants.FILE_TYPE_VIEW_XML.equals(fileType)) {
                setPath(resourcePath);
                path = resourcePath;
            } else if (XuiConstants.FILE_TYPE_XMETA.equals(fileType)) {
                String moduleId = ResourceHelper.getModuleId(resourcePath);
                String objName = StringHelper.fileNameNoExt(resourcePath);
                String baseObjName = StringHelper.firstPart(objName, '_');
                String viewPath = "/" + moduleId + "/pages/" + baseObjName + "/" + objName + "." + XuiConstants.FILE_TYPE_VIEW_XML;
                setPath(viewPath);
                path = viewPath;
            }
        }

        if (StringHelper.isEmpty(path)) {
            throw new NopException(ERR_XUI_REF_VIEW_NOT_EXISTS)
                    .source(this).param(ARG_VIEW_PATH, path);
        }

        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (!resource.exists())
            throw new NopException(ERR_XUI_REF_VIEW_NOT_EXISTS)
                    .source(this).param(ARG_VIEW_PATH, path);

        String fileType = StringHelper.fileType(path);
        if (XuiConstants.FILE_TYPE_VIEW_XML.endsWith(fileType)) {
            String page = getPage();
            String form = getForm();
            String grid = getGrid();
            int count = 0;
            if (!StringHelper.isEmpty(page))
                count++;
            if (!StringHelper.isEmpty(form))
                count++;
            if (!StringHelper.isEmpty(grid))
                count++;

            if (count == 0)
                throw new NopException(ERR_XUI_REF_VIEW_MUST_HAS_PAGE_OR_GRID_OR_FORM_ATTR)
                        .source(this).param(ARG_VIEW_PATH, path);

            if (count > 1)
                throw new NopException(ERR_XUI_REF_VIEW_PAGE_GRID_FORM_ONLY_ALLOW_ONE_NON_EMPTY)
                        .source(this).param(ARG_VIEW_PATH, path);
        }
    }
}