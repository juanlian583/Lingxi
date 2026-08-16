/* 灵汐 Live2D 渲染层：pixi-live2d-display(Cubism4) 驱动，60fps 骨骼动画
   运行日志通过 console.log 上报给原生层（复制诊断日志功能），不在页面显示 */
(function () {
    'use strict';

    var params = new URLSearchParams(location.search);
    var MODEL = params.get('model') || 'Haru/Haru.model3.json';

    var app = null, model = null, ready = false;
    var canvas = document.getElementById('canvas');
    var bubbleEl = document.getElementById('bubble');
    var bubbleTimer = null;

    function dbgLine(msg) {
        try { console.log('[lingxi] ' + msg); } catch (e) {}
    }

    function reportError(msg) {
        dbgLine('❌ ' + msg);
        try {
            if (window.LingxiNative && window.LingxiNative.onError) {
                window.LingxiNative.onError(String(msg).slice(0, 300));
            }
        } catch (e) {}
    }

    dbgLine('页面已加载: ' + location.href);
    dbgLine('视口尺寸: ' +
            Math.max(document.documentElement.clientWidth, window.innerWidth) + 'x' +
            Math.max(document.documentElement.clientHeight, window.innerHeight) +
            ' dpr=' + (window.devicePixelRatio || 1));
    dbgLine('pixi: ' + (window.PIXI ? PIXI.VERSION : '未加载 ❌'));
    dbgLine('live2d-display: ' +
            (window.PIXI && PIXI.live2d && PIXI.live2d.Live2DModel ? '已加载 ✅' : '未加载 ❌'));
    dbgLine('cubism core: ' + (window.Live2DCubismCore ? '已加载 ✅' : '未加载 ❌'));
    dbgLine('目标模型: ' + MODEL);

    // 显式测试模型文件能否拉取（诊断用）
    try {
        fetch(MODEL).then(function (r) {
            dbgLine('fetch 模型 -> HTTP ' + r.status);
            return r.text().then(function (txt) {
                dbgLine('model3.json 字节数: ' + txt.length +
                        (txt.indexOf('FileReferences') >= 0 ? '（格式正确）' : '（格式异常!）'));
            });
        }).catch(function (e) {
            dbgLine('fetch 模型失败 ❌: ' + e.message);
        });
    } catch (e) {
        dbgLine('fetch 调用异常: ' + e.message);
    }

    function fit() {
        if (!model || !app) return;
        try {
            var w = Math.max(document.documentElement.clientWidth, window.innerWidth) || 1;
            var h = Math.max(document.documentElement.clientHeight, window.innerHeight) || 1;
            app.renderer.resize(w, h);
            var mw = (model.internalModel && model.internalModel.width) || 1024;
            var mh = (model.internalModel && model.internalModel.height) || 1024;
            var s = Math.min(w / mw, h / mh);
            model.scale.set(s);
            model.x = w / 2;
            model.y = h;
            model.anchor.set(0.5, 1.0);
            dbgLine('适配: 模型 ' + mw + 'x' + mh + ' scale=' + s.toFixed(3));
        } catch (e) { reportError('fit 异常: ' + e.message); }
    }

    function safe(fn) {
        try { fn(); } catch (e) { reportError(e.message); }
    }

    function showBubble(text, ms) {
        if (!bubbleEl) return;
        bubbleEl.textContent = text || '';
        bubbleEl.style.display = 'block';
        if (bubbleTimer) clearTimeout(bubbleTimer);
        bubbleTimer = setTimeout(function () { bubbleEl.style.display = 'none'; }, ms || 4000);
    }

    async function boot() {
        try {
            app = new PIXI.Application({
                view: canvas,
                transparent: true,
                backgroundAlpha: 0,
                antialias: true,
                autoDensity: true,
                resolution: window.devicePixelRatio || 1
            });
            if (!PIXI.live2d || !PIXI.live2d.Live2DModel) {
                reportError('pixi-live2d-display 未加载');
                return;
            }
            if (PIXI.live2d.Live2DModel.registerTicker) {
                PIXI.live2d.Live2DModel.registerTicker(PIXI.Ticker);
            }
            dbgLine('开始加载模型: ' + MODEL);
            model = await PIXI.live2d.Live2DModel.from(MODEL, { autoInteract: false });
            dbgLine('模型加载成功 ✅');
            app.stage.addChild(model);
            fit();
            // 视口尺寸可能晚于模型就绪，前 10 秒持续校准
            var n = 0;
            var fitTimer = setInterval(function () {
                if (++n > 20) { clearInterval(fitTimer); return; }
                fit();
            }, 500);
            window.addEventListener('resize', fit);
            ready = true;
            dbgLine('灵汐 Live2D 就绪 ✅');
            if (window.LingxiNative && window.LingxiNative.onReady) {
                safe(function () { window.LingxiNative.onReady(); });
            }
            scheduleIdle();
        } catch (e) {
            reportError('Live2D 启动失败: ' + (e && e.message));
            if (MODEL !== 'Haru/Haru.model3.json') {
                dbgLine('回退到内置模型…');
                MODEL = 'Haru/Haru.model3.json';
                try { if (app) { app.destroy(true); app = null; model = null; } } catch (e2) {}
                boot();
            } else {
                showBubble('😢 模型加载失败，请复制诊断日志反馈', 60000);
            }
        }
    }

    function scheduleIdle() {
        setInterval(function () {
            if (!model || !ready) return;
            safe(function () { model.motion('Idle'); });
        }, 9000);
    }

    /* 暴露给原生的 API */
    window.Lingxi = {
        say: function (text) { showBubble(text, 5000); },
        showBubble: function (text, ms) { showBubble(text, ms || 4000); },
        hideBubble: function () {
            if (bubbleTimer) clearTimeout(bubbleTimer);
            if (bubbleEl) bubbleEl.style.display = 'none';
        },
        motion: function (name) { safe(function () { if (model) model.motion(name); }); },
        randomMotion: function () { safe(function () { if (model) model.motion('TapBody'); }); },
        pat: function () {
            safe(function () {
                if (model) {
                    model.motion('TapBody');
                    try { model.expression('F01'); } catch (e) {}
                }
            });
        },
        think: function () { safe(function () { if (model) model.motion('Idle'); }); },
        reply: function (ok) {
            safe(function () {
                if (model) {
                    model.motion('TapBody');
                    try { model.expression(ok ? 'F03' : 'F08'); } catch (e) {}
                }
            });
        }
    };

    /* 阻止 WebView 处理触摸，手势全部交给原生层 */
    document.addEventListener('touchstart', function (e) { e.preventDefault(); }, { passive: false });
    document.addEventListener('touchmove', function (e) { e.preventDefault(); }, { passive: false });
    document.addEventListener('touchend', function (e) { e.preventDefault(); }, { passive: false });
    document.addEventListener('touchcancel', function (e) { e.preventDefault(); }, { passive: false });

    if (bubbleEl) {
        bubbleEl.addEventListener('click', function () {
            if (window.LingxiNative && window.LingxiNative.onBubbleClick) {
                safe(function () { window.LingxiNative.onBubbleClick(); });
            }
        });
    }

    boot();
})();
