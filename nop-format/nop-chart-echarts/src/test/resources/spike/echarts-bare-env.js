/**
 * WI0 spike 裸环境 shim：为 dist/echarts.js 提供最小宿主环境。
 * 红线：不定义 window/document/navigator/self（保持 env.node=true → animation:false 且 setOption 自动 flush）。
 * 未实现的 Canvas2D 操作显式抛错，不静默跳过。
 */
(function (global) {
    'use strict';

    // dist dev 构建在无 window 时读裸 global（echarts 构造器 env.hasGlobalWindow ? window : global），
    // GraalJS 无 Node 宿主的 global 绑定，按 Node 语义自引用到 globalThis
    global.global = global;

    global.__spikeCanvases = [];

    global.console = {
        log: function () { __hostsConsole.log(fmt(arguments)); },
        warn: function () { __hostsConsole.warn(fmt(arguments)); },
        error: function () { __hostsConsole.error(fmt(arguments)); },
        info: function () { __hostsConsole.info(fmt(arguments)); },
        debug: function () { __hostsConsole.log(fmt(arguments)); }
    };

    function fmt(args) {
        var parts = [];
        for (var i = 0; i < args.length; i++) {
            var v = args[i];
            parts.push(typeof v === 'object' && v !== null ? JSON.stringify(v) : String(v));
        }
        return parts.join(' ');
    }

    global.setTimeout = function (fn, delay) {
        return __hostsTimer.schedule(fn, Number(delay) || 0);
    };

    global.clearTimeout = function (id) {
        __hostsTimer.cancel(id);
    };

    global.setInterval = function () {
        throw new Error('spike bare env: setInterval must not be used in server static render');
    };

    global.clearInterval = function () {
        throw new Error('spike bare env: clearInterval must not be used in server static render');
    };

    function wrapGradient(javaGradient) {
        return {
            __grad: javaGradient,
            addColorStop: function (offset, color) {
                javaGradient.addColorStop(Number(offset), String(color));
            }
        };
    }

    function requirePaintValue(value, kind) {
        if (typeof value === 'string')
            return { str: value };
        if (value && value.__grad)
            return { grad: value.__grad };
        throw new Error('spike: unsupported ' + kind + ' paint value: ' + value);
    }

    function makeContext(backend) {
        var ctx = {};
        var state = {
            fillStyle: '#000000',
            strokeStyle: '#000000',
            lineWidth: 1,
            lineCap: 'butt',
            lineJoin: 'miter',
            miterLimit: 10,
            lineDashOffset: 0,
            globalAlpha: 1,
            font: '10px sans-serif',
            textAlign: 'start',
            textBaseline: 'alphabetic',
            shadowBlur: 0,
            shadowColor: 'rgba(0,0,0,0)',
            shadowOffsetX: 0,
            shadowOffsetY: 0,
            globalCompositeOperation: 'source-over'
        };

        function styleProp(name, setter) {
            Object.defineProperty(ctx, name, {
                get: function () {
                    return state[name];
                },
                set: function (v) {
                    state[name] = v;
                    setter(v);
                }
            });
        }

        styleProp('fillStyle', function (v) {
            var p = requirePaintValue(v, 'fillStyle');
            if (p.str) backend.setFillStyle(p.str);
            else backend.setFillStyleGradient(p.grad);
        });
        styleProp('strokeStyle', function (v) {
            var p = requirePaintValue(v, 'strokeStyle');
            if (p.str) backend.setStrokeStyle(p.str);
            else backend.setStrokeStyleGradient(p.grad);
        });
        styleProp('lineWidth', function (v) { backend.setLineWidth(Number(v)); });
        styleProp('lineCap', function (v) { backend.setLineCap(String(v)); });
        styleProp('lineJoin', function (v) { backend.setLineJoin(String(v)); });
        styleProp('miterLimit', function (v) { backend.setMiterLimit(Number(v)); });
        styleProp('lineDashOffset', function (v) { backend.setLineDashOffset(Number(v)); });
        styleProp('globalAlpha', function (v) { backend.setGlobalAlpha(Number(v)); });
        styleProp('font', function (v) { backend.setFont(String(v)); });
        styleProp('textAlign', function (v) { backend.setTextAlign(String(v)); });
        styleProp('textBaseline', function (v) { backend.setTextBaseline(String(v)); });
        styleProp('shadowBlur', function (v) {
            if (Number(v)) throw new Error('spike: shadowBlur not implemented in WI0 PoC, got: ' + v);
        });
        styleProp('shadowColor', function () {
            // 颜色单独出现无效果；真实阴影由 shadowBlur 抛错拦截
        });
        styleProp('shadowOffsetX', function (v) {
            if (Number(v)) throw new Error('spike: shadowOffsetX not implemented in WI0 PoC, got: ' + v);
        });
        styleProp('shadowOffsetY', function (v) {
            if (Number(v)) throw new Error('spike: shadowOffsetY not implemented in WI0 PoC, got: ' + v);
        });
        styleProp('globalCompositeOperation', function (v) {
            if (String(v) !== 'source-over')
                throw new Error('spike: globalCompositeOperation not implemented in WI0 PoC, got: ' + v);
        });

        ctx.save = function () { backend.save(); };
        ctx.restore = function () { backend.restore(); };
        ctx.translate = function (x, y) { backend.translate(Number(x), Number(y)); };
        ctx.scale = function (x, y) { backend.scale(Number(x), Number(y)); };
        ctx.rotate = function (a) { backend.rotate(Number(a)); };
        ctx.transform = function (a, b, c, d, e, f) {
            backend.transform(Number(a), Number(b), Number(c), Number(d), Number(e), Number(f));
        };
        ctx.setTransform = function (a, b, c, d, e, f) {
            backend.setTransform(Number(a), Number(b), Number(c), Number(d), Number(e), Number(f));
        };
        ctx.beginPath = function () { backend.beginPath(); };
        ctx.closePath = function () { backend.closePath(); };
        ctx.moveTo = function (x, y) { backend.moveTo(Number(x), Number(y)); };
        ctx.lineTo = function (x, y) { backend.lineTo(Number(x), Number(y)); };
        ctx.bezierCurveTo = function (c1x, c1y, c2x, c2y, x, y) {
            backend.bezierCurveTo(Number(c1x), Number(c1y), Number(c2x), Number(c2y), Number(x), Number(y));
        };
        ctx.quadraticCurveTo = function (cx, cy, x, y) {
            backend.quadraticCurveTo(Number(cx), Number(cy), Number(x), Number(y));
        };
        ctx.arc = function (x, y, r, a0, a1, ccw) {
            backend.arc(Number(x), Number(y), Number(r), Number(a0), Number(a1), !!ccw);
        };
        ctx.arcTo = function () {
            throw new Error('spike: arcTo not implemented in WI0 PoC');
        };
        ctx.ellipse = function () {
            throw new Error('spike: ellipse not implemented in WI0 PoC');
        };
        ctx.rect = function (x, y, w, h) {
            backend.rect(Number(x), Number(y), Number(w), Number(h));
        };
        ctx.fill = function (rule) {
            if (rule && rule !== 'nonzero')
                throw new Error('spike: fill rule "' + rule + '" not implemented in WI0 PoC');
            backend.fill();
        };
        ctx.stroke = function () { backend.stroke(); };
        ctx.clip = function () { backend.clip(); };
        ctx.fillRect = function (x, y, w, h) {
            backend.fillRect(Number(x), Number(y), Number(w), Number(h));
        };
        ctx.strokeRect = function (x, y, w, h) {
            backend.strokeRect(Number(x), Number(y), Number(w), Number(h));
        };
        ctx.clearRect = function (x, y, w, h) {
            backend.clearRect(Number(x), Number(y), Number(w), Number(h));
        };
        ctx.fillText = function (text, x, y) {
            backend.fillText(String(text), Number(x), Number(y));
        };
        ctx.strokeText = function (text, x, y) {
            backend.strokeText(String(text), Number(x), Number(y));
        };
        ctx.measureText = function (text) {
            // 必须包一层纯 JS 对象：zrender 对返回值直接读 .width 属性，
            // GraalJS interop 对 Java 对象的属性读取不保证映射 getter
            var metrics = backend.measureText(String(text));
            return { width: metrics.getWidth() };
        };
        ctx.setLineDash = function (dash) {
            var arr = [];
            for (var i = 0; i < dash.length; i++)
                arr.push(Number(dash[i]));
            backend.setLineDash(arr);
        };
        ctx.createLinearGradient = function (x0, y0, x1, y1) {
            return wrapGradient(backend.createLinearGradient(Number(x0), Number(y0), Number(x1), Number(y1)));
        };
        ctx.createRadialGradient = function (x0, y0, r0, x1, y1, r1) {
            return wrapGradient(backend.createRadialGradient(Number(x0), Number(y0), Number(r0),
                Number(x1), Number(y1), Number(r1)));
        };
        ctx.drawImage = function () {
            throw new Error('spike: drawImage not implemented in WI0 PoC');
        };
        ctx.getImageData = function () {
            throw new Error('spike: getImageData not implemented in WI0 PoC');
        };
        ctx.putImageData = function () {
            throw new Error('spike: putImageData not implemented in WI0 PoC');
        };
        ctx.createPattern = function () {
            throw new Error('spike: createPattern not implemented in WI0 PoC');
        };
        return ctx;
    }

    global.__spikeCreateCanvas = function (width, height) {
        var backend = __hostsBackendFactory.create(Number(width), Number(height));
        var ctxCache = null;
        var canvas = {};
        Object.defineProperty(canvas, 'width', {
            get: function () { return backend.getWidth(); },
            set: function (v) { backend.resize(Number(v), backend.getHeight()); }
        });
        Object.defineProperty(canvas, 'height', {
            get: function () { return backend.getHeight(); },
            set: function (v) { backend.resize(backend.getWidth(), Number(v)); }
        });
        canvas.getContext = function (type) {
            if (String(type) !== '2d')
                throw new Error('spike: unsupported context type: ' + type);
            if (!ctxCache)
                ctxCache = makeContext(backend);
            return ctxCache;
        };
        canvas.style = {};
        global.__spikeCanvases.push(canvas);
        return canvas;
    };

    global.__spikeLoadImage = function (src, onload, onerror) {
        __hostsImage.request(String(src));
        return {
            src: String(src),
            onload: onload || null,
            onerror: onerror || null,
            width: 0,
            height: 0,
            complete: false
        };
    };
})(globalThis);
