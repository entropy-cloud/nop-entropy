package io.nop.git.cli;

import io.nop.git.api.ChangeType;
import io.nop.git.api.NopGitCommit;
import io.nop.git.api.NopGitDiffEntry;
import io.nop.git.api.NopGitRef;
import io.nop.git.api.NopGitRename;
import io.nop.git.api.NopGitStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Formats facade results as native git pure-text output (design
 * 02-cli-compat-design): no ANSI colors, byte-aligned separators, porcelain
 * v1, unified diff text, diffstat with the native 80-column scaling.
 */
public final class GitOutputFormatter {

    private GitOutputFormatter() {
    }

    // ------------------------------------------------------------------
    // status
    // ------------------------------------------------------------------

    /**
     * porcelain v1: two status letters + space + path; renames render as
     * {@code R  old -> new}; untracked directories are already collapsed in
     * {@link NopGitStatus}.
     */
    public static List<String> statusPorcelain(NopGitStatus status) {
        List<String> lines = new ArrayList<>();
        // per-path XY codes: X = index vs HEAD, Y = index vs worktree
        var paths = new java.util.TreeMap<String, char[]>();

        for (NopGitRename rename : status.stagedRenames()) {
            lines.add("R  " + rename.oldPath() + " -> " + rename.newPath());
        }
        for (String path : status.stagedAdded())
            paths.put(path, setX(paths.get(path), 'A'));
        for (String path : status.stagedChanged())
            paths.put(path, setX(paths.get(path), 'M'));
        for (String path : status.stagedRemoved())
            paths.put(path, setX(paths.get(path), 'D'));
        for (String path : status.modified())
            paths.put(path, setY(paths.get(path), 'M'));
        for (String path : status.deleted())
            paths.put(path, setY(paths.get(path), 'D'));
        for (var entry : paths.entrySet()) {
            char[] xy = entry.getValue();
            lines.add("" + (xy[0] == 0 ? ' ' : xy[0]) + (xy[1] == 0 ? ' ' : xy[1])
                    + " " + entry.getKey());
        }
        for (String path : status.untracked())
            lines.add("?? " + path);
        return lines;
    }

    private static char[] setX(char[] xy, char value) {
        char[] slot = xy == null ? new char[2] : xy;
        slot[0] = value;
        return slot;
    }

    private static char[] setY(char[] xy, char value) {
        char[] slot = xy == null ? new char[2] : xy;
        slot[1] = value;
        return slot;
    }

