package io.nop.tool.migrate;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.tool.refactor.FileExtFilter;

import java.io.File;
import java.util.List;

public class MigrateXlibTask extends AbstractMigrateTask {
    @Override
    public void migrateFile(File sourceFile, File targetFile) {
        XNode node = this.readXml(sourceFile);
        XNode tags = node.childByTag("tags");
        if (tags == null)
            return;
        tags.forEachChild(tag -> {
            tag.setAttr("name", tag.getTagName());
            tag.setTagName("tag");
        });

        List<XNode> children = tags.detachChildren();
        tags.remove();
        children.forEach(node::appendChild);

        ResourceHelper.writeXml(new FileResource(targetFile), node);
    }

    public static void main(String[] args) {
        File srcDir = new File("c:/can/nop/nop-entropy");
        File targetDir = new File("nop-tool/target/migrate");
        System.out.println(targetDir.getAbsolutePath());
        new MigrateXlibTask().migrateDir(srcDir, FileExtFilter.forFileExt("xlib"), targetDir);
    }
}
