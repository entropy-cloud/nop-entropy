package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.tools.sequential_thinking.model.ThoughtData;
import io.nop.ai.tools.sequential_thinking.model.ThoughtSession;
import io.nop.ai.tools.sequential_thinking.model.ThoughtStage;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.json.JsonTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ThoughtStorage {
    private final Lock lock = new ReentrantLock();
    private final File storageDir;

    public ThoughtStorage(String storageDirPath) {
        if (storageDirPath == null || storageDirPath.isEmpty()) {
            this.storageDir = new File(System.getProperty("user.home"), ".mcp_sequential_thinking");
        } else {
            this.storageDir = FileHelper.resolveFile(storageDirPath);
        }
    }

    private File getSessionFile(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        return new File(storageDir, sessionId + ".json");
    }

    private List<ThoughtData> loadSession(String sessionId) {
        File sessionFile = getSessionFile(sessionId);

        if (!sessionFile.exists()) {
            return new ArrayList<>();
        }

        String json = FileHelper.readText(sessionFile, null);
        ThoughtSession session = fromJson(json, ThoughtSession.class);
        return session.getThoughts();
    }

    private void saveSession(String sessionId, List<ThoughtData> thoughts) {
        File sessionFile = getSessionFile(sessionId);
        ThoughtSession session = new ThoughtSession(thoughts);
        String json = toJson(session);
        FileHelper.writeText(sessionFile, json, null);
    }

    public void addThought(String sessionId, ThoughtData thought) {
        Objects.requireNonNull(thought, "thought cannot be null");

        lock.lock();
        try {
            List<ThoughtData> thoughts = loadSession(sessionId);
            thoughts.add(thought);
            saveSession(sessionId, thoughts);
        } finally {
            lock.unlock();
        }
    }

    public List<ThoughtData> getAllThoughts(String sessionId) {
        lock.lock();
        try {
            return new ArrayList<>(loadSession(sessionId));
        } finally {
            lock.unlock();
        }
    }

    public List<ThoughtData> getThoughtsByStage(String sessionId, ThoughtStage stage) {
        Objects.requireNonNull(stage, "stage cannot be null");

        lock.lock();
        try {
            return loadSession(sessionId).stream()
                    .filter(t -> t.getStage() == stage)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    public void clearHistory(String sessionId) {
        lock.lock();
        try {
            saveSession(sessionId, new ArrayList<>());
        } finally {
            lock.unlock();
        }
    }

    public void exportSession(String sessionId, String filePath) {
        Objects.requireNonNull(filePath, "filePath cannot be null");

        lock.lock();
        try {
            List<ThoughtData> thoughts = loadSession(sessionId);
            ThoughtSession session = new ThoughtSession(thoughts);
            String json = toJson(session);
            FileHelper.writeText(new File(filePath), json, null);
        } finally {
            lock.unlock();
        }
    }

    public void importSession(String sessionId, String filePath) {
        Objects.requireNonNull(filePath, "filePath cannot be null");

        lock.lock();
        try {
            String json = FileHelper.readText(new File(filePath), null);
            ThoughtSession session = fromJson(json, ThoughtSession.class);
            saveSession(sessionId, session.getThoughts());
        } finally {
            lock.unlock();
        }
    }

    private String toJson(Object obj) {
        return JsonTool.stringify(obj);
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        return JsonTool.parseBeanFromText(json, clazz);
    }
}