    /**
     * Long {@code git status} text (also consumed by the nothing-to-commit
     * branch of {@code git commit}); mirrors native section order, hint lines
     * and trailing summary.
     */
    public static List<String> statusLong(NopGitStatus status, boolean unborn) {
        List<String> lines = new ArrayList<>();

        lines.add("On branch " + statusCurrentBranch(status));
        if (unborn) {
            lines.add("");
            lines.add("No commits yet");
        }

        // sections: each holds [header, hint..., entries]; native sorts the
        // entries of a section by path, not by change type
        List<List<String>> sections = new ArrayList<>();

        boolean hasStaged = !status.stagedAdded().isEmpty() || !status.stagedChanged().isEmpty()
                || !status.stagedRemoved().isEmpty() || !status.stagedRenames().isEmpty();
        boolean hasUnstaged = !status.modified().isEmpty() || !status.deleted().isEmpty();
        boolean hasUntracked = !status.untracked().isEmpty();

        if (hasStaged) {
            List<String> section = new ArrayList<>();
            section.add("Changes to be committed:");
            section.add("  (use \"git restore --staged <file>...\" to unstage)");
            List<String[]> rows = new ArrayList<>();
            for (String path : status.stagedAdded())
                rows.add(new String[]{"new file", path});
            for (String path : status.stagedChanged())
                rows.add(new String[]{"modified", path});
            for (String path : status.stagedRemoved())
                rows.add(new String[]{"deleted", path});
            for (var rename : status.stagedRenames())
                rows.add(new String[]{"renamed", rename.oldPath() + " -> " + rename.newPath()});
            rows.sort(java.util.Comparator.comparing(r -> r[1]));
            for (String[] row : rows)
                section.add("\t" + sectionLine(row[0], row[1]));
            sections.add(section);
        }

        if (hasUnstaged) {
            List<String> section = new ArrayList<>();
            section.add("Changes not staged for commit:");
            section.add("  (use \"git add <file>...\" to update what will be committed)");
            section.add("  (use \"git restore <file>...\" to discard changes in working directory)");
            List<String[]> rows = new ArrayList<>();
            for (String path : status.modified())
                rows.add(new String[]{"modified", path});
            for (String path : status.deleted())
                rows.add(new String[]{"deleted", path});
            rows.sort(java.util.Comparator.comparing(r -> r[1]));
            for (String[] row : rows)
                section.add("\t" + sectionLine(row[0], row[1]));
            sections.add(section);
        }

        if (hasUntracked) {
            List<String> section = new ArrayList<>();
            section.add("Untracked files:");
            section.add("  (use \"git add <file>...\" to include in what will be committed)");
            for (String path : status.untracked())
                section.add("\t" + path);
            sections.add(section);
        }

        // native: no blank between header and the first section, blank
        // between sections, trailing blank when there is no summary line
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0)
                lines.add("");
            lines.addAll(sections.get(i));
        }

        if (!hasStaged) {
            String summary;
            if (unborn) {
                summary = "nothing to commit (create/copy files and use \"git add\" to track)";
            } else if (hasUnstaged) {
                summary = "no changes added to commit (use \"git add\" and/or \"git commit -a\")";
            } else if (hasUntracked) {
                summary = "nothing added to commit but untracked files present"
                        + " (use \"git add\" to track)";
            } else {
                summary = "nothing to commit, working tree clean";
            }
            // born + clean: summary directly follows the header, no blanks
            if (!unborn && !hasUntracked && !hasUnstaged) {
                lines.add(summary);
            } else {
                lines.add("");
                lines.add(summary);
            }
        } else if (!sections.isEmpty()) {
            lines.add(""); // trailing blank after the last section
        }
        return lines;
    }

    private static String statusCurrentBranch(NopGitStatus status) {
        return status.branch() != null ? status.branch() : "HEAD";
    }

    private static String sectionLine(String label, String path) {
        // git pads "label:" to a fixed 12-column: "new file:   x", "deleted:    x"
        return String.format("%-12s%s", label + ":", path);
    }

    // ------------------------------------------------------------------
    // diff --stat
    // ------------------------------------------------------------------

    /**
     * Native {@code diff --stat} at the non-tty default width of 80 columns:
     * left-aligned path column, change-count column (minimum width 3), plus
     * sign graph scaled to the largest change, then the summary line with
     * native singular/plural wording.
     */
    public static List<String> diffStat(List<NopGitDiffEntry> entries) {
        List<String> lines = new ArrayList<>();
        if (entries.isEmpty())
            return lines;

        // native git (non-tty width 80): name column capped at 50, count
        // column width = digits of the largest change, graph budget =
        // 80 - name - number - 6; scaling only kicks in when the largest
        // change exceeds the budget
        int width = 80;
        int maxChange = 0;
        int maxNameLen = 0;
        for (NopGitDiffEntry entry : entries) {
            maxChange = Math.max(maxChange, entry.insertions() + entry.deletions());
            maxNameLen = Math.max(maxNameLen, displayName(entry).length());
        }
        int nameWidth = Math.min(maxNameLen, 50);
        int numberWidth = String.valueOf(maxChange).length();
        int graphWidth = width - nameWidth - numberWidth - 6;

        int totalInsertions = 0;
        int totalDeletions = 0;
        for (NopGitDiffEntry entry : entries) {
            String name = displayName(entry);
            String shown;
            if (name.length() > nameWidth) {
                shown = "..." + name.substring(name.length() - (nameWidth - 3));
            } else {
                shown = name;
            }
            String pad = " ".repeat(nameWidth - shown.length());
            if (entry.binary()) {
                lines.add(" " + shown + pad + " | Bin");
                continue;
            }
            int insertions = entry.insertions();
            int deletions = entry.deletions();
            totalInsertions += insertions;
            totalDeletions += deletions;
            String graph;
            if (maxChange > graphWidth) {
                int add = ceilScale(insertions, graphWidth, maxChange);
                int del = ceilScale(deletions, graphWidth, maxChange);
                if (add + del > graphWidth) {
                    // keep at least one symbol per nonzero side
                    if (insertions == 0) {
                        del = graphWidth;
                    } else if (deletions == 0) {
                        add = graphWidth;
                    } else {
                        add = graphWidth - del;
                        if (add < 1)
                            add = 1;
                        del = graphWidth - add;
                    }
                }
                graph = "+".repeat(add) + "-".repeat(del);
            } else {
                graph = "+".repeat(insertions) + "-".repeat(deletions);
            }
            String countStr = String.valueOf(insertions + deletions);
            String countPad = " ".repeat(numberWidth - countStr.length());
            String tail = graph.isEmpty() ? "" : " " + graph;
            lines.add(" " + shown + pad + " | " + countPad + countStr + tail);
        }

        StringBuilder summary = new StringBuilder();
        summary.append(' '); // native summary lines start with a leading space
        int files = entries.size();
        summary.append(files).append(files == 1 ? " file" : " files").append(" changed, ");
        boolean first = true;
        boolean showInsertions = totalInsertions > 0 || totalDeletions == 0;
        boolean showDeletions = totalDeletions > 0 || totalInsertions == 0;
        if (showInsertions) {
            summary.append(totalInsertions)
                    .append(totalInsertions == 1 ? " insertion(+)" : " insertions(+)");
            first = false;
        }
        if (showDeletions) {
            if (!first)
                summary.append(", ");
            summary.append(totalDeletions)
                    .append(totalDeletions == 1 ? " deletion(-)" : " deletions(-)");
        }
        lines.add(summary.toString());
        return lines;
    }

    private static String displayName(NopGitDiffEntry entry) {
        return entry.changeType() == ChangeType.RENAMED
                ? entry.oldPath() + " => " + entry.newPath()
                : entry.newPath();
    }

    private static int ceilScale(int value, int width, int maxChange) {
        if (value == 0 || maxChange == 0)
            return 0;
        return (int) Math.ceil((double) value * width / maxChange);
    }

    // ------------------------------------------------------------------
    // log
    // ------------------------------------------------------------------

    public static List<String> logOneline(List<NopGitCommit> commits) {
        List<String> lines = new ArrayList<>();
        for (NopGitCommit commit : commits) {
            lines.add(commit.abbrevId() + " " + commit.shortMessage());
        }
        return lines;
    }

    /**
     * Long {@code git log} entry: commit/Author/Date header + 4-space indented
     * message lines, separated by a blank line before each following commit.
     */
    public static List<String> logLong(List<NopGitCommit> commits) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < commits.size(); i++) {
            NopGitCommit commit = commits.get(i);
            if (i > 0)
                lines.add("");
            lines.add("commit " + commit.id());
            lines.add("Author: " + commit.authorName() + " <" + commit.authorEmail() + ">");
            lines.add("Date:   " + gitDate(commit.commitTime()));
            lines.add("");
            for (String messageLine : commit.fullMessage().split("\n", -1)) {
                if (messageLine.isEmpty()) {
                    lines.add("");
                } else {
                    lines.add("    " + messageLine);
                }
            }
        }
        return lines;
    }

    private static String gitDate(OffsetDateTime time) {
        // native default date: "Thu Nov 20 23:00:00 2025 +0800", rendered in
        // the commit's own offset; single-digit days are space-padded
        return String.format(java.util.Locale.ROOT, "%ta %tb %s %tT %tY %tz",
                time, time, String.format("%2d", time.getDayOfMonth()), time, time, time);
    }

    // ------------------------------------------------------------------
    // branch
    // ------------------------------------------------------------------

    /**
     * {@code git branch} list; current branch is prefixed {@code "* "};
     * remote refs render as {@code remotes/origin/x} with the symbolic
     * {@code origin/HEAD -> ...} arrow.
     */
    public static List<String> branchList(List<NopGitRef> refs, String currentBranch) {
        List<String> lines = new ArrayList<>();
        List<NopGitRef> local = refs.stream().filter(NopGitRef::isBranch)
                .sorted(Comparator.comparing(NopGitRef::shortName)).toList();
        for (NopGitRef ref : local) {
            boolean current = ref.shortName().equals(currentBranch);
            lines.add((current ? "* " : "  ") + ref.shortName());
        }
        List<NopGitRef> remotes = refs.stream().filter(NopGitRef::isRemote)
                .sorted(Comparator.comparing(NopGitRef::name)).toList();
        for (NopGitRef ref : remotes) {
            if (ref.symbolicTarget() != null) {
                String target = ref.symbolicTarget().replaceFirst("refs/remotes/", "");
                lines.add("  remotes/" + ref.shortName() + " -> " + target);
            } else {
                lines.add("  remotes/" + ref.shortName());
            }
        }
        return lines;
    }
}
