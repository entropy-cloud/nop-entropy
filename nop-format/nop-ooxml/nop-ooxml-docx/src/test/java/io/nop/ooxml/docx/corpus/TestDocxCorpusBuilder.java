package io.nop.ooxml.docx.corpus;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corpus synthesis smoke tests: every builder method must produce (a) byte-identical
 * output on repeated invocation (determinism contract) and (b) a structurally valid
 * docx package (required parts present, every XML part well-formed).
 *
 * <p>Visual (LibreOffice) opening of selected samples is covered by TestDocxVisualVerifier;
 * this test stays offline and deterministic.</p>
 */
public class TestDocxCorpusBuilder {

    private static final String[] ALL_SAMPLES = {
            "simpleDocument", "titleAndListDoc", "imageDoc", "hyperlinkDoc",
            "inlineTabBreakDoc", "pageBreakAndSeparatorDoc", "multiSectionDoc",
            "mergedTableDoc", "fullRunStyleDoc", "pageSetupDoc", "backgroundWatermarkDoc"};

    @Test
    public void testDeterministicByteIdenticalOutput() {
        for (String sample : ALL_SAMPLES) {
            byte[] first = build(sample);
            byte[] second = build(sample);
            assertArrayEquals(first, second, "sample not byte-identical across invocations: " + sample);
        }
    }

    @Test
    public void testZipPartStructure() throws Exception {
        Set<String> parts = partNames(build("simpleDocument"));
        assertRequiredCoreParts(parts, "simpleDocument");
        assertEquals(5, parts.size(), "simple sample should have exactly core parts: " + parts);

        parts = partNames(build("titleAndListDoc"));
        assertRequiredCoreParts(parts, "titleAndListDoc");
        assertTrue(parts.contains("word/numbering.xml"), "list sample needs numbering part");

        parts = partNames(build("imageDoc"));
        assertRequiredCoreParts(parts, "imageDoc");
        assertTrue(parts.contains("word/media/image1.png"), "image sample needs media part");

        parts = partNames(build("multiSectionDoc"));
        assertRequiredCoreParts(parts, "multiSectionDoc");
        assertTrue(parts.contains("word/header1.xml") && parts.contains("word/footer1.xml")
                && parts.contains("word/header2.xml") && parts.contains("word/footer2.xml"),
                "multi-section sample needs two header/footer pairs: " + parts);

        parts = partNames(build("backgroundWatermarkDoc"));
        assertRequiredCoreParts(parts, "backgroundWatermarkDoc");
        assertTrue(parts.contains("word/settings.xml") && parts.contains("word/header1.xml"),
                "background sample needs settings + header (watermark) parts");
    }

    @Test
    public void testEveryXmlPartIsWellFormed() throws Exception {
        for (String sample : ALL_SAMPLES) {
            assertAllXmlPartsWellFormed(build(sample), sample);
        }
    }

    @Test
    public void testDocumentRelationshipsResolve() throws Exception {
        String rels = partText(build("imageDoc"), "word/_rels/document.xml.rels");
        assertTrue(rels.contains("Target=\"media/image1.png\""), "image rel target: " + rels);
        assertTrue(rels.contains("Type=\"" + DocxCorpusRelationships.IMAGE + "\""), "image rel type");

        rels = partText(build("hyperlinkDoc"), "word/_rels/document.xml.rels");
        assertTrue(rels.contains("TargetMode=\"External\""), "hyperlink rel must be external: " + rels);
    }

    // ------------------------------------------------------------------ helpers

    /** Relationship type constants mirrored here to avoid depending on builder internals. */
    static final class DocxCorpusRelationships {
        static final String IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
        static final String HYPERLINK = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink";
    }

    private static byte[] build(String sample) {
        Supplier<byte[]> supplier = supplierFor(sample);
        return supplier.get();
    }

    private static Supplier<byte[]> supplierFor(String sample) {
        switch (sample) {
            case "simpleDocument":
                return DocxCorpusBuilder::simpleDocument;
            case "titleAndListDoc":
                return DocxCorpusBuilder::titleAndListDoc;
            case "imageDoc":
                return DocxCorpusBuilder::imageDoc;
            case "hyperlinkDoc":
                return DocxCorpusBuilder::hyperlinkDoc;
            case "inlineTabBreakDoc":
                return DocxCorpusBuilder::inlineTabBreakDoc;
            case "pageBreakAndSeparatorDoc":
                return DocxCorpusBuilder::pageBreakAndSeparatorDoc;
            case "multiSectionDoc":
                return DocxCorpusBuilder::multiSectionDoc;
            case "mergedTableDoc":
                return DocxCorpusBuilder::mergedTableDoc;
            case "fullRunStyleDoc":
                return DocxCorpusBuilder::fullRunStyleDoc;
            case "pageSetupDoc":
                return DocxCorpusBuilder::pageSetupDoc;
            case "backgroundWatermarkDoc":
                return DocxCorpusBuilder::backgroundWatermarkDoc;
            default:
                throw new IllegalArgumentException("unknown sample: " + sample);
        }
    }

    private static void assertRequiredCoreParts(Set<String> parts, String sample) {
        for (String required : new String[]{
                "[Content_Types].xml", "_rels/.rels", "word/document.xml",
                "word/_rels/document.xml.rels", "word/styles.xml"}) {
            assertTrue(parts.contains(required),
                    "sample " + sample + " missing required part " + required + ": " + parts);
        }
    }

    private static Set<String> partNames(byte[] docx) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static String partText(byte[] docx, String name) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    byte[] bytes = readAll(zip);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private static void assertAllXmlPartsWellFormed(byte[] docx, String sample) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".xml") && !entry.getName().endsWith(".rels"))
                    continue;
                byte[] bytes = readAll(zip);
                // parse with strict error handling; a malformed part fails the test here
                try (InputStream in = new ByteArrayInputStream(bytes)) {
                    factory.newDocumentBuilder().parse(in);
                } catch (Exception e) {
                    throw new AssertionError(
                            "sample " + sample + " part " + entry.getName() + " is not well-formed", e);
                }
            }
        }
        assertNotNull(docx);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
