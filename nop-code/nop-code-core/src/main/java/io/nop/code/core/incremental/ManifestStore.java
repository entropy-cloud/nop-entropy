package io.nop.code.core.incremental;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.type.utils.GenericTypeHelper;
public class ManifestStore {

    private static final Logger LOG = LoggerFactory.getLogger(ManifestStore.class);

    /**
     * 保存指纹到 VFS 资源
     */
    public void save(String manifestPath, List<FileFingerprint> fingerprints) {
        String json = JsonTool.stringify(fingerprints);
        IResource resource = VirtualFileSystem.instance().getResource(manifestPath);
        resource.writeText(json, null);
    }

    /**
     * 从 VFS 资源加载指纹
     */
    public List<FileFingerprint> load(String manifestPath) {
        IResource resource = VirtualFileSystem.instance().getResource(manifestPath);
        if (!resource.exists()) {
            return new ArrayList<>();
        }

        String content = resource.readText();

        try {
            List<FileFingerprint> result = JsonTool.parseBeanFromText(content,
                    GenericTypeHelper.buildListType(
                            ReflectionManager.instance().buildRawType(FileFingerprint.class)));
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            LOG.warn("Failed to parse manifest file: {}", manifestPath, e);
            return new ArrayList<>();
        }
    }
}
