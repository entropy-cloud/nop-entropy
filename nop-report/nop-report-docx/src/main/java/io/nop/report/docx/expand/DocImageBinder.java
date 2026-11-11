package io.nop.report.docx.expand;

import com.google.zxing.oned.Code128Writer;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.KeyedList;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.integration.api.qrcode.IQrcodeService;
import io.nop.integration.api.qrcode.QrcodeOptions;
import io.nop.office.doc.model.NopOfficeDocModelException;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeChartBindingModel;
import io.nop.office.doc.model.OfficeCodeBindingModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeImageTemplateModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * WI8 (plan 2523): image placeholder binding, decisions B1-B5 in
 * ai-dev/design/word-editor/online-word-model-design.md section 20.
 *
 * <ul>
 * <li>B1: chartBinding / codeBinding are mutually exclusive children of
 * OfficeImageTemplateModel; defining both is a fail-fast error.</li>
 * <li>B2: products are injected into doc.assets as {@code __gen-img-<n>} and the
 * image assetRef is rewritten. The assets list is materialized into a fresh
 * KeyedList copying the existing asset references before the first injection -
 * expand shares the input doc's assets list (WI7 residual gap), and the binder
 * must never pollute the input template (E4 invariant).</li>
 * <li>B3: nop-chart-echarts is opt-in: EChartsRenderer is probed via
 * Class.forName and instantiated only with its public default constructor (its
 * engine field is a private singleton; do not reflectively construct
 * EchartsEngine). A declared chartBinding with the renderer class missing is a
 * named error, never a silent skip.</li>
 * <li>B4: code128 uses an in-module zxing Code128Writer bit matrix rendered to
 * PNG via ImageIO; QR codes go through the IQrcodeService bean.</li>
 * <li>B5: default sizes - chart 800x400 (xdef defaults, guarded here for
 * programmatic models), qr 0 means service default (option not set), code128 0
 * means content-fit (matrix size scaled).</li>
 * </ul>
 *
 * Images without a binding definition are a complete no-op.
 */
public class DocImageBinder {
    public static final String DEFAULT_CHART_RENDERER_CLASS =
            "io.nop.chart.echarts.render.EChartsRenderer";
    public static final String GEN_IMAGE_NAME_PREFIX = "__gen-img-";
    /** fallback scale for code128 when the binding gives no explicit size */
    private static final int CODE128_SCALE = 2;
    private static final int DEFAULT_CODE128_HEIGHT = 100;
    private static final double DEFAULT_CHART_WIDTH = 800;
    private static final double DEFAULT_CHART_HEIGHT = 400;

    private final String chartRendererClassName;
    /** asset name sequence; keeps increasing across multiple bind() calls */
    private int seq = 0;

    /**
     * WI9: number of images bound by the most recent {@link #bind} call
     * (observability only; no behavioral effect).
     */
    private int lastBoundImageCount;

    public DocImageBinder() {
        this(DEFAULT_CHART_RENDERER_CLASS);
    }

    /**
     * @param chartRendererClassName overridable so tests can simulate a classpath
     *                               without nop-chart-echarts (fail-fast coverage
     *                               without actually stripping the dependency)
     */
    public DocImageBinder(String chartRendererClassName) {
        this.chartRendererClassName = chartRendererClassName;
    }

    /**
     * Binds all image blocks that carry a chartBinding/codeBinding definition in
     * the page body, header and footer block sequences. Images without a binding
     * definition are not touched.
     */
    public void bind(OfficeDocModel doc, IEvalScope scope) {
        lastBoundImageCount = 0;
        if (doc == null || doc.getPages() == null)
            return;
        for (OfficeDocPageModel page : doc.getPages()) {
            bindBlocks(doc, page.getBody(), scope);
            bindBlocks(doc, page.getHeader(), scope);
            bindBlocks(doc, page.getFooter(), scope);
        }
    }

    private void bindBlocks(OfficeDocModel doc, List<OfficeBlock> blocks, IEvalScope scope) {
        if (blocks == null)
            return;
        for (ListIterator<OfficeBlock> it = blocks.listIterator(); it.hasNext();) {
            OfficeBlock block = it.next();
            if (block instanceof OfficeImageModel) {
                OfficeImageModel bound = bindImage(doc, (OfficeImageModel) block, scope);
                if (bound != block)
                    it.set(bound);
            }
        }
    }

