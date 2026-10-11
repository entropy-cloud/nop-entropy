package io.nop.find.core.model;

import io.nop.find.core.NopFindException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一次查找命令（{@link io.nop.find.core.coordinator.FindCoordinator} 的输入模型，不可变）。
 *
 * <p>遍历相关配置（threads / respectGitignore / includeHidden）由 {@code FindCoordinator}
 * 构造参数持有，与 {@code SearchCoordinator}/{@code SearchCommand} 的分层一致，
 * 避免同一配置出现两个来源。
 *
 * <p>query 为空、limit 非正一律抛 {@link NopFindException}（快速失败，不静默降级）。
 */
public class FindCommand {
    private final Path root;
    private final String query;
    private final int limit;
    private final boolean caseSensitive;
    private final List<String> globs;

    private FindCommand(Builder builder) {
        this.root = Objects.requireNonNull(builder.root, "root");
        this.query = builder.query;
        if (query == null || query.isEmpty()) {
            throw new NopFindException("find query must not be empty");
        }
        if (builder.limit <= 0) {
            throw new NopFindException("find limit must be positive, got " + builder.limit);
        }
        this.limit = builder.limit;
        this.caseSensitive = builder.caseSensitive;
        this.globs = List.copyOf(builder.globs);
    }

    public static Builder builder(Path root, String query) {
        return new Builder(root, query);
    }

    /**
     * 快捷构造：默认 limit=100、大小写不敏感、无 glob 过滤。
     */
    public static FindCommand of(Path root, String query) {
        return builder(root, query).build();
    }

    public Path getRoot() {
        return root;
    }

    public String getQuery() {
        return query;
    }

    public int getLimit() {
        return limit;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public List<String> getGlobs() {
        return globs;
    }

    public static class Builder {
        private final Path root;
        private final String query;
        private int limit = 100;
        private boolean caseSensitive;
        private List<String> globs = List.of();

        private Builder(Path root, String query) {
            this.root = root;
            this.query = query;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder globs(List<String> globs) {
            this.globs = globs == null ? List.of() : new ArrayList<>(globs);
            return this;
        }

        public Builder glob(String glob) {
            List<String> copy = new ArrayList<>(this.globs);
            copy.add(glob);
            this.globs = copy;
            return this;
        }

        public FindCommand build() {
            return new FindCommand(this);
        }
    }
}