package io.nop.ai.translate;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.markdown.utils.MarkdownHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Disabled
public class FixDocLinks extends BaseTestCase {

    @Test
    public void testFix() {
        File rootDir = getProjectDir();
        File dir = new File(rootDir, "docs-en");

        Map<String, String> map = collectPaths();


        FileHelper.walk(dir, file -> {
            if (file.getName().endsWith(".md")) {
                String text = FileHelper.readText(file, null);
                List<IntRangeBean> ranges = MarkdownHelper.findLinkPositions(text, false);

                List<IntRangeBean> changeRanges = new ArrayList<>();
                List<String> changeUrls = new ArrayList<>();

                for (IntRangeBean range : ranges) {
                    String url = MarkdownHelper.getLinkUrl(text, range);
                    if (url.contains("/src/main/") && url.contains("gitee.com/")) {
                        int pos = url.indexOf("/src/main");
                        String basePath = url.substring(pos);
                        String mapPath = map.get(basePath);
                        if (mapPath != null) {
                            String normalizedUrl = StringHelper.appendPath(
                                    "https://gitee.com/canonical-entropy/nop-entropy/blob/master", mapPath);
                            changeRanges.add(range);
                            changeUrls.add(normalizedUrl);
                        }
                    }
                }

                String normalizedText = MarkdownHelper.changeLinkUrl(text, changeRanges, changeUrls);
                if (!text.equals(normalizedText)) {
                    FileHelper.writeText(file, normalizedText, null);
                }
            }
            return FileVisitResult.CONTINUE;
        });
    }

    File getProjectDir() {
        return FileHelper.getAbsoluteFile(new File(getModuleDir(), "../../.."));
    }

    Map<String, String> collectPaths() {
        File rootDir = getProjectDir();

        Map<String, String> map = new TreeMap<>();

        FileHelper.walk(rootDir, file -> {
            String relativePath = FileHelper.getRelativePath(rootDir, file);
            int pos = relativePath.indexOf("/src/main");
            if (pos > 0) {
                String basePath = relativePath.substring(pos);
                map.put(basePath, relativePath);
            }
            return FileVisitResult.CONTINUE;
        });

        return map;
    }
}
