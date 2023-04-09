package io.nop.report.spl.model;

import com.scudata.cellset.datamodel.PgmCellSet;
import com.scudata.util.CellSetUtil;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.report.spl.SplConstants;
import io.nop.report.spl.execute.SplHelper;

import java.io.InputStream;

import static io.nop.report.spl.SplErrors.ARG_PATH;
import static io.nop.report.spl.SplErrors.ERR_XPT_UNKNOWN_SPL_RESOURCE;

public class SplModelLoader implements IResourceObjectLoader<SplModel> {

    @Override
    public SplModel loadObjectFromPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (!resource.exists())
            throw new NopException(ERR_XPT_UNKNOWN_SPL_RESOURCE)
                    .param(ARG_PATH, path);

        SplModel ret = new SplModel();
        ret.setLocation(SourceLocation.fromPath(path));

        String fileType = StringHelper.fileType(resource.getName());

        if (SplConstants.FILE_TYPE_SPL.equals(fileType)) {
            String text = ResourceHelper.readText(resource);
            SplHelper.spl2CellSet(text);
            ret.setSource(text);
        } else {
            InputStream is = resource.getInputStream();
            try {
                PgmCellSet ps = CellSetUtil.readPgmCellSet(is);
                ret.setSource(SplHelper.cellSetToSpl(ps));
            } catch (Exception e) {
                throw NopException.adapt(e);
            } finally {
                IoHelper.safeCloseObject(is);
            }
        }

        return ret;
    }
}