    private OfficeImageModel bindImage(OfficeDocModel doc, OfficeImageModel image, IEvalScope scope) {
        OfficeImageTemplateModel model = image.getModel();
        if (model == null)
            return image;
        OfficeChartBindingModel chart = model.getChartBinding();
        OfficeCodeBindingModel code = model.getCodeBinding();
        if (chart == null && code == null)
            return image; // no-op for unbound images

        if (chart != null && code != null)
            throw new NopOfficeDocModelException("image '" + image.getId()
                    + "' defines both chartBinding and codeBinding; they are mutually exclusive");

        String name = GEN_IMAGE_NAME_PREFIX + (++seq);
        byte[] data;
        if (chart != null) {
            data = renderChart(chart, image, scope);
        } else {
            data = renderCode(code, image, scope);
        }
        // materialize the image block before rewriting its assetRef: the block instance
        // may be shared with the input template (expand copies share blocks), and the
        // input must never be mutated (E4)
        OfficeImageModel copy = new OfficeImageModel();
        copy.setId(image.getId());
        copy.setType(image.getType());
        copy.setAssetRef(image.getAssetRef());
        copy.setWidth(image.getWidth());
        copy.setHeight(image.getHeight());
        copy.setLocation(image.getLocation());
        copy.setModel(model);
        injectAsset(doc, copy, name, data);
        lastBoundImageCount++;
        return copy;
    }

    /**
     * WI9: number of images bound by the most recent {@link #bind} call.
     */
    public int getLastBoundImageCount() {
        return lastBoundImageCount;
    }

    // ---- chart binding (B3) ----------------------------------------------

    private byte[] renderChart(OfficeChartBindingModel binding, OfficeImageModel image, IEvalScope scope) {
        if (binding.getOptionExpr() == null)
            throw new NopOfficeDocModelException(
                    "image '" + image.getId() + "' has chartBinding without optionExpr");

        Class<?> rendererClass;
        try {
            rendererClass = Class.forName(chartRendererClassName);
        } catch (ClassNotFoundException e) {
            throw new NopOfficeDocModelException(
                    "chartBinding requires the nop-chart-echarts dependency (class "
                            + chartRendererClassName + " not found on classpath)",
                    e);
        }

        Object optionValue = binding.getOptionExpr().invoke(scope);
        if (optionValue == null)
            throw new NopOfficeDocModelException(
                    "chartBinding optionExpr for image '" + image.getId() + "' evaluated to null");

        // B5: xdef defaults are 800x400; guard for programmatically built models
        double width = binding.getWidth() > 0 ? binding.getWidth() : DEFAULT_CHART_WIDTH;
        double height = binding.getHeight() > 0 ? binding.getHeight() : DEFAULT_CHART_HEIGHT;
        try {
            // only the public default constructor - the engine inside is a
            // private singleton (B3), never reflectively constructed
            Object renderer = rendererClass.getDeclaredConstructor().newInstance();
            return (byte[]) rendererClass
                    .getMethod("renderPng", String.class, int.class, int.class)
                    .invoke(renderer, String.valueOf(optionValue), (int) width, (int) height);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new NopOfficeDocModelException(
                    "chart rendering failed for image '" + image.getId() + "': " + e.getCause().getMessage(),
                    e.getCause());
        } catch (Exception e) {
            throw new NopOfficeDocModelException(
                    "chart rendering failed for image '" + image.getId() + "': " + e.getMessage(), e);
        }
    }

    // ---- code binding (B4/B5) --------------------------------------------

    private byte[] renderCode(OfficeCodeBindingModel binding, OfficeImageModel image, IEvalScope scope) {
        if (binding.getCodeExpr() == null)
            throw new NopOfficeDocModelException(
                    "image '" + image.getId() + "' has codeBinding without codeExpr");
        String codeType = binding.getCodeType();
        if (codeType == null || codeType.isEmpty())
            throw new NopOfficeDocModelException(
                    "image '" + image.getId() + "' has codeBinding without codeType");

        Object value = binding.getCodeExpr().invoke(scope);
        if (value == null)
            throw new NopOfficeDocModelException(
                    "codeBinding codeExpr for image '" + image.getId() + "' evaluated to null");
        String content = String.valueOf(value);

        switch (codeType) {
            case "qr":
                return renderQr(image, content, binding.getWidth(), binding.getHeight());
            case "code128":
                return renderCode128(image, content, binding.getWidth(), binding.getHeight());
            default:
                throw new NopOfficeDocModelException("unsupported codeBinding codeType '" + codeType
                        + "' on image '" + image.getId() + "'; supported: qr, code128");
        }
    }

