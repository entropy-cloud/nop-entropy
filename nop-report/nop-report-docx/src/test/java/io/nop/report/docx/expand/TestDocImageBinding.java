package io.nop.report.docx.expand;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.collections.KeyedList;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.resource.IResource;
import io.nop.integration.api.qrcode.IQrcodeService;
import io.nop.integration.api.qrcode.QrcodeOptions;
import io.nop.integration.qrcode.ZxingQrcodeService;
import io.nop.office.doc.model.NopOfficeDocModelException;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeChartBindingModel;
import io.nop.office.doc.model.OfficeCodeBindingModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeImageTemplateModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.ooxml.docx.output.OfficeDocModelWriter;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import io.nop.report.pdf.doc.OfficeDocPdfRenderer;
import jakarta.annotation.Nonnull;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 Phase 3 (plan 2523): image binding acceptance. Chart (echarts), QR
 * (decode round-trip), code128 (bar structure), B5 size defaults, fail-fast
 * matrix, expander integration and the E4 assets-copy regression.
 */
public class TestDocImageBinding extends JunitBaseTestCase {
    static final String BAR_OPTION_JSON =
            "{\"title\":{\"text\":\"销售额月报\",\"textStyle\":{\"fontSize\":18}},"
                    + "\"xAxis\":{\"type\":\"category\",\"data\":[\"一月\",\"二月\",\"三月\"]},"
                    + "\"yAxis\":{\"type\":\"value\"},"
                    + "\"series\":[{\"type\":\"bar\",\"data\":[100,200,300]}]}";

    private IBeanContainer previousContainer;
    private CapturingQrcodeService capturingService;

    @BeforeEach
    void setUp() {
        capturingService = new CapturingQrcodeService();
        previousContainer = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
    }

    @AfterEach
    void cleanup() {
        BeanContainer.registerInstance(previousContainer);
    }

    // ---- model builders ---------------------------------------------------

    private static OfficeDocModel doc(OfficeBlock... blocks) {
        OfficeDocModel docModel = new OfficeDocModel();
        docModel.setWidth(595.3);
        docModel.setHeight(841.9);
        docModel.setPages(new KeyedList<>(p -> p.getName()));
        docModel.getPages().add(page(blocks));
        return docModel;
    }

    private static OfficeDocPageModel page(OfficeBlock... blocks) {
        OfficeDocPageModel p = new OfficeDocPageModel();
        p.setName("page1");
        OfficePageMargins margins = new OfficePageMargins();
        margins.setTop(72.0);
        margins.setBottom(72.0);
        margins.setLeft(72.0);
        margins.setRight(72.0);
        p.setMargins(margins);
        p.setBody(new ArrayList<>());
        p.setHeader(new ArrayList<>());
        p.setFooter(new ArrayList<>());
        p.getBody().addAll(Arrays.asList(blocks));
        return p;
    }

    private static OfficeImageModel image(OfficeImageTemplateModel model) {
        OfficeImageModel image = new OfficeImageModel();
        image.setId("img1");
        image.setWidth(400.0);
        image.setHeight(200.0);
        image.setModel(model);
        return image;
    }

    private static OfficeImageTemplateModel templateModel() {
        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setChartBinding(chartBinding());
        return model;
    }

    private static OfficeChartBindingModel chartBinding() {
        OfficeChartBindingModel binding = new OfficeChartBindingModel();
        binding.setOptionExpr((IEvalAction) ctx -> BAR_OPTION_JSON);
        // explicit size keeps the echarts render cheap; B5 defaults are covered
        // in testChartDefaultSizeUses800x400
        binding.setWidth(400);
        binding.setHeight(200);
        return binding;
    }

    private static OfficeCodeBindingModel codeBinding(String codeType, IEvalAction codeExpr) {
        OfficeCodeBindingModel binding = new OfficeCodeBindingModel();
        binding.setCodeType(codeType);
        binding.setCodeExpr(codeExpr);
        return binding;
    }

    // ---- chart binding ------------------------------------------------------

    @Test
    public void testChartBindingProducesRealPng() throws Exception {
        OfficeDocModel docModel = doc(image(templateModel()));
        new DocImageBinder().bind(docModel, null);

        OfficeImageModel img = (OfficeImageModel) docModel.getPages().get(0).getBody().get(0);
        assertEquals("__gen-img-1", img.getAssetRef());
        OfficeImageAsset asset = genAsset(docModel);
        assertNotNull(asset);
        assertEquals("png", asset.getExt());
        byte[] data = asset.getData().toByteArray();
        assertTrue(data.length > 4);
        // PNG magic
        assertEquals(0x89, data[0] & 0xFF);
        assertEquals(0x50, data[1] & 0xFF);
        assertEquals(0x4E, data[2] & 0xFF);
        assertEquals(0x47, data[3] & 0xFF);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(data));
        assertEquals(400, decoded.getWidth());
        assertEquals(200, decoded.getHeight());

