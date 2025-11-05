package io.nop.pdf.extract.export;

import java.io.IOException;
import java.io.Writer;

import io.nop.pdf.extract.struct.ResourceDocument;

public class ResourceDocumentHtmlExporter extends
		AbstractResourceDocumentExporter {

	@Override
	public void exportToWriter(ResourceDocument doc, Writer out, String encoding)
			throws IOException {
		new DefaultResourceHtmlWriter(doc.getFileName()).writeDoc(doc, out);
	}
}