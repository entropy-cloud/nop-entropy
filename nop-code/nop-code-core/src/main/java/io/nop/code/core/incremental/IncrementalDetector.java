package io.nop.code.core.incremental;

import java.io.IOException;
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
 */
public class IncrementalDetector {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

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

    private byte[] computeSha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        java.io.InputStream in = Files.newInputStream(file);
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            in.close();
        }

        return digest.digest();
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
