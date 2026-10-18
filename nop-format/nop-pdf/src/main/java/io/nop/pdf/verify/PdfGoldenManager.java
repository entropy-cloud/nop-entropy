package io.nop.pdf.verify;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages golden PNG snapshots under {@code _tmp/} (gitignored, never committed).
 *
 * <p>Adjudication (plan 2463): golden PNGs are machine-local because non-embedded
 * standard-14 fonts are substituted by environment fonts, so cross-machine pixel equality
 * is not portable. Cross-environment gates must use structural assertions
 * ({@link RegionProbe}). The default golden root is {@code <repo-root>/_tmp/pdf-golden/<name>/},
 * where the repo root is located by walking up from {@code user.dir} until a {@code .git}
 * entry (file or directory, covering git worktrees) is found.</p>
 */
public class PdfGoldenManager {

    /**
     * System property that turns on golden overwrite for mismatched snapshots
     * (update mode: rerun with {@code -Dnop.pdf.golden.update=true}).
     */
    public static final String UPDATE_MODE_PROPERTY = "nop.pdf.golden.update";

    private final Path dir;

    /**
     * @param dir directory holding this manager's golden PNG files; created lazily on first write
     */
    public PdfGoldenManager(Path dir) {
        this.dir = Guard.notNull(dir, "dir");
    }

    /**
     * Directory holding this manager's golden PNG files.
     */
    public Path getGoldenRoot() {
        return dir;
    }

    /**
     * Manager bound to {@code <repo-root>/_tmp/pdf-golden/<name>/}.
     *
     * @param name logical fixture name, used as subdirectory
     */
    public static PdfGoldenManager forDefaultDir(String name) {
        Guard.notEmpty(name, "name");
        return new PdfGoldenManager(resolveRepoRoot().resolve("_tmp").resolve("pdf-golden").resolve(name));
    }

    /**
     * Locate the repository root by walking up from {@code user.dir} until a {@code .git}
     * entry (file or directory, both valid in worktree checkouts) is found.
     *
     * @throws IllegalStateException when no {@code .git} entry exists up to the filesystem root
     */
    public static Path resolveRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve(".git")))
                return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Cannot locate repository root: no .git entry found walking up from user.dir="
                        + System.getProperty("user.dir"));
    }

    /**
     * Compare the actual image against the golden snapshot named {@code pageName}.
     *
     * <ul>
     *     <li>Golden missing: saves the actual image as baseline and reports
     *     {@link GoldenCompareResult.Status#BASELINE_CREATED} (never silently passes).</li>
     *     <li>Golden matches pixel-identically: {@link GoldenCompareResult.Status#PASS}.</li>
     *     <li>Golden differs: {@link GoldenCompareResult.Status#FAIL}; in update mode
     *     ({@code -Dnop.pdf.golden.update=true}) the golden is overwritten and
     *     {@link GoldenCompareResult.Status#UPDATED} is reported.</li>
     * </ul>
     *
     * @param pageName snapshot name within the manager directory, e.g. {@code "page-1"}
     * @param actual   the freshly rendered image
     * @return the comparison outcome, never null
     */
    public GoldenCompareResult compare(String pageName, BufferedImage actual) {
        Guard.notEmpty(pageName, "pageName");
        Guard.notNull(actual, "actual");
        Path file = dir.resolve(pageName + ".png");
        if (!Files.exists(file)) {
            save(file, actual);
            return GoldenCompareResult.baselineCreated(file);
        }
        BufferedImage golden = read(file);
        ImageDiffResult diff = ImageDiff.compare(golden, actual);
        if (diff.isIdentical())
            return GoldenCompareResult.pass(file);
        if (isUpdateMode()) {
            save(file, actual);
            return GoldenCompareResult.updated(file);
        }
        return GoldenCompareResult.fail(file, diff);
    }

    /**
     * Unconditionally (re)write the golden snapshot for {@code pageName}.
     */
    public void update(String pageName, BufferedImage actual) {
        Guard.notEmpty(pageName, "pageName");
        Guard.notNull(actual, "actual");
        save(dir.resolve(pageName + ".png"), actual);
    }

    public static boolean isUpdateMode() {
        return Boolean.getBoolean(UPDATE_MODE_PROPERTY);
    }

    private void save(Path file, BufferedImage image) {
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    private BufferedImage read(Path file) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null)
                throw new IllegalStateException("Not a readable image file: " + file);
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
