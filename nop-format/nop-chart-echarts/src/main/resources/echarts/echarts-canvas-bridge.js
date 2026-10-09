/**
 * echarts-canvas-bridge：GraalJS 裸环境生产 shim（plan 2362 D24）。
 * 绑定 Java2DCanvas（经 __bridge host）；ctx 属性写入翻译为 Java 方法调用；
 * 数据形态值一律以纯 JS 对象/数组跨越边界（WI0 interop 约束）。
 * 未实现的 Canvas2D 面显式抛错，不静默跳过。
 * 裸环境红线：不定义 window/document/navigator/self（保 env.node → animation:false 自动 flush）。
 */
(function (global) {
    'use strict';

    // dist dev 构建在无 window 时读裸 global（echarts 构造器 env.hasGlobalWindow ? window : global），
    // GraalJS 无 Node 宿主的 global 绑定，按 Node 语义自引用到 globalThis
    global.global = global;

    function fail(message) {
        throw new Error('echarts-canvas-bridge: ' + message);
    }

    // ----- 裸环境红线（setup 时断言）-----
    global.__bridgeAssertBareEnv = function () {
        var leaked = [];
        if (typeof window !== 'undefined') leaked.push('window');
        if (typeof document !== 'undefined') leaked.push('document');
        if (typeof navigator !== 'undefined') leaked.push('navigator');
        if (typeof self !== 'undefined') leaked.push('self');
        if (leaked.length)
            fail('bare env violated, defined: ' + leaked.join(','));
        return true;
    };

    global.console = {
        log: function () { __bridge.logInfo(fmt(arguments)); },
        warn: function () { __bridge.logWarn(fmt(arguments)); },
        error: function () { __bridge.logError(fmt(arguments)); },
        info: function () { __bridge.logInfo(fmt(arguments)); },
        debug: function () { __bridge.logInfo(fmt(arguments)); }
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
        return __bridge.scheduleTimer(fn, Number(delay) || 0);
    };
    global.clearTimeout = function (id) {
        __bridge.cancelTimer(id);
    };
    global.setInterval = function () {
        fail('setInterval must not be used in server static render');
    };
    global.clearInterval = function () {
        fail('clearInterval must not be used in server static render');
    };

    // ----- 画布与 context -----

    function requirePaintValue(value, kind) {
        if (typeof value === 'string')
            return { css: value };
        if (value && value.__grad)
            return { grad: value.__grad };
        if (value && value.__pattern)
            return { pattern: value.__pattern };
        fail('unsupported ' + kind + ' paint value');
    }

    function makeContext(canvas) {
        // D22（复核修订）：backend 可经异值 resize 换绑——所有方法惰性取当前 backend
        function B() {
            return canvas.__canvasBackend;
        }

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
            if (p.css) B().setFillStyleCss(p.css);
            else if (p.grad) B().setFillStyleGradient(p.grad);
            else B().setFillStylePattern(p.pattern);
        });
        styleProp('strokeStyle', function (v) {
            var p = requirePaintValue(v, 'strokeStyle');
            if (p.css) B().setStrokeStyleCss(p.css);
            else if (p.grad) B().setStrokeStyleGradient(p.grad);
            else B().setStrokeStylePattern(p.pattern);
        });
        styleProp('lineWidth', function (v) { B().setLineWidth(Number(v)); });
        styleProp('lineCap', function (v) { B().setLineCap(String(v)); });
        styleProp('lineJoin', function (v) { B().setLineJoin(String(v)); });
        styleProp('miterLimit', function (v) { B().setMiterLimit(Number(v)); });
        styleProp('lineDashOffset', function (v) { B().setLineDashOffset(Number(v)); });
        styleProp('globalAlpha', function (v) { B().setGlobalAlpha(Number(v)); });
        styleProp('font', function (v) { B().setFont(String(v)); });
        styleProp('textAlign', function (v) { B().setTextAlign(String(v)); });
        styleProp('textBaseline', function (v) { B().setTextBaseline(String(v)); });
        styleProp('shadowBlur', function (v) { B().setShadowBlur(Number(v)); });
        styleProp('shadowColor', function (v) { B().setShadowColor(String(v)); });
        styleProp('shadowOffsetX', function (v) { B().setShadowOffsetX(Number(v)); });
        styleProp('shadowOffsetY', function (v) { B().setShadowOffsetY(Number(v)); });
        styleProp('globalCompositeOperation', function (v) {
            if (String(v) !== 'source-over')
                fail('globalCompositeOperation "' + v + '" not supported (zrender does not use it)');
        });

        ctx.save = function () { B().save(); };
        ctx.restore = function () { B().restore(); };
        ctx.translate = function (x, y) { B().translate(Number(x), Number(y)); };
        ctx.scale = function (x, y) { B().scale(Number(x), Number(y)); };
        ctx.rotate = function (a) { B().rotate(Number(a)); };
        ctx.transform = function (a, b, c, d, e, f) {
            B().transform(Number(a), Number(b), Number(c), Number(d), Number(e), Number(f));
        };
        ctx.setTransform = function (a, b, c, d, e, f) {
            B().setTransform(Number(a), Number(b), Number(c), Number(d), Number(e), Number(f));
        };
        ctx.beginPath = function () { B().beginPath(); };
        ctx.closePath = function () { B().closePath(); };
        ctx.moveTo = function (x, y) { B().moveTo(Number(x), Number(y)); };
        ctx.lineTo = function (x, y) { B().lineTo(Number(x), Number(y)); };
        ctx.bezierCurveTo = function (c1x, c1y, c2x, c2y, x, y) {
            B().bezierCurveTo(Number(c1x), Number(c1y), Number(c2x), Number(c2y), Number(x), Number(y));
        };
        ctx.quadraticCurveTo = function (cx, cy, x, y) {
            B().quadraticCurveTo(Number(cx), Number(cy), Number(x), Number(y));
        };
        ctx.arc = function (x, y, r, a0, a1, ccw) {
            B().arc(Number(x), Number(y), Number(r), Number(a0), Number(a1), !!ccw);
        };
        ctx.arcTo = function (x1, y1, x2, y2, r) {
            B().arcTo(Number(x1), Number(y1), Number(x2), Number(y2), Number(r));
        };
        ctx.ellipse = function (x, y, rx, ry, rot, a0, a1, ccw) {
            B().ellipse(Number(x), Number(y), Number(rx), Number(ry), Number(rot),
                Number(a0), Number(a1), !!ccw);
        };
        ctx.rect = function (x, y, w, h) {
            B().rect(Number(x), Number(y), Number(w), Number(h));
        };
        ctx.fill = function (rule) {
            if (rule && rule !== 'nonzero')
                fail('fill rule "' + rule + '" not supported (zrender does not use it)');
            B().fill();
        };
        ctx.stroke = function () { B().stroke(); };
        ctx.clip = function () { B().clip(); };
        ctx.fillRect = function (x, y, w, h) {
            B().fillRect(Number(x), Number(y), Number(w), Number(h));
        };
        ctx.strokeRect = function (x, y, w, h) {
            B().strokeRect(Number(x), Number(y), Number(w), Number(h));
        };
        ctx.clearRect = function (x, y, w, h) {
            B().clearRect(Number(x), Number(y), Number(w), Number(h));
        };
        ctx.fillText = function (text, x, y) {
            B().fillText(text === null ? null : String(text), Number(x), Number(y));
        };
        ctx.strokeText = function (text, x, y) {
            B().strokeText(text === null ? null : String(text), Number(x), Number(y));
        };
        ctx.measureText = function (text) {
            var metrics = B().measureText(text === null ? null : String(text));
            // WI0 interop 约束：返回值必须是纯 JS 对象（Java 对象属性读不映射 getter）
            return { width: metrics.getWidth(), ascent: metrics.getAscent(), descent: metrics.getDescent() };
        };
        ctx.setLineDash = function (dash) {
            var arr = [];
            for (var i = 0; i < dash.length; i++)
                arr.push(Number(dash[i]));
            B().setLineDash(arr);
        };
        ctx.createLinearGradient = function (x0, y0, x1, y1) {
            return wrapGradient(B().createLinearGradient(Number(x0), Number(y0), Number(x1), Number(y1)));
        };
        ctx.createRadialGradient = function (x0, y0, r0, x1, y1, r1) {
            return wrapGradient(B().createRadialGradient(Number(x0), Number(y0), Number(r0),
                Number(x1), Number(y1), Number(r1)));
        };
        ctx.createPattern = function (source, repetition) {
            var image = resolveImage(source);
            return { __pattern: B().createPattern(image, repetition === undefined ? null : String(repetition)) };
        };
        ctx.drawImage = function (source) {
            var image = resolveImage(source);
            if (arguments.length === 3) {
                B().drawImage(image, Number(arguments[1]), Number(arguments[2]));
            } else if (arguments.length === 5) {
                B().drawImage(image, Number(arguments[1]), Number(arguments[2]),
                    Number(arguments[3]), Number(arguments[4]));
            } else if (arguments.length === 9) {
                B().drawImage(image, Number(arguments[1]), Number(arguments[2]),
                    Number(arguments[3]), Number(arguments[4]), Number(arguments[5]),
                    Number(arguments[6]), Number(arguments[7]), Number(arguments[8]));
            } else {
                fail('drawImage requires 3, 5 or 9 arguments, got ' + arguments.length);
            }
        };
        ctx.getImageData = function (x, y, w, h) {
            var data = B().getImageData(Number(x), Number(y), Number(w), Number(h));
            return { width: data.getWidth(), height: data.getHeight(), data: __bridge.imageDataToRgba(data) };
        };
        ctx.putImageData = function (imageData) {
            if (!imageData || !imageData.data)
                fail('putImageData requires an image data object');
            var dx = Number(arguments[1]) || 0;
            var dy = Number(arguments[2]) || 0;
            if (arguments.length >= 7) {
                fail('putImageData dirty-rect form not bridged (zrender heatmap uses the 3-arg form)');
            }
            __bridge.putImageDataRgba(B(), imageData.data,
                Number(imageData.width), Number(imageData.height), dx, dy);
        };
        return ctx;
    }

    function wrapGradient(javaGradient) {
        return {
            __grad: javaGradient,
            addColorStop: function (offset, color) {
                javaGradient.addColorStop(Number(offset), String(color));
            }
        };
    }

    function resolveImage(source) {
        if (source && source.__canvasBackend)
            return source.__canvasBackend.getImage();
        return source;
    }

    global.__bridgeCreateCanvas = function (width, height) {
        var canvas = { style: {} };
        var backend = __bridge.createCanvas(Number(width), Number(height));
        var ctxCache = null;
        Object.defineProperty(canvas, '__canvasBackend', {
            get: function () { return backend; }
        });
        // D22（复核修订）：异值赋值重建底层缓冲（浏览器 resize 清空语义）；root 路径恒同值
        Object.defineProperty(canvas, 'width', {
            get: function () { return backend.getWidth(); },
            set: function (v) { backend = __bridge.resizeCanvas(backend, Number(v), backend.getHeight()); }
        });
        Object.defineProperty(canvas, 'height', {
            get: function () { return backend.getHeight(); },
            set: function (v) { backend = __bridge.resizeCanvas(backend, backend.getWidth(), Number(v)); }
        });
        canvas.getContext = function (type) {
            if (String(type) !== '2d')
                fail('unsupported context type: ' + type);
            if (!ctxCache)
                ctxCache = makeContext(canvas);
            return ctxCache;
        };
        return canvas;
    };

    // ----- echarts 装配（render 脚本调用） -----

    global.__bridgeSetupPlatform = function () {
        global.__bridgeAssertBareEnv();
        echarts.setPlatformAPI({
            createCanvas: function () {
                return __bridgeCreateCanvas(300, 150);
            },
            loadImage: function (src) {
                __bridge.requestImage(String(src));
                return { src: String(src), onload: null, onerror: null, width: 0, height: 0 };
            },
            getTime: undefined
        });
    };

    global.__bridgeInitChart = function (width, height, renderer, option) {
        var root;
        if (renderer === 'svg') {
            root = null;
        } else {
            root = __bridgeCreateCanvas(width, height);
        }
        var opts = { renderer: renderer, width: width, height: height };
        if (renderer === 'svg')
            opts.ssr = true;
        var chart = echarts.init(root, null, opts);
        // 裸环境 canvas 路径与 SSR 路径统一强制静态终态（roadmap 红线：不做动画）
        option.animation = false;
        chart.setOption(option);
        global.__activeChart = chart;
        global.__activeRoot = root;
        return true;
    };
})(globalThis);