        verifyDocxExit(docModel, data);
        verifyPdfExit(docModel);
    }

    /**
     * B5: a chartBinding without explicit size renders at the named default
     * 800x400 (xdef defaults; the binder guards the same values for
     * programmatically built models).
     */
    @Test
    public void testChartDefaultSizeUses800x400() throws Exception {
        OfficeChartBindingModel binding = new OfficeChartBindingModel();
        binding.setOptionExpr((IEvalAction) ctx -> BAR_OPTION_JSON);
        binding.setWidth(0);
        binding.setHeight(0);

        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setChartBinding(binding);
        OfficeDocModel docModel = doc(image(model));
        new DocImageBinder().bind(docModel, null);

        byte[] data = genAsset(docModel).getData().toByteArray();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(data));
        assertEquals(800, decoded.getWidth());
        assertEquals(400, decoded.getHeight());
    }

    // ---- qr binding -------------------------------------------------------

    @Test
    public void testQrBindingDecodesBackToContent() throws Exception {
        BeanContainer.registerInstance(singleServiceContainer(new ZxingQrcodeService()));

        OfficeCodeBindingModel binding = codeBinding("qr", ctx -> "https://nop.example.com/wi8");
        binding.setWidth(200);
        binding.setHeight(200);
        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setCodeBinding(binding);

        OfficeDocModel docModel = doc(image(model));
        new DocImageBinder().bind(docModel, null);

        byte[] data = genAsset(docModel).getData().toByteArray();
        String decoded = decodeQr(data);
        assertEquals("https://nop.example.com/wi8", decoded);
    }

    /**
     * B5 for qr: width/height 0 must not be set on QrcodeOptions (service
     * default applies).
     */
    @Test
    public void testQrDefaultSizeLeavesOptionsUnset() {
        BeanContainer.registerInstance(singleServiceContainer(capturingService));

        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setCodeBinding(codeBinding("qr", ctx -> "content"));

        new DocImageBinder().bind(doc(image(model)), null);
        QrcodeOptions options = capturingService.lastOptions;
        assertNotNull(options);
        assertEquals("content", options.getContent());
        assertEquals(0.0, options.getWidth());
        assertEquals(0.0, options.getHeight());
    }

    // ---- code128 binding --------------------------------------------------

    @Test
    public void testCode128ProducesBarStructure() throws Exception {
        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setCodeBinding(codeBinding("code128", ctx -> "NOP-2523"));

        OfficeDocModel docModel = doc(image(model));
        new DocImageBinder().bind(docModel, null);

        byte[] data = genAsset(docModel).getData().toByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        assertNotNull(img);
        // B5 content-fit: height falls back to the fixed default bar height
        assertEquals(100, img.getHeight());
        assertTrue(img.getWidth() > 0);

        // black/white column alternation: there must be dark and light columns
        int darkColumns = 0;
        int lightColumns = 0;
        for (int x = 0; x < img.getWidth(); x++) {
            boolean dark = isColumnDark(img, x);
            if (dark)
                darkColumns++;
            else
                lightColumns++;
        }
        assertTrue(darkColumns > 0, "code128 must contain dark columns");
        assertTrue(lightColumns > 0, "code128 must contain light columns");
        assertTrue(darkColumns < img.getWidth(), "bars must alternate with quiet zones");
    }

    @Test
    public void testCode128ExplicitWidthHeight() throws Exception {
        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        OfficeCodeBindingModel binding = codeBinding("code128", ctx -> "X");
        binding.setWidth(300);
        binding.setHeight(60);
        model.setCodeBinding(binding);

        OfficeDocModel docModel = doc(image(model));
        new DocImageBinder().bind(docModel, null);

        byte[] data = genAsset(docModel).getData().toByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        assertEquals(300, img.getWidth());
        assertEquals(60, img.getHeight());
    }

    // ---- fail fast (B1/B3) ---------------------------------------------

    @Test
    public void testDoubleBindingFailsFast() {
        OfficeImageTemplateModel model = templateModel();
        model.setCodeBinding(codeBinding("code128", ctx -> "X"));

        NopOfficeDocModelException ex = assertThrows(NopOfficeDocModelException.class,
                () -> new DocImageBinder().bind(doc(image(model)), null));
        assertTrue(ex.getMessage().contains("mutually exclusive"));
    }

    @Test
    public void testMissingCodeExprFailsFast() {
        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setCodeBinding(codeBinding("qr", null));

        NopOfficeDocModelException ex = assertThrows(NopOfficeDocModelException.class,
                () -> new DocImageBinder().bind(doc(image(model)), null));
        assertTrue(ex.getMessage().contains("codeExpr"));
    }

    @Test
    public void testUnsupportedCodeTypeFailsFast() {
        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setCodeBinding(codeBinding("barcode39", ctx -> "X"));

        NopOfficeDocModelException ex = assertThrows(NopOfficeDocModelException.class,
                () -> new DocImageBinder().bind(doc(image(model)), null));
        assertTrue(ex.getMessage().contains("barcode39"));
    }

    /**
     * B3: a declared chartBinding with the echarts renderer class missing must be
     * a named error pointing at the missing dependency - never a silent skip.
     * The probe class name is injectable so the test does not have to strip the
     * dependency from the classpath.
     */
    @Test
    public void testMissingEchartsClassFailsFast() {
        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setChartBinding(chartBinding());

        DocImageBinder binder = new DocImageBinder("io.nop.chart.echarts.render.MissingRenderer");
        NopOfficeDocModelException ex = assertThrows(NopOfficeDocModelException.class,
                () -> binder.bind(doc(image(model)), null));
        assertTrue(ex.getMessage().contains("nop-chart-echarts"));
    }

    @Test
    public void testQrWithoutServiceBeanFailsFast() {
        BeanContainer.registerInstance(null);

        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setCodeBinding(codeBinding("qr", ctx -> "X"));

        NopOfficeDocModelException ex = assertThrows(NopOfficeDocModelException.class,
                () -> new DocImageBinder().bind(doc(image(model)), null));
        assertTrue(ex.getMessage().contains("IQrcodeService"));
    }

    // ---- no-op / E4 invariants ------------------------------------------

    @Test
    public void testUnboundImageIsNoOp() {
        OfficeImageModel img = image(null);
        img.setAssetRef("word/media/existing.png");
        OfficeDocModel docModel = doc(img);
        docModel.setAssets(new KeyedList<>(a -> a.getName()));

        new DocImageBinder().bind(docModel, null);
        assertEquals("word/media/existing.png", img.getAssetRef());
        assertTrue(docModel.getAssets().isEmpty());
    }

    /**
     * E4 regression (WI7 residual gap): expand shares the input doc's assets
     * list; bind must materialize a copy so the input template is never
     * polluted, and repeated expand+bind stays idempotent.
     */
    @Test
    public void testAssetsAreCopiedNotSharedWithInput() {
        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setCodeBinding(codeBinding("code128", ctx -> "E4"));
        OfficeImageModel img = image(model);

        OfficeDocModel input = doc(img);
        KeyedList<OfficeImageAsset> inputAssets = new KeyedList<>(a -> a.getName());
        input.setAssets(inputAssets);

        OfficeDocModel out = new OfficeDocModelExpander().expand(input, Map.of());
        assertTrue(out.getAssets() != inputAssets, "bind must materialize its own assets list");
        assertEquals(1, out.getAssets().size());
        assertEquals("__gen-img-1", out.getAssets().iterator().next().getName());
        assertTrue(inputAssets.isEmpty(), "input template assets must stay untouched");

        // repeated expand+bind on the same input is idempotent for the input
        OfficeDocModel out2 = new OfficeDocModelExpander().expand(input, Map.of());
        assertEquals(1, out2.getAssets().size());
        assertTrue(inputAssets.isEmpty());
    }

    // ---- expander integration -------------------------------------------

    @Test
    public void testExpanderIntegratesTableAndImageBinding() {
        // xpt table (vertical expansion) + bound code128 image in one model
        io.nop.office.doc.model.WordTable table = new io.nop.office.doc.model.WordTable();
        table.setId("t1");
        io.nop.office.doc.model.WordTableRow header = new io.nop.office.doc.model.WordTableRow();
        io.nop.office.doc.model.WordTableCell h = new io.nop.office.doc.model.WordTableCell();
        h.setId("c1");
        h.setValue("name");
        header.getCells().add(h);
        table.getRows().add(header);
        io.nop.office.doc.model.WordTableRow dataRow = new io.nop.office.doc.model.WordTableRow();
        io.nop.office.doc.model.WordTableCell c = new io.nop.office.doc.model.WordTableCell();
        c.setId("c2");
        c.setValue("*=^data!name");
        dataRow.getCells().add(c);
        table.getRows().add(dataRow);
        table.makeModel().setRenderType("xpt");

        OfficeImageTemplateModel model = new OfficeImageTemplateModel();
        model.setCodeBinding(codeBinding("code128", ctx -> "ORDER-001"));
        OfficeImageModel img = image(model);

        OfficeDocModel input = doc(table, img);
        input.setAssets(new KeyedList<>(a -> a.getName()));

        OfficeDocModel out = new OfficeDocModelExpander().expand(input, Map.of("data",
                Arrays.asList(Map.of("name", "a"), Map.of("name", "b"))));

        List<OfficeBlock> body = out.getPages().get(0).getBody();
        io.nop.office.doc.model.WordTable expanded = (io.nop.office.doc.model.WordTable) body.get(0);
        assertEquals(3, expanded.getRowCount(), "header + 2 data rows");
        OfficeImageModel boundImg = (OfficeImageModel) body.get(1);
        assertEquals("__gen-img-1", boundImg.getAssetRef());
        assertNotNull(findGeneratedAsset(out));
    }

    // ---- helpers ---------------------------------------------------------

    private static boolean isColumnDark(BufferedImage img, int x) {
        int dark = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            int rgb = img.getRGB(x, y) & 0xFFFFFF;
            if (rgb < 0x808080)
                dark++;
        }
        return dark > img.getHeight() / 2;
    }

    private static String decodeQr(byte[] png) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(img)));
        Result result = new MultiFormatReader().decode(bitmap,
                Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE));
        return result.getText();
    }

    /** docx exit link: write -> reparse -> asset bytes identical */
    private void verifyDocxExit(OfficeDocModel docModel, byte[] expectedPng) throws Exception {
        IResource target = getTargetResource("wi8-bind-result.docx");
        new OfficeDocModelWriter().writeToResource(docModel, target);
        OfficeDocModel reparsed = new OfficeDocModelParser().parseFromResource(target);
        assertNotNull(reparsed.getAssets());
        assertTrue(reparsed.getAssets().size() >= 1);
        byte[] reparsedData = findGeneratedAsset(reparsed).getData().toByteArray();
        assertArrayEquals(expectedPng, reparsedData);
    }

    /** assets is a plain List in the model layer; look the generated asset up by name */
    private static OfficeImageAsset genAsset(OfficeDocModel doc) {
        OfficeImageAsset asset = findGeneratedAsset(doc);
        assertNotNull(asset);
        return asset;
    }

    private static OfficeImageAsset findGeneratedAsset(OfficeDocModel doc) {
        for (OfficeImageAsset asset : doc.getAssets()) {
            if (asset.getName() != null && asset.getName().contains("__gen-img-"))
                return asset;
        }
        return null;
    }

    /**
     * PDF exit link: render the doc and probe the chart region (first block,
     * 400x200 pt at the top of the page) for non-white pixels.
     */
    private void verifyPdfExit(OfficeDocModel docModel) throws Exception {
        byte[] pdf = new OfficeDocPdfRenderer().render(docModel);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage page = new PDFRenderer(document).renderImageWithDPI(0, 72);
            int nonWhite = 0;
            // chart placement: margins 72pt, image 400x200 pt as first block
            for (int y = 72; y < 272 && y < page.getHeight(); y++) {
                for (int x = 72; x < 472 && x < page.getWidth(); x++) {
                    int rgb = page.getRGB(x, y) & 0xFFFFFF;
                    if (rgb != 0xFFFFFF && rgb != 0x000000)
                        nonWhite++;
                }
            }
            assertTrue(nonWhite > 50,
                    "chart region must contain rendered chart pixels, nonWhite=" + nonWhite);
        }
    }

    private static IBeanContainer singleServiceContainer(IQrcodeService service) {
        return new IBeanContainer() {
            @Override
            public String getId() {
                return "wi8-test-container";
            }

            @Override
            public void start() {
            }

            @Override
            public void stop() {
            }

            @Override
            public void restart() {
            }

            @Override
            public boolean containsBean(String name) {
                return false;
            }

            @Override
            public boolean isRunning() {
                return true;
            }

            @Nonnull
            @Override
            public Object getBean(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean containsBeanType(Class<?> clazz) {
                return clazz == IQrcodeService.class;
            }

            @Nonnull
            @Override
            public <T> T getBeanByType(Class<T> clazz) {
                if (clazz == IQrcodeService.class)
                    return clazz.cast(service);
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T tryGetBeanByType(Class<T> clazz) {
                return clazz == IQrcodeService.class ? clazz.cast(service) : null;
            }

            @Override
            public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
                return Map.of();
            }

            @Override
            public Map<String, Object> getBeansWithAnnotation(Class<? extends java.lang.annotation.Annotation> annClass) {
                return Map.of();
            }

            @Override
            public String getBeanScope(String name) {
                return "singleton";
            }

            @Override
            public Class<?> getBeanClass(String name) {
                return null;
            }

            @Override
            public String findAutowireCandidate(Class<?> beanType) {
                return null;
            }
        };
    }

    static class CapturingQrcodeService implements IQrcodeService {
        QrcodeOptions lastOptions;

        @Override
        public byte[] createQrcodeBytes(QrcodeOptions options) {
            this.lastOptions = options;
            return new byte[]{1, 2, 3};
        }
    }
}
