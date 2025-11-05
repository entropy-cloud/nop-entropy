package io.nop.pdf.extract.export;

import io.nop.pdf.extract.IResourceDocumentExporter;
import io.nop.pdf.extract.ResourceDocumentConstants;
import io.nop.api.core.exceptions.NopScriptError;

import java.util.HashMap;
import java.util.Map;

public class ResourceDocumentExporters {
    static Map<String, IResourceDocumentExporter> exporters = new HashMap<String, IResourceDocumentExporter>();

    static {
        exporters.put(ResourceDocumentConstants.EXPORT_TYPE_HTML, new ResourceDocumentHtmlExporter());
        exporters.put(ResourceDocumentConstants.EXPORT_TYPE_TXT, new ResourceDocumentTxtExporter());
        exporters.put(ResourceDocumentConstants.EXPORT_TYPE_IMAGE, new ResourceDocumentImageExporter());
    }

    public static IResourceDocumentExporter getExporter(String exportType) {
        IResourceDocumentExporter exporter = exporters.get(exportType);
        if (exporter == null)
            throw new NopScriptError("pdf.err_unknown_export_type").param("exportType", exportType);
        return exporter;
    }
}