    private byte[] renderQr(OfficeImageModel image, String content, double width, double height) {
        // null-safe: an uninitialized (detached) container means no service bean
        IBeanContainer container = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
        IQrcodeService service = container == null ? null : container.tryGetBeanByType(IQrcodeService.class);
        if (service == null)
            throw new NopOfficeDocModelException(
                    "codeBinding codeType=qr requires an IQrcodeService bean (e.g. nop-integration-zxing)");

        // B5: 0 means service default - do not set the option
        QrcodeOptions options = new QrcodeOptions();
        options.setContent(content);
        if (width > 0)
            options.setWidth(width);
        if (height > 0)
            options.setHeight(height);
        try {
            return service.createQrcodeBytes(options);
        } catch (NopOfficeDocModelException e) {
            throw e;
        } catch (Exception e) {
            throw new NopOfficeDocModelException(
                    "qr rendering failed for image '" + image.getId() + "': " + e.getMessage(), e);
        }
    }

    /**
     * B4: private in-module code128 rendering - zxing bit matrix to PNG via
     * ImageIO. B5: width/height 0 means content-fit (matrix size x scale).
     */
    private byte[] renderCode128(OfficeImageModel image, String content, double width, double height) {
        boolean[] bars;
        try {
            // single-row bit pattern; bars = module columns
            bars = new Code128Writer().encode(content);
        } catch (Exception e) {
            throw new NopOfficeDocModelException(
                    "code128 encoding failed for image '" + image.getId() + "': " + e.getMessage(), e);
        }

        // B5: content-fit = module columns x scale; default bar height fixed so a
        // 1D barcode stays readable
        int w = width > 0 ? (int) width : bars.length * CODE128_SCALE;
        int h = height > 0 ? (int) height : DEFAULT_CODE128_HEIGHT;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_BGR);
        for (int x = 0; x < w; x++) {
            boolean dark = bars[(int) ((long) x * bars.length / w)];
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, dark ? 0x000000 : 0xFFFFFF);
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new NopOfficeDocModelException(
                    "code128 PNG encoding failed for image '" + image.getId() + "': " + e.getMessage(), e);
        }
    }

    // ---- asset injection (B2) --------------------------------------------

    /**
     * Injects the rendered bytes as a new asset named {@code __gen-img-<n>} and
     * rewrites the image assetRef. Before the first injection the assets list is
     * materialized into a fresh KeyedList copying the existing asset references -
     * expand shares the input doc's assets list, and the input template must stay
     * untouched (E4 / WI7 residual gap fix).
     */
    private void injectAsset(OfficeDocModel doc, OfficeImageModel image, String name, byte[] data) {
        List<OfficeImageAsset> assets = doc.getAssets();
        // Materialize once per doc instance (identity-based): the expand result
        // shares the input template's assets list, and mutating it would pollute
        // the input (E4 / WI7 residual gap). Subsequent injections on the same
        // doc reuse the already-materialized copy.
        if (materializedDocs.add(doc)) { // first injection on this doc: materialize
            KeyedList<OfficeImageAsset> copy = new KeyedList<>(OfficeImageAsset::getName);
            if (assets != null)
                copy.addAll(assets);
            doc.setAssets(copy);
            assets = copy;
        }
        OfficeImageAsset asset = new OfficeImageAsset();
        asset.setName(name);
        asset.setExt("png");
        asset.setData(ByteString.of(data));
        assets.add(asset);
        image.setAssetRef(name);
    }

    /**
     * docs whose assets list has already been materialized (identity semantics). Weak
     * keys: the binder may be a long-lived singleton and must not pin rendered documents.
     */
    private final Set<OfficeDocModel> materializedDocs = Collections
            .newSetFromMap(new java.util.WeakHashMap<>());
}
