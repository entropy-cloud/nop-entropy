package io.nop.ooxml.docx.verify;

import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test-scope visual acceptance bridge for docx fixtures: converts a .docx to PDF via
 * LibreOffice headless ({@code soffice --convert-to pdf}), renders the PDF pages to
 * PNG and compares them against golden snapshots — reusing the nop-pdf verification
 * toolchain ({@link PdfGoldenManager}/{@code PdfImageRenderer}/{@code ImageDiff}).
 *
 * <p><b>Golden directory convention</b>: {@code <repo-root>/_tmp/docx-golden/<fixture>/}
 * (kept separate from the PDF renderer goldens in {@code _tmp/pdf-golden/}). Snapshots
 * are machine-local and not committed.</p>
 *
 * <p><b>Update mode ownership</b>: this class owns the system property
 * {@code nop.docx.golden.update} (deliberately NOT the pdf toolchain's
 * {@code nop.pdf.golden.update}); in update mode mismatched snapshots are overwritten
 * via {@link PdfGoldenManager#update(String, java.awt.image.BufferedImage)}.</p>
 *
 * <p><b>soffice missing degradation</b>: when LibreOffice is not installed, callers
 * skip visual fixtures via {@code Assumptions.assumeTrue(isSofficeAvailable(), ...)}
 * — an explicit named adjudication with a log message, never a silent no-op.</p>
 */
public final class DocxVisualVerifier {

    /** System property turning on golden overwrite for this toolchain's fixtures. */
    public static final String UPDATE_MODE_PROPERTY = "nop.docx.golden.update";

    /** System property overriding the soffice executable path. */
    public static final String SOFFICE_PATH_PROPERTY = "nop.docx.soffice.path";

    private static final long CONVERT_TIMEOUT_SECONDS = 180;

    private final PdfGoldenManager golden;
    private final Path soffice;
    private GoldenCompareResult lastResult;

    private DocxVisualVerifier(PdfGoldenManager golden, Path soffice) {
        this.golden = golden;
        this.soffice = soffice;
    }

    /**
     * Verifier bound to {@code <repo-root>/_tmp/docx-golden/<fixtureName>/}.
     *
     * @throws IllegalStateException when soffice is not available on this machine
     */
    public static DocxVisualVerifier forDefaultDir(String fixtureName) {
        Path dir = PdfGoldenManager.resolveRepoRoot()
                .resolve("_tmp").resolve("docx-golden").resolve(fixtureName);
        return new DocxVisualVerifier(new PdfGoldenManager(dir), requireSoffice());
    }

    /**
     * Locate the soffice executable: explicit system property first, then common
     * install locations, then PATH.
     */
    public static Path findSoffice() {
        String override = System.getProperty(SOFFICE_PATH_PROPERTY);
        if (override != null && Files.isExecutable(Path.of(override)))
            return Path.of(override);

        List<String> candidates = new ArrayList<>(List.of(
                "/opt/homebrew/bin/soffice",
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/usr/bin/soffice",
                "/usr/local/bin/soffice"));
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                candidates.add(Paths.get(dir, "soffice").toString());
            }
        }
        for (String candidate : candidates) {
            Path p = Path.of(candidate);
            if (Files.isExecutable(p))
                return p;
        }
        return null;
    }

    public static boolean isSofficeAvailable() {
        return findSoffice() != null;
    }

    private static Path requireSoffice() {
        Path found = findSoffice();
        if (found == null)
            throw new IllegalStateException("LibreOffice soffice not found; set system property "
                    + SOFFICE_PATH_PROPERTY + " or install LibreOffice");
        return found;
    }

    public boolean isUpdateMode() {
        return Boolean.getBoolean(UPDATE_MODE_PROPERTY);
    }

    /**
     * Convert a .docx file to PDF via LibreOffice headless.
     *
     * @return the converted PDF bytes
     * @throws IllegalStateException when conversion fails or produces no PDF
     */
    public byte[] convertToPdf(Path docx) {
        try {
            Path workDir = Files.createTempDirectory("docx-visual");
            // isolated profile: avoids first-start wizard and concurrent-profile lock conflicts
            Path profile = workDir.resolve("soffice-profile");
            ProcessBuilder pb = new ProcessBuilder(
                    soffice.toString(), "--headless", "--norestore", "--nolockcheck",
                    "-env:UserInstallation=" + profile.toUri(),
                    "--convert-to", "pdf", "--outdir", workDir.toString(),
                    docx.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(CONVERT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new IllegalStateException("soffice conversion timed out after "
                        + CONVERT_TIMEOUT_SECONDS + "s for " + docx);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("soffice conversion failed (exit "
                        + process.exitValue() + ") for " + docx + ": " + output);
            }

            String baseName = docx.getFileName().toString();
            if (baseName.endsWith(".docx"))
                baseName = baseName.substring(0, baseName.length() - 5);
            Path pdf = workDir.resolve(baseName + ".pdf");
            if (!Files.exists(pdf)) {
                throw new IllegalStateException("soffice produced no pdf for " + docx
                        + "; output: " + output);
            }
            byte[] bytes = Files.readAllBytes(pdf);
            deleteRecursively(workDir);
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("soffice conversion io failure for " + docx, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("soffice conversion interrupted for " + docx, e);
        }
    }

    /**
     * Golden-compare one rendered page image and report whether the snapshot is
     * acceptable. PASS and BASELINE_CREATED are both acceptable (first run on a new
     * machine creates the machine-local baseline); FAIL is a regression unless update
     * mode ({@link #UPDATE_MODE_PROPERTY}) overwrote the snapshot.
     */
    public boolean verifyGolden(String pageName, java.awt.image.BufferedImage actual) {
        GoldenCompareResult result = golden.compare(pageName, actual);
        lastResult = result;
        switch (result.getStatus()) {
            case PASS:
            case BASELINE_CREATED:
                return true;
            case UPDATED: // only reachable if the pdf update property was also enabled; treat as acceptable
                return true;
            case FAIL:
                if (isUpdateMode()) {
                    golden.update(pageName, actual);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /** Last comparison outcome, for diagnostics/logging by the caller. */
    public GoldenCompareResult getLastResult() {
        return lastResult;
    }

    public Path getGoldenRoot() {
        return golden.getGoldenRoot();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // best-effort cleanup of temp working dir; original failure surfaced by caller
                }
            });
        }
    }
}
