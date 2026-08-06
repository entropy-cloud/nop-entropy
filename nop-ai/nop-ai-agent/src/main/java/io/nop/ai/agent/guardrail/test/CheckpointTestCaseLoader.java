package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.PrincipalRole;
import io.nop.ai.agent.security.SecurityCheckpoint;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads declarative YAML checkpoint corpora into {@link CheckpointTestCase}
 * lists (design {@code guardrail-contract.md} §增量 4). Mirrors the existing
 * content-layer {@code CorpusLoader}: nop's {@link ResourceHelper} +
 * {@link JsonTool#parseYaml} pipeline (same as
 * {@code FileSystemSkillProvider}).
 *
 * <p>YAML format (root is a list of case mappings):
 *
 * <pre>
 * - id: tdl-001
 *   category: tool-deny-list
 *   toolName: bash
 *   args:                       # optional, defaults to empty
 *     command: "rm -rf /"
 *   channelKind: GROUP          # optional: WEBUI | API | DM | GROUP
 *   principalRole: USER         # optional: USER | OPERATOR
 *   workDir: /tmp               # optional
 *   sessionId: s-tdl-001
 *   expectedDecision: DENY      # required: ALLOW | DENY | DENY_AND_BREAK
 *   expectedMatchedRule: hardcoded_deny_list  # optional
 *   description: "deny-listed tool"           # optional
 *   # write-intent-conflict seeding (optional):
 *   prePopConflictPath: /tmp/target.txt
 *   prePopConflictSession: other-session
 * </pre>
 *
 * <p>Malformed entries fail loud (no silent skip), per Minimum Rules #24.
 */
public class CheckpointTestCaseLoader {

    public static final String DEFAULT_CORPUS_DIR = "/nop/ai/agent/checkpoint-test/corpus";

    /**
     * Load all {@code .yaml}/{@code .yml} corpus files under the given
     * virtual-filesystem directory and merge them into a single case list.
     * Duplicate case ids across files are rejected.
     */
    public static List<CheckpointTestCase> loadDirectory(String vfsDirPath) {
        if (StringHelper.isEmpty(vfsDirPath)) {
            throw new NopAiAgentException(
                    "CheckpointTestCaseLoader.loadDirectory: vfsDirPath must not be empty");
        }
        while (vfsDirPath.length() > 1 && vfsDirPath.endsWith("/")) {
            vfsDirPath = vfsDirPath.substring(0, vfsDirPath.length() - 1);
        }
        IResource dir = VirtualFileSystem.instance().getResource(vfsDirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }
        List<IResource> files = new ArrayList<>();
        for (IResource child : VirtualFileSystem.instance().getChildren(dir.getPath())) {
            if (!child.isDirectory()
                    && (child.getName().endsWith(".yaml") || child.getName().endsWith(".yml"))) {
                files.add(child);
            }
        }
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        // Sort by name for deterministic ordering (stable regression baselines).
        files.sort((a, b) -> a.getName().compareTo(b.getName()));
        List<CheckpointTestCase> all = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (IResource file : files) {
            for (CheckpointTestCase tc : load(file)) {
                if (!seenIds.add(tc.getId())) {
                    throw new NopAiAgentException(
                            "CheckpointTestCaseLoader: duplicate case id '" + tc.getId()
                                    + "' (loaded from " + file.getPath() + ")");
                }
                all.add(tc);
            }
        }
        return all;
    }

    /**
     * Load a single YAML corpus file into a case list.
     */
    @SuppressWarnings("unchecked")
    public static List<CheckpointTestCase> load(IResource resource) {
        if (resource == null) {
            throw new NopAiAgentException("CheckpointTestCaseLoader.load: resource must not be null");
        }
        String text;
        try {
            text = ResourceHelper.readText(resource, null);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "CheckpointTestCaseLoader: failed to read corpus file: " + resource.getPath(), e);
        }

