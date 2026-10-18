package io.nop.pdf.verify;

import io.nop.commons.util.FileHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPdfGoldenManager extends BaseTestCase {

    private static BufferedImage solid(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, rgb);
        return img;
    }

    private static BufferedImage withBlackBlock(BufferedImage src) {
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++)
                img.setRGB(x, y, src.getRGB(x, y));
        for (int y = 10; y < 14; y++)
            for (int x = 10; x < 14; x++)
                img.setRGB(x, y, 0x000000);
        return img;
    }

    private PdfGoldenManager newManager() {
        Path dir = getTargetFile("pdf-verify/golden").toPath();
        if (Files.exists(dir))
            FileHelper.deleteAll(dir.toFile());
        return new PdfGoldenManager(dir);
    }

    @Test
    public void testGoldenLifecycle() {
        PdfGoldenManager manager = newManager();
        BufferedImage actual = solid(32, 32, 0xFFFFFF);

        // first run: baseline created, must NOT count as pass
        GoldenCompareResult first = manager.compare("page-1", actual);
        assertEquals(GoldenCompareResult.Status.BASELINE_CREATED, first.getStatus());
        assertTrue(Files.exists(first.getGoldenFile()));
        AssertionError baselineErr = assertThrows(AssertionError.class, first::assertPass);
        assertTrue(baselineErr.getMessage().contains("did not pass"));

        // second run, same input: pass
        GoldenCompareResult second = manager.compare("page-1", solid(32, 32, 0xFFFFFF));
        assertEquals(GoldenCompareResult.Status.PASS, second.getStatus());
        second.assertPass();

        // perturbed input: fail with diff details
        GoldenCompareResult failed = manager.compare("page-1", withBlackBlock(actual));
        assertEquals(GoldenCompareResult.Status.FAIL, failed.getStatus());
        assertEquals(16, failed.getDiff().getDiffPixels());
        AssertionError failErr = assertThrows(AssertionError.class, failed::assertPass);
        assertTrue(failErr.getMessage().contains(PdfGoldenManager.UPDATE_MODE_PROPERTY));

        // update mode: overwrite, then pass again
        String oldProp = System.getProperty(PdfGoldenManager.UPDATE_MODE_PROPERTY);
        try {
            System.setProperty(PdfGoldenManager.UPDATE_MODE_PROPERTY, "true");
            GoldenCompareResult updated = manager.compare("page-1", withBlackBlock(actual));
            assertEquals(GoldenCompareResult.Status.UPDATED, updated.getStatus());
        } finally {
            if (oldProp == null)
                System.clearProperty(PdfGoldenManager.UPDATE_MODE_PROPERTY);
            else
                System.setProperty(PdfGoldenManager.UPDATE_MODE_PROPERTY, oldProp);
        }
        assertEquals(GoldenCompareResult.Status.PASS, manager.compare("page-1", withBlackBlock(actual)).getStatus());
    }

    @Test
    public void testUpdateMethodRewritesGolden() {
        PdfGoldenManager manager = newManager();
        manager.update("page-9", solid(16, 16, 0xFF0000));
        assertEquals(GoldenCompareResult.Status.PASS,
                manager.compare("page-9", solid(16, 16, 0xFF0000)).getStatus());
    }

    @Test
    public void testRepoRootResolution() {
        Path root = PdfGoldenManager.resolveRepoRoot();
        assertTrue(Files.exists(root.resolve(".git")),
                "repo root must contain a .git entry (file or dir for worktrees): " + root);
        // default dir binding must nest under _tmp/pdf-golden/<name>
        Path expected = root.resolve("_tmp").resolve("pdf-golden").resolve("some-fixture");
        assertEquals(expected, PdfGoldenManager.forDefaultDir("some-fixture").getGoldenRoot());
    }
}
