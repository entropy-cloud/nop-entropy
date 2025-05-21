package io.nop.pdf.extract;

import java.util.Arrays;
import java.util.List;

public interface ResourceDocumentConstants {
	String EXPORT_TYPE_HTML = "html";
	String EXPORT_TYPE_TXT = "txt";
	String EXPORT_TYPE_IMAGE = "image";
	
	List<String> EXPORT_TYPES = Arrays.asList(EXPORT_TYPE_HTML, EXPORT_TYPE_TXT, EXPORT_TYPE_IMAGE);
}