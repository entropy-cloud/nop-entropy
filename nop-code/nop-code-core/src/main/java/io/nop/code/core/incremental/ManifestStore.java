package io.nop.code.core.incremental;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.core.reflect.ReflectionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 指纹清单持久化。将文件指纹列表序列化为JSON数组保存到磁盘，用于增量检测的状态持久化。
 */
public class ManifestStore {

    public void save(Path manifestFile, List<FileFingerprint> fingerprints) throws IOException {
        Path parent = manifestFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String json = JsonTool.stringify(fingerprints);
        Files.writeString(manifestFile, json);
    }

    public List<FileFingerprint> load(Path manifestFile) throws IOException {
        if (!Files.exists(manifestFile)) {
            return new ArrayList<>();
        }

        String content = Files.readString(manifestFile);

        try {
            List<FileFingerprint> result = JsonTool.parseBeanFromText(content,
                    GenericTypeHelper.buildListType(
                            ReflectionManager.instance().buildRawType(FileFingerprint.class)));
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            // Malformed JSON returns empty list (matches existing behavior)
            return new ArrayList<>();
        }
    }
}
