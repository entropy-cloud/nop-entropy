package io.nop.code.core.incremental;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class PathFingerprintStore implements IFingerprintStore {

    private final Path baseDir;
    private final ManifestStore manifestStore = new ManifestStore();

    public PathFingerprintStore(Path baseDir) {
        this.baseDir = baseDir;
    }

    private Path resolveManifestPath(String indexId) {
        return baseDir.resolve(indexId).resolve("manifest.json");
    }

    @Override
    public void saveFingerprints(String indexId, List<FileFingerprint> fingerprints) throws IOException {
        manifestStore.save(resolveManifestPath(indexId), fingerprints);
    }

    @Override
    public List<FileFingerprint> loadFingerprints(String indexId) throws IOException {
        return manifestStore.load(resolveManifestPath(indexId));
    }

    @Override
    public void deleteByPaths(String indexId, List<String> filePaths) throws IOException {
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }
        Set<String> pathSet = new HashSet<>(filePaths);
        Path manifestPath = resolveManifestPath(indexId);
        List<FileFingerprint> fingerprints = manifestStore.load(manifestPath);
        Iterator<FileFingerprint> it = fingerprints.iterator();
        while (it.hasNext()) {
            FileFingerprint fp = it.next();
            if (fp != null && pathSet.contains(fp.getFilePath())) {
                it.remove();
            }
        }
        manifestStore.save(manifestPath, fingerprints);
    }

    @Override
    public void deleteByIndex(String indexId) throws IOException {
        Path manifestPath = resolveManifestPath(indexId);
        Files.deleteIfExists(manifestPath);
    }
}
