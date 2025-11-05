package io.nop.pdf.extract.processor;

import io.nop.pdf.extract.IResourceDocumentProcessor;
import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.parser.SimpleTocDetector;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.TocTable;

public class DefaultTocTableProcessor implements IResourceDocumentProcessor{

	@Override
	public void process(ResourceDocument doc, ResourceParseConfig config) {
		 SimpleTocDetector detector = new SimpleTocDetector();
		 detector.setStartMarker(config.getTocStartMark());
		 detector.setEndMarker(config.getTocEndMark());
		 TocTable tocTable = detector.detect(doc);
		 doc.setTocTable(tocTable);
	}
}
