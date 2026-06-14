package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * File-system-backed {@link ISkillProvider} that loads {@code *.skill.yaml}
 * files from a configured base directory via Nop's {@link VirtualFileSystem}
 * resource scanning (design {@code skill-system-design.md} §8.1: "文件系统扫描
 * {@code nop-ai-agent/skills/*.skill.yaml}").
 *
 * <p>Each file is parsed into a {@link SkillModel}. The loaded set is cached on
 * first access — skills do not change at runtime per design §7.3 (no dynamic
 * registration).
 *
 * <p>Behaviour:
 * <ul>
 *   <li><b>Missing directory</b> → returns an empty set (zero-config default,
 *       not an error).</li>
 *   <li><b>Malformed YAML</b> → fails fast with a clear
 *       {@link NopAiAgentException} naming the offending file.</li>
 *   <li><b>Duplicate skill name across files</b> → fails fast with a clear
 *       {@link NopAiAgentException} naming both files and the duplicated name.</li>
 * </ul>
 *
 * <p>The YAML format follows design §4.1. The root may be either a flat map of
 * fields or a {@code skill:} wrapper; both are accepted. The phase-1
 * Scheduling-layer fields are read; unknown fields are ignored.
 */
public class FileSystemSkillProvider implements ISkillProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemSkillProvider.class);

    public static final String SKILL_FILE_SUFFIX = ".skill.yaml";

    private final String baseDir;
    private volatile Collection<SkillModel> cached;

    /**
     * @param baseDir VFS path of the directory to scan, e.g.
     *                 {@code "/nop/ai/skills"}. May be {@code null} or point to
     *                 a non-existent directory — both yield an empty skill set.
     */
    public FileSystemSkillProvider(String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public Collection<SkillModel> getSkills() {
        Collection<SkillModel> snapshot = cached;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (cached == null) {
                cached = Collections.unmodifiableCollection(load());
            }
            return cached;
        }
    }

    private Collection<SkillModel> load() {
        if (StringHelper.isEmpty(baseDir)) {
            return Collections.emptyList();
        }

        IResource dir = VirtualFileSystem.instance().getResource(baseDir);
        if (!dir.exists() || !dir.isDirectory()) {
            LOG.debug("Skill directory not found or not a directory, returning empty skill set: baseDir={}", baseDir);
            return Collections.emptyList();
        }

        List<IResource> files = new ArrayList<>();
        for (IResource child : VirtualFileSystem.instance().getChildren(dir.getPath())) {
            if (!child.isDirectory() && child.getName().endsWith(SKILL_FILE_SUFFIX)) {
                files.add(child);
            }
        }

        if (files.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, SkillModel> byName = new LinkedHashMap<>();
        for (IResource file : files) {
            SkillModel skill = parseSkill(file);
            String name = skill.getName();
            if (StringHelper.isEmpty(name)) {
                throw new NopAiAgentException(
                        "Skill file has no name field: file=" + file.getPath());
            }
            SkillModel existing = byName.get(name);
            if (existing != null) {
                throw new NopAiAgentException(
                        "Duplicate skill name '" + name + "' found in multiple files: "
                                + file.getPath() + " conflicts with a previously loaded skill");
            }
            byName.put(name, skill);
        }

        LOG.info("Loaded {} skill(s) from {}: {}", byName.size(), baseDir, byName.keySet());
        return new ArrayList<>(byName.values());
    }

    @SuppressWarnings("unchecked")
    private SkillModel parseSkill(IResource file) {
        String text;
        try {
            text = ResourceHelper.readText(file, null);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "Failed to read skill file: file=" + file.getPath(), e);
        }

        Object parsed;
        try {
            parsed = JsonTool.parseYaml(null, text);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "Malformed YAML in skill file, parsing failed: file=" + file.getPath(), e);
        }

        if (parsed == null) {
            throw new NopAiAgentException(
                    "Skill file is empty: file=" + file.getPath());
        }

        if (!(parsed instanceof Map)) {
            throw new NopAiAgentException(
                    "Skill file root must be a mapping, got " + parsed.getClass().getName()
                            + ": file=" + file.getPath());
        }

        Map<String, Object> root = (Map<String, Object>) parsed;
        Map<String, Object> fields = root;

        Object skillWrapper = root.get("skill");
        if (skillWrapper instanceof Map) {
            fields = (Map<String, Object>) skillWrapper;
        }

        return toSkillModel(file.getPath(), fields);
    }

    @SuppressWarnings("unchecked")
    private SkillModel toSkillModel(String filePath, Map<String, Object> fields) {
        SkillModel skill = new SkillModel();

        Object name = fields.get("name");
        if (name == null) {
            throw new NopAiAgentException(
                    "Skill file missing required 'name' field: file=" + filePath);
        }
        skill.setName(name.toString());

        Object goal = fields.get("goal");
        if (goal != null) {
            skill.setGoal(goal.toString());
        }

        Object intentSignature = fields.get("intentSignature");
        if (intentSignature != null) {
            skill.setIntentSignature(toStringList(intentSignature, filePath, "intentSignature"));
        }

        Object topPattern = fields.get("topPattern");
        if (topPattern != null) {
            skill.setTopPattern(parseTopPattern(topPattern.toString(), filePath));
        }

        Object dependencies = fields.get("dependencies");
        if (dependencies != null) {
            skill.setDependencies(toStringList(dependencies, filePath, "dependencies"));
        }

        Object tags = fields.get("tags");
        if (tags != null) {
            skill.setTags(toStringSet(tags));
        }

        Object resourceScope = fields.get("resourceScope");
        if (resourceScope != null) {
            skill.setResourceScope(toResourceScopeSet(resourceScope, filePath));
        }

        return skill;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value, String filePath, String fieldName) {
        if (value instanceof Collection) {
            List<String> list = new ArrayList<>();
            for (Object item : (Collection<Object>) value) {
                if (item != null) {
                    list.add(item.toString());
                }
            }
            return list;
        }
        if (value instanceof String) {
            return Collections.singletonList((String) value);
        }
        throw new NopAiAgentException(
                "Skill field '" + fieldName + "' must be a string or list of strings, got "
                        + value.getClass().getName() + ": file=" + filePath);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> toStringSet(Object value) {
        if (value instanceof Collection) {
            Set<String> set = new LinkedHashSet<>();
            for (Object item : (Collection<Object>) value) {
                if (item != null) {
                    set.add(item.toString());
                }
            }
            return set;
        }
        if (value instanceof String) {
            // csv-set per design §4.1 (tags: csv-set)
            Set<String> set = new LinkedHashSet<>();
            for (String part : ((String) value).split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed);
                }
            }
            return set;
        }
        return new LinkedHashSet<>();
    }

    @SuppressWarnings("unchecked")
    private static Set<SkillResourceScope> toResourceScopeSet(Object value, String filePath) {
        Set<SkillResourceScope> set = new LinkedHashSet<>();
        Collection<Object> items;
        if (value instanceof Collection) {
            items = (Collection<Object>) value;
        } else if (value instanceof String) {
            List<Object> list = new ArrayList<>();
            for (String part : ((String) value).split(",")) {
                list.add(part.trim());
            }
            items = list;
        } else {
            return set;
        }
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            set.add(parseResourceScope(item.toString(), filePath));
        }
        return set;
    }

    private static SkillTopPattern parseTopPattern(String value, String filePath) {
        try {
            return SkillTopPattern.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "Invalid topPattern value '" + value + "' in skill file: file=" + filePath);
        }
    }

    private static SkillResourceScope parseResourceScope(String value, String filePath) {
        try {
            return SkillResourceScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "Invalid resourceScope value '" + value + "' in skill file: file=" + filePath);
        }
    }
}
