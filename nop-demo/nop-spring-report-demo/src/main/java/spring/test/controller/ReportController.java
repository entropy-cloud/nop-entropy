package spring.test.controller;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.xlang.api.XLang;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@RestController
public class ReportController {
    @GetMapping(path = "/export-json", produces = MediaType.APPLICATION_JSON)
    public String exportJson() {
        Object bean = parseExcel();
        return JsonTool.serialize(bean, true);
    }

    DynamicObject parseExcel() {
        IResource resource = new ClassPathResource("classpath:data/test_imp3.test.xlsx");
        DynamicObject bean = (DynamicObject) ExcelHelper.loadXlsxObject("/nop/test/imp/test3.imp.xml", resource);
        return bean;
    }

    @GetMapping("/export-html")
    public ResponseEntity<String> exportHtml() {
        Object bean = parseExcel();

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("indexYears", Arrays.asList(2001, 2002, 2003, 2004));

        String html = ExcelReportHelper.getHtmlForXlsxObject("/nop/test/imp/test3.imp.xml", bean, scope);

        return buildResponse(html, "text/html;charset=UTF-8");
    }

    @GetMapping("/export-excel")
    public ResponseEntity<FileSystemResource> exportExcel() {
        Object bean = parseExcel();

        IResource resource = ResourceHelper.getTempResource("demo");
        try {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue("indexYears", Arrays.asList(2001, 2002, 2003, 2004));

            ExcelReportHelper.saveXlsxObject("/nop/test/imp/test3.imp.xml", resource, bean, scope);

            return downloadFile(resource, "export.xlsx");
        } catch (Exception e) {
            resource.delete();
            throw NopException.adapt(e);
        }
    }

    @GetMapping("/render-report-as-xlsx")
    public ResponseEntity<FileSystemResource> exportReportAsXlsx() {
        IReportEngine reportEngine = BeanContainer.getBeanByType(IReportEngine.class);

        IResource resource = ResourceHelper.getTempResource("demo");
        try {
            IEvalScope scope = XLang.newEvalScope();

            ITemplateOutput output = reportEngine.getRenderer("/nop/test/report/test-report.xpt.xlsx", "xlsx");

            output.generateToResource(resource, scope);

            return downloadFile(resource, "report-result.xlsx");
        } catch (Exception e) {
            resource.delete();
            throw NopException.adapt(e);
        }
    }

    @GetMapping("/render-report-as-html")
    public ResponseEntity<String> exportReportAsHtml() {
        IReportEngine reportEngine = BeanContainer.getBeanByType(IReportEngine.class);


        IEvalScope scope = XLang.newEvalScope();

        ITextTemplateOutput output = reportEngine.getHtmlRenderer("/nop/test/report/test-report.xpt.xlsx");

        String html = output.generateText(scope);

        return buildResponse(html, "text/html;charset=UTF-8");
    }

    <T> ResponseEntity<T> buildResponse(T body, String contentType){
        return buildResponse(body, contentType,null);
    }

    <T> ResponseEntity<T> buildResponse(T body, String contentType, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("content-type", contentType);
        if(fileName != null){
            String encoded = StringHelper.encodeURL(fileName);
            headers.set("Content-Disposition", "attachment; filename=" + encoded);
        }
        ResponseEntity<T> response = new ResponseEntity<>(body, headers, HttpStatus.OK);
        return response;
    }

    private ResponseEntity<FileSystemResource> downloadFile(IResource resource, String fileName) {
        ResponseEntity<FileSystemResource> response = buildResponse(
                new FileSystemResource(resource.toFile()), "application/octet-stream;charset=UTF-8",fileName);

        GlobalExecutors.globalTimer().schedule(() -> {
            resource.delete();
            return null;
        }, 5, TimeUnit.MINUTES);

        return response;
    }

}
