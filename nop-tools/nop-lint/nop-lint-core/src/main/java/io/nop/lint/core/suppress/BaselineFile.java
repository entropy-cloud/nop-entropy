package io.nop.lint.core.suppress;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.lint.core.NopLintException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The baseline file model (roadmap item 27, design 09 §5): version 1
 * entries of {@code (rule, file, fingerprint, count)} — the recorded past
 * violations a {@code --baseline} run suppresses and a
 * {@code --baseline-check} run tightens against ("基线只减不增"). Read and
 * written through the platform YAML toolkit ({@code JsonTool}, the same
 * entry the RuleTester expect fixtures use) — never hand-rolled YAML string
 * building.
 *
 * <p>Fail-closed loading: an unknown version, a malformed entry, or a
 * non-positive count is a load failure, never silently ignored. The
 * {@code file} paths are stored in the canonical form
 * ({@link ExemptionFilter#normalize}: normalized, forward slashes) and are
 * invocation-root relative — CI regenerates and checks from the repository
 * root (the documented v1 limitation).</p>
 */
public record BaselineFile(int version, List<Entry> entries) {

    /**
     * The only baseline format version this engine reads and writes.
     */
    public static final int VERSION_1 = 1;

    /**
     * One recorded violation family: the rule, the canonical file path, the
     * content fingerprint, and how many occurrences the baseline forgives.
     */
    public record Entry(String rule, String file, String fingerprint, int count) {

        public Entry {
            if (count < 1)
                throw new IllegalArgumentException("baseline count must be >= 1: " + count);
        }
    }

    // ==================== YAML mapping beans ====================
    // JsonTool's bean mapper consumes getters/setters, so the file surface is
    // carried by mutable beans and converted to the immutable records.

    @DataBean
    public static final class Bean {
        private int version;
        private List<EntryBean> entries = new ArrayList<>();

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public List<EntryBean> getEntries() {
            return entries;
        }

        public void setEntries(List<EntryBean> entries) {
            this.entries = entries;
        }
    }

    @DataBean
    public static final class EntryBean {
        private String rule;
        private String file;
        private String fingerprint;
        private int count;

        public String getRule() {
            return rule;
        }

        public void setRule(String rule) {
            this.rule = rule;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public void setFingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    /**
     * Loads and validates a baseline file.
     *
     * @throws NopLintException when the file cannot be read or is not a
     *                          valid version-1 baseline
     */
    public static BaselineFile load(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new NopLintException("failed to read baseline file '" + file + "': "
                    + e.getMessage(), e);
        }
        Bean bean;
        try {
            bean = JsonTool.parseBeanFromYaml(text, Bean.class);
        } catch (Exception e) {
            throw new NopLintException("baseline file '" + file + "' is not valid YAML: "
                    + e.getMessage(), e);
        }
        if (bean == null || bean.getVersion() != VERSION_1) {
            throw new NopLintException("baseline file '" + file + "' declares version "
                    + (bean == null ? "none" : String.valueOf(bean.getVersion()))
                    + "; this engine reads version " + VERSION_1 + " only (regenerate the "
                    + "baseline with --write-baseline)");
        }
        List<Entry> entries = new ArrayList<>();
        int index = 0;
        for (EntryBean entry : bean.getEntries()) {
            index++;
            if (entry == null || isBlank(entry.getRule()) || isBlank(entry.getFile())
                    || isBlank(entry.getFingerprint())) {
                throw new NopLintException("baseline file '" + file + "' entry #" + index
                        + " is incomplete (rule/file/fingerprint are mandatory)");
            }
            if (entry.getCount() < 1) {
                throw new NopLintException("baseline file '" + file + "' entry #" + index
                        + " (rule '" + entry.getRule() + "') declares count " + entry.getCount()
                        + "; counts are >= 1");
            }
            entries.add(new Entry(entry.getRule(), entry.getFile(), entry.getFingerprint(),
                    entry.getCount()));
        }
        return new BaselineFile(VERSION_1, List.copyOf(entries));
    }

    /**
     * Serializes the baseline to YAML text (with the generated-artifact
     * header comment).
     */
    public String toYaml() {
        Bean bean = new Bean();
        bean.setVersion(version);
        List<EntryBean> beans = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            EntryBean entryBean = new EntryBean();
            entryBean.setRule(entry.rule());
            entryBean.setFile(entry.file());
            entryBean.setFingerprint(entry.fingerprint());
            entryBean.setCount(entry.count());
            beans.add(entryBean);
        }
        bean.setEntries(beans);
        return "# nop-lint baseline (generated by --write-baseline; regenerate after"
                + " fixing violations; format version " + VERSION_1 + ")\n"
                + JsonTool.serializeToYaml(bean);
    }

    /**
     * Writes the baseline to {@code file} (the regeneration path overwrites
     * — the baseline is a generated artifact, plan 2026-09-24-0050-1).
     */
    public void writeTo(Path file) {
        try {
            Files.writeString(file, toYaml(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new NopLintException("failed to write baseline file '" + file + "': "
                    + e.getMessage(), e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