        Object parsed;
        try {
            parsed = JsonTool.parseYaml(null, text);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "CheckpointTestCaseLoader: malformed YAML in corpus file: " + resource.getPath(), e);
        }

        if (parsed == null) {
            throw new NopAiAgentException(
                    "CheckpointTestCaseLoader: corpus file is empty: " + resource.getPath());
        }
        if (!(parsed instanceof List)) {
            throw new NopAiAgentException(
                    "CheckpointTestCaseLoader: corpus root must be a YAML list, got "
                            + parsed.getClass().getName() + ": " + resource.getPath());
        }

        List<?> rawList = (List<?>) parsed;
        List<CheckpointTestCase> cases = new ArrayList<>(rawList.size());
        int index = 0;
        for (Object item : rawList) {
            cases.add(parseCase((Map<String, Object>) item, resource.getPath(), index));
            index++;
        }
        return cases;
    }

    private static CheckpointTestCase parseCase(Map<String, Object> map, String source, int index) {
        if (map == null) {
            throw new NopAiAgentException(
                    "CheckpointTestCaseLoader: corpus entry #" + index + " is null in " + source);
        }
        String id = readString(map, "id", source, index, true);
        String category = readString(map, "category", source, index, true);
        String toolName = readString(map, "toolName", source, index, true);
        String sessionId = readString(map, "sessionId", source, index, true);
        String workDir = readString(map, "workDir", source, index, false);

        Map<String, Object> args = Collections.emptyMap();
        Object argsRaw = map.get("args");
        if (argsRaw != null) {
            if (!(argsRaw instanceof Map)) {
                throw new NopAiAgentException(
                        "CheckpointTestCaseLoader: 'args' must be a mapping in case #" + index
                                + " (" + source + ")");
            }
            args = new LinkedHashMap<>((Map<String, Object>) argsRaw);
        }

        ChannelKind channelKind = null;
        String channelStr = readString(map, "channelKind", source, index, false);
        if (channelStr != null) {
            channelKind = parseEnum(channelStr, ChannelKind.class, "channelKind", source, index);
        }

        Principal principal = null;
        String roleStr = readString(map, "principalRole", source, index, false);
        if (roleStr != null) {
            PrincipalRole role = parseEnum(roleStr, PrincipalRole.class, "principalRole", source, index);
            principal = role == PrincipalRole.OPERATOR ? Principal.operator() : Principal.user();
        }

        String decisionStr = readString(map, "expectedDecision", source, index, true);
        SecurityCheckpoint.Decision expectedDecision =
                parseEnum(decisionStr, SecurityCheckpoint.Decision.class, "expectedDecision", source, index);

        String expectedMatchedRule = readString(map, "expectedMatchedRule", source, index, false);
        String description = readString(map, "description", source, index, false);

        String prePopConflictPath = readString(map, "prePopConflictPath", source, index, false);
        String prePopConflictSession = readString(map, "prePopConflictSession", source, index, false);

        return new CheckpointTestCase(id, category, toolName, args, channelKind, principal,
                workDir, sessionId, expectedDecision, expectedMatchedRule, description,
                prePopConflictPath, prePopConflictSession);
    }

    private static String readString(Map<String, Object> map, String key, String source, int index,
                                     boolean required) {
        Object v = map.get(key);
        String s = v == null ? null : v.toString().trim();
        if (required && StringHelper.isEmpty(s)) {
            throw new NopAiAgentException(
                    "CheckpointTestCaseLoader: missing required field '" + key + "' in case #" + index
                            + " (" + source + ")");
        }
        return StringHelper.isEmpty(s) ? null : s;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> enumType,
                                                   String fieldName, String source, int index) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "CheckpointTestCaseLoader: invalid " + fieldName + " '" + value + "' in case #" + index
                            + " (" + source + "); expected one of " + EnumSetNames.of(enumType));
        }
    }

    // Small helper to render enum constant names in error messages without
    // pulling java.util.EnumSet reflection boilerplate into each call site.
    private static final class EnumSetNames {
        static <E extends Enum<E>> String of(Class<E> enumType) {
            E[] constants = enumType.getEnumConstants();
            List<String> names = new ArrayList<>(constants.length);
            for (E c : constants) {
                names.add(c.name());
            }
            return names.toString();
        }
    }
}
