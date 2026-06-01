package io.nop.code.core.incremental;

import io.nop.code.core.util.DigestHelper;
import io.nop.core.resource.IResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增量变更检测器。通过两级检测策略（mtime快速比较 + SHA-256内容哈希）识别文件变更。
 * 支持基于 Path 和 IResource 的两种操作方式。
 */
public class IncrementalDetector {

    // ========== Path-based methods ==========

    public FileFingerprint computeFingerprint(Path file) throws IOException {
        String contentHash = DigestHelper.sha256Hex(file);
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        long fileSize = Files.size(file);
        return new FileFingerprint(file.toString(), contentHash, lastModified, fileSize);
    }

    public ChangeSet detectChanges(List<FileFingerprint> previous, List<Path> currentFiles) throws IOException {
        Map<String, FileFingerprint> prevMap = new HashMap<>();
        for (FileFingerprint fp : previous) {
            prevMap.put(fp.getFilePath(), fp);
        }

        Map<String, Path> currentMap = new HashMap<>();
        for (Path p : currentFiles) {
            currentMap.put(p.toString(), p);
        }

        List<String> addedFiles = new ArrayList<>();
        List<String> modifiedFiles = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();
        List<String> unchangedFiles = new ArrayList<>();

        for (FileFingerprint prev : previous) {
            if (!currentMap.containsKey(prev.getFilePath())) {
                deletedFiles.add(prev.getFilePath());
            }
        }

        for (Path currentFile : currentFiles) {
            String pathStr = currentFile.toString();
            FileFingerprint prev = prevMap.get(pathStr);

            if (prev == null) {
                addedFiles.add(pathStr);
            } else {
                long currentMtime = Files.getLastModifiedTime(currentFile).toMillis();
                long currentSize = Files.size(currentFile);

                if (currentMtime == prev.getLastModified() && currentSize == prev.getFileSize()) {
                    unchangedFiles.add(pathStr);
                } else {
                    String currentHashHex = DigestHelper.sha256Hex(currentFile);

                    if (currentHashHex.equals(prev.getContentHash())) {
                        unchangedFiles.add(pathStr);
                    } else {
                        modifiedFiles.add(pathStr);
                    }
                }
            }
        }

        ChangeSet changeSet = new ChangeSet();
        changeSet.setAddedFiles(addedFiles);
        changeSet.setModifiedFiles(modifiedFiles);
        changeSet.setDeletedFiles(deletedFiles);
        changeSet.setUnchangedFiles(unchangedFiles);
        return changeSet;
    }

    public List<FileFingerprint> computeFingerprints(List<Path> files) throws IOException {
        List<FileFingerprint> fingerprints = new ArrayList<>(files.size());
        for (Path file : files) {
            fingerprints.add(computeFingerprint(file));
        }
        return fingerprints;
    }

    // ========== IResource-based methods ==========

    /**
     * 从 IResource 计算文件指纹
     */
    public FileFingerprint computeFingerprint(IResource resource) throws IOException {
        String contentHash;
        try (InputStream is = resource.getInputStream()) {
            contentHash = DigestHelper.sha256HexFromStream(is);
        }
        long lastModified = resource.lastModified();
        long fileSize = resource.length();
        return new FileFingerprint(resource.getPath(), contentHash, lastModified, fileSize);
    }

    /**
     * 基于 IResource 的增量变更检测
     */
    public ChangeSet detectResourceChanges(List<FileFingerprint> previous, List<IResource> currentResources) throws IOException {
        Map<String, FileFingerprint> prevMap = new HashMap<>();
        for (FileFingerprint fp : previous) {
            prevMap.put(fp.getFilePath(), fp);
        }

        Map<String, IResource> currentMap = new HashMap<>();
        for (IResource r : currentResources) {
            currentMap.put(r.getPath(), r);
        }

        List<String> addedFiles = new ArrayList<>();
        List<String> modifiedFiles = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();
        List<String> unchangedFiles = new ArrayList<>();

        // 检测已删除的文件
        for (FileFingerprint prev : previous) {
            if (!currentMap.containsKey(prev.getFilePath())) {
                deletedFiles.add(prev.getFilePath());
            }
        }

        // 检测新增和修改的文件
        for (IResource currentResource : currentResources) {
            String pathStr = currentResource.getPath();
            FileFingerprint prev = prevMap.get(pathStr);

            if (prev == null) {
                addedFiles.add(pathStr);
            } else {
                long currentMtime = currentResource.lastModified();
                long currentSize = currentResource.length();

                if (currentMtime == prev.getLastModified() && currentSize == prev.getFileSize()) {
                    unchangedFiles.add(pathStr);
                } else {
                    String currentHashHex;
                    try (InputStream is = currentResource.getInputStream()) {
                        currentHashHex = DigestHelper.sha256HexFromStream(is);
                    }

                    if (currentHashHex.equals(prev.getContentHash())) {
                        unchangedFiles.add(pathStr);
                    } else {
                        modifiedFiles.add(pathStr);
                    }
                }
            }
        }

        ChangeSet changeSet = new ChangeSet();
        changeSet.setAddedFiles(addedFiles);
        changeSet.setModifiedFiles(modifiedFiles);
        changeSet.setDeletedFiles(deletedFiles);
        changeSet.setUnchangedFiles(unchangedFiles);
        return changeSet;
    }

    /**
     * 批量计算 IResource 列表的指纹
     */
    public List<FileFingerprint> computeResourceFingerprints(List<IResource> resources) throws IOException {
        List<FileFingerprint> fingerprints = new ArrayList<>(resources.size());
        for (IResource resource : resources) {
            fingerprints.add(computeFingerprint(resource));
        }
        return fingerprints;
    }

    /**
     * 从 IFingerprintStore 加载旧指纹，与当前 IResource 列表对比检测变更
     */
    public ChangeSet detectChangesFromStore(IFingerprintStore store, String indexId,
                                            List<IResource> currentResources) throws IOException {
        List<FileFingerprint> previous = store.loadFingerprints(indexId);
        return detectResourceChanges(previous, currentResources);
    }

    /**
     * 计算 IResource 列表的指纹并保存到 store
     */
    public List<FileFingerprint> computeAndSaveFingerprints(IFingerprintStore store, String indexId,
                                                            List<IResource> resources) throws IOException {
        List<FileFingerprint> fingerprints = computeResourceFingerprints(resources);
        store.saveFingerprints(indexId, fingerprints);
        return fingerprints;
    }
}
