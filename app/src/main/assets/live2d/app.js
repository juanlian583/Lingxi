/* 灵汐 Live2D 渲染层：pixi-live2d-display 驱动，60fps 骨骼动画 */
(function () {
    'use strict';

    var params = new URLSearchParams(location.search);
    var MODEL = params.get('model') || 'Haru/Haru.model3.json';

    var app = null, model = null, ready = false;
    var canvas = document.getElementById('canvas');
    var bubbleEl = document.getElementById('bubble');
    var bubbleTimer = null;
    var failed = false;

    function log(msg) {
        try { console.log('[lingxi] ' + msg); } catch (e) {}
    }

    function reportError(msg) {
        try {
            if (window.LingxiNative && window.LingxiNative.onError) {
                window.LingxiNative.onError(String(msg).slice(0, 300));
            }
        } catch (e) {}
        log('err: ' + msg);
    }

    function fit() {
        if (!model || !app) return;
        try {
            var w = window.innerWidth;
            var h = window.innerHeight;
            app.renderer.resize(w, h);
            var mw = (model.internalModel && model.internalModel.width) || 1024;
            var mh = (model.internalModel && model.internalModel.height) || 1024;
            var s = Math.min(w / mw, h / mh);
            model.scale.set(s);
            model.x = w / 2;
            model.y = h;
            model.anchor.set(0.5, 1.0);
        } catch (e) { reportError('fit: ' + e.message); }
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

    function showFail() {
        failed = true;
        if (bubbleEl) {
            bubbleEl.textContent = '😢 模型加载失败，请检查网络或模型地址';
            bubbleEl.style.display = 'block';
        }
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
                showFail();
                return;
            }
            if (PIXI.live2d.Live2DModel.registerTicker) {
                PIXI.live2d.Live2DModel.registerTicker(PIXI.Ticker);
            }
            model = await PIXI.live2d.Live2DModel.from(MODEL, { autoInteract: false });
            app.stage.addChild(model);
            fit();
            window.addEventListener('resize', fit);
            ready = true;
            log('模型加载完成: ' + MODEL);
            if (window.LingxiNative && window.LingxiNative.onReady) {
                safe(function () { window.LingxiNative.onReady(); });
            }
            scheduleIdle();
        } catch (e) {
            reportError('Live2D 启动失败: ' + (e && e.message));
            // 自定义模型加载失败时回退到内置模型
            if (MODEL !== 'Haru/Haru.model3.json') {
                MODEL = 'Haru/Haru.model3.json';
                log('回退到内置模型');
                try { if (app) { app.destroy(true); app = null; model = null; } } catch (e2) {}
                boot();
            } else {
                showFail();
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
