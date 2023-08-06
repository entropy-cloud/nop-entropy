package io.nop.file.quarkus.web;

import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.file.core.AbstractFileService;
import io.nop.file.core.IFileRecord;
import io.nop.graphql.core.web.JaxrsHelper;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Path("")
@ApplicationScoped
public class QuarkusFileService extends AbstractFileService {
    @Path("/f/upload")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@MultipartForm FileUploadForm form) {
        String locale = ContextProvider.currentLocale();
        try {
            long length = form.getFile().available();
            checkMaxLength(length);

            String fileId = fileStore.saveFile(form, maxFileLength);

            Map<String, Object> data = new HashMap<>();
            data.put("value", fileId);
            ApiResponse<Map<String, Object>> res = ApiResponse.buildSuccess(data);
            return Response.ok(res).build();
        } catch (Exception e) {
            ApiResponse<?> response = ErrorMessageManager.instance().buildResponse(locale, e);
            return JaxrsHelper.buildJaxrsResponse(response);
        }
    }

    @BizQuery
    public WebContentBean download(@Name("fileId") String fileId, @Name("contentType") String contentType) {
        IFileRecord record = fileStore.getFile(fileId);
        if (StringHelper.isEmpty(contentType))
            contentType = record.getContentType();

        return new WebContentBean(contentType, record.getResource(), record.getFileName());
    }
}
