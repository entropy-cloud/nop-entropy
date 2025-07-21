package io.nop.ooxml.docx.utils;

import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.ooxml.docx.model.WordOfficePackage;

import java.io.File;

public class DocxHelper {
    public static void extractImagesToDir(IResource resource, File dir) {
        WordOfficePackage pkg = new WordOfficePackage();
        pkg.loadFromResource(resource);
        try {
            pkg.saveImagesToDir(dir);
        } finally {
            IoHelper.safeCloseObject(pkg);
        }
    }
}
