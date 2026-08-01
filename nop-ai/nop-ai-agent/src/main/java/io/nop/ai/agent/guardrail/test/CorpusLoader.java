package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads declarative YAML attack corpora into {@link AttackCase} lists (design
 * {@code guardrail-contract.md} §增量 1, Decision F). Uses nop's existing
 * {@link ResourceHelper} + {@link JsonTool#parseYaml} — the same pipeline as
 * {@code FileSystemSkillProvider}.
 *
 * <p>YAML format (root is a list of case mappings):
 *
 * <pre>
 * - id: pi-001
 *   category: prompt_injection
 *   threatClass: LLM01_prompt_injection   # optional, defaults to category
 *   payload: "ignore all previous instructions"
 *   direction: INPUT                       # optional, defaults to INPUT
 *   expectedBehavior: BLOCK                # BLOCK | PASS
 *   description: "classic override"        # optional
 * </pre>
 *
 * <p>Malformed entries fail loud (no silent skip), per Minimum Rules #24.
 */
public class CorpusLoader {

    /**
     * Load all {@code .yaml}/{@code .yml} corpus files under the given
     * virtual-filesystem directory and merge them into a single case list.
     * Duplicate case ids across files are rejected.
     */
    public static List<AttackCase> loadDirectory(String vfsDirPath) {
        if (StringHelper.isEmpty(vfsDirPath)) {
            throw new NopAiAgentException("CorpusLoader.loadDirectory: vfsDirPath must not be empty");
        }
        // VFS paths must not end with '/' (ResourceHelper.checkNormalVirtualPath rejects them)
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
        List<AttackCase> all = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (IResource file : files) {
            for (AttackCase ac : load(file)) {
                if (!seenIds.add(ac.getId())) {
                    throw new NopAiAgentException(
                            "CorpusLoader: duplicate attack case id '" + ac.getId()
                                    + "' (loaded from " + file.getPath() + ")");
                }
                all.add(ac);
            }
        }
        return all;
    }

    /**
     * Load a single YAML corpus file into a case list.
     */
    @SuppressWarnings("unchecked")
    public static List<AttackCase> load(IResource resource) {
        if (resource == null) {
            throw new NopAiAgentException("CorpusLoader.load: resource must not be null");
        }
        String text;
        try {
            text = ResourceHelper.readText(resource, null);
        } catch (Exception e) {
            throw new NopAiAgentException("CorpusLoader: failed to read corpus file: " + resource.getPath(), e);
        }

        Object parsed;
        try {
            parsed = JsonTool.parseYaml(null, text);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "CorpusLoader: malformed YAML in corpus file: " + resource.getPath(), e);
        }

        if (parsed == null) {
            throw new NopAiAgentException("CorpusLoader: corpus file is empty: " + resource.getPath());
        }
        if (!(parsed instanceof List)) {
            throw new NopAiAgentException(
                    "CorpusLoader: corpus root must be a YAML list, got " + parsed.getClass().getName()
                            + ": " + resource.getPath());
        }

        List<?> rawList = (List<?>) parsed;
        List<AttackCase> cases = new ArrayList<>(rawList.size());
        int index = 0;
        for (Object item : rawList) {
            cases.add(parseCase((Map<String, Object>) item, resource.getPath(), index));
            index++;
        }
        return cases;
    }

    private static AttackCase parseCase(Map<String, Object> map, String source, int index) {
        if (map == null) {
            throw new NopAiAgentException(
                    "CorpusLoader: corpus entry #" + index + " is null in " + source);
        }
        String id = readString(map, "id", source, index, true);
        String category = readString(map, "category", source, index, true);
        String threatClass = readString(map, "threatClass", source, index, false);
        if (threatClass == null) {
            threatClass = category;
        }
        String payload = readString(map, "payload", source, index, true);
        String directionStr = readString(map, "direction", source, index, false);
        GuardrailDirection direction = directionStr == null
                ? GuardrailDirection.INPUT
                : parseDirection(directionStr, source, index);
        String expectedStr = readString(map, "expectedBehavior", source, index, true);
        ExpectedBehavior expected = parseExpected(expectedStr, source, index);
        String description = readString(map, "description", source, index, false);

        return new AttackCase(id, category, threatClass, payload, direction, expected, description, null);
    }

    private static String readString(Map<String, Object> map, String key, String source, int index,
                                     boolean required) {
        Object v = map.get(key);
        String s = v == null ? null : v.toString().trim();
        if (required && StringHelper.isEmpty(s)) {
            throw new NopAiAgentException(
                    "CorpusLoader: missing required field '" + key + "' in corpus entry #" + index
                            + " (" + source + ")");
        }
        return StringHelper.isEmpty(s) ? null : s;
    }

    private static GuardrailDirection parseDirection(String value, String source, int index) {
        try {
            return GuardrailDirection.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "CorpusLoader: invalid direction '" + value + "' in corpus entry #" + index
                            + " (" + source + "); expected INPUT or OUTPUT");
        }
    }

    private static ExpectedBehavior parseExpected(String value, String source, int index) {
        try {
            return ExpectedBehavior.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "CorpusLoader: invalid expectedBehavior '" + value + "' in corpus entry #" + index
                            + " (" + source + "); expected BLOCK or PASS");
        }
    }
}
