package io.nop.code.core.incremental;

import io.nop.core.resource.IResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增量变更检测器。通过两级检测策略（mtime快速比较 + SHA-256内容哈希）识别文件变更。
 * 支持基于 Path 和 IResource 的两种操作方式。
 */
public class IncrementalDetector {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    // ========== Path-based methods ==========

    public FileFingerprint computeFingerprint(Path file) throws IOException {
        byte[] hash = computeSha256(file);
        String contentHash = bytesToHex(hash);
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

        List<Path> addedFiles = new ArrayList<>();
        List<Path> modifiedFiles = new ArrayList<>();
        List<Path> deletedFiles = new ArrayList<>();
        List<Path> unchangedFiles = new ArrayList<>();

        for (FileFingerprint prev : previous) {
            if (!currentMap.containsKey(prev.getFilePath())) {
                deletedFiles.add(Path.of(prev.getFilePath()));
            }
        }

        for (Path currentFile : currentFiles) {
            String pathStr = currentFile.toString();
            FileFingerprint prev = prevMap.get(pathStr);

            if (prev == null) {
                addedFiles.add(currentFile);
            } else {
                long currentMtime = Files.getLastModifiedTime(currentFile).toMillis();
                long currentSize = Files.size(currentFile);

                if (currentMtime == prev.getLastModified() && currentSize == prev.getFileSize()) {
                    unchangedFiles.add(currentFile);
                } else {
                    byte[] currentHash = computeSha256(currentFile);
                    String currentHashHex = bytesToHex(currentHash);

                    if (currentHashHex.equals(prev.getContentHash())) {
                        unchangedFiles.add(currentFile);
                    } else {
                        modifiedFiles.add(currentFile);
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
        byte[] hash = computeSha256FromStream(resource.getInputStream());
        String contentHash = bytesToHex(hash);
        long lastModified = resource.lastModified();
        long fileSize = resource.length();
        return new FileFingerprint(resource.getStdPath(), contentHash, lastModified, fileSize);
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
            currentMap.put(r.getStdPath(), r);
        }

        List<IResource> addedFiles = new ArrayList<>();
        List<IResource> modifiedFiles = new ArrayList<>();
        List<IResource> deletedFiles = new ArrayList<>();
        List<IResource> unchangedFiles = new ArrayList<>();

        // 检测已删除的文件
        for (FileFingerprint prev : previous) {
            if (!currentMap.containsKey(prev.getFilePath())) {
                deletedFiles.add(new DeletedResourceStub(prev.getFilePath()));
            }
        }

        // 检测新增和修改的文件
        for (IResource currentResource : currentResources) {
            String pathStr = currentResource.getStdPath();
            FileFingerprint prev = prevMap.get(pathStr);

            if (prev == null) {
                addedFiles.add(currentResource);
            } else {
                long currentMtime = currentResource.lastModified();
                long currentSize = currentResource.length();

                if (currentMtime == prev.getLastModified() && currentSize == prev.getFileSize()) {
                    unchangedFiles.add(currentResource);
                } else {
                    byte[] currentHash = computeSha256FromStream(currentResource.getInputStream());
                    String currentHashHex = bytesToHex(currentHash);

                    if (currentHashHex.equals(prev.getContentHash())) {
                        unchangedFiles.add(currentResource);
                    } else {
                        modifiedFiles.add(currentResource);
                    }
                }
            }
        }

        ChangeSet changeSet = new ChangeSet();
        changeSet.setAddedFiles(toPaths(addedFiles));
        changeSet.setModifiedFiles(toPaths(modifiedFiles));
        changeSet.setDeletedFiles(toPaths(deletedFiles));
        changeSet.setUnchangedFiles(toPaths(unchangedFiles));
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

    // ========== Private helpers ==========

    private byte[] computeSha256(Path file) throws IOException {
        return computeSha256FromStream(Files.newInputStream(file));
    }

    private byte[] computeSha256FromStream(InputStream in) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = in) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    private static List<Path> toPaths(List<IResource> resources) {
        List<Path> paths = new ArrayList<>(resources.size());
        for (IResource r : resources) {
            paths.add(Path.of(r.getStdPath()));
        }
        return paths;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >> 4) & 0x0f]);
            sb.append(HEX_CHARS[b & 0x0f]);
        }
        return sb.toString();
    }
}
