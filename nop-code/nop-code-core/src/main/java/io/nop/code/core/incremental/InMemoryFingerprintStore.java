package io.nop.code.core.incremental;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryFingerprintStore implements IFingerprintStore {

    private final ConcurrentHashMap<String, Map<String, FileFingerprint>> store = new ConcurrentHashMap<>();

    @Override
    public void saveFingerprints(String indexId, List<FileFingerprint> fingerprints) throws IOException {
        Map<String, FileFingerprint> map = new ConcurrentHashMap<>();
        if (fingerprints != null) {
            for (FileFingerprint fp : fingerprints) {
                if (fp != null && fp.getFilePath() != null) {
                    map.put(fp.getFilePath(), fp);
                }
            }
        }
        store.put(indexId, map);
    }

    @Override
    public List<FileFingerprint> loadFingerprints(String indexId) throws IOException {
        Map<String, FileFingerprint> map = store.get(indexId);
        if (map == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(map.values());
    }

    @Override
    public void deleteByPaths(String indexId, List<String> filePaths) throws IOException {
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }
        Map<String, FileFingerprint> map = store.get(indexId);
        if (map == null) {
            return;
        }
        for (String path : filePaths) {
            map.remove(path);
        }
    }

    @Override
    public void deleteByIndex(String indexId) throws IOException {
        store.remove(indexId);
    }
}
