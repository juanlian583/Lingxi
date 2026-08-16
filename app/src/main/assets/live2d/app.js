/* 灵汐 Live2D 渲染层：pixi-live2d-display(Cubism4) 驱动，60fps 骨骼动画
   支持两种模型：Haru（内置动作）与 Baixi 白兮（无内置动作，程序化动画）
   运行日志通过 console.log 上报给原生层（复制诊断日志功能） */
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
    dbgLine('目标模型: ' + MODEL);

    // ---------- 程序化参数动画（无内置动作的模型使用） ----------

    var tweens = [];

    function setParam(id, v) {
        try { model.internalModel.coreModel.setParameterValueById(id, v, 1.0); } catch (e) {}
    }

    function animParam(id, from, to, dur, delay) {
        if (!model) return;
        setParam(id, from);
        tweens.push({
            id: id, from: from, to: to,
            dur: dur || 500,
            start: Date.now() + (delay || 0)
        });
    }

    function initTweener() {
        if (!app || !model) return;
        app.ticker.add(function () {
            var now = Date.now();
            for (var i = tweens.length - 1; i >= 0; i--) {
                var t = tweens[i];
                if (now < t.start) continue;
                var p = Math.min(1, (now - t.start) / t.dur);
                var e = 1 - (1 - p) * (1 - p); // easeOut
                setParam(t.id, t.from + (t.to - t.from) * e);
                if (p >= 1) tweens.splice(i, 1);
            }
        });
    }

    function hasMotions() {
        try {
            var defs = model.internalModel.motionManager.definitions;
            return !!(defs && defs.length);
        } catch (e) { return false; }
    }

    // 歪头打招呼
    function procTap() {
        animParam('ParamAngleZ', 0, 18, 220);
        animParam('ParamAngleZ', 18, -10, 260, 240);
        animParam('ParamAngleZ', -10, 0, 220, 520);
        animParam('ParamBodyAngleZ', 0, 8, 220);
        animParam('ParamBodyAngleZ', 8, 0, 400, 300);
    }

    // 开心弹跳
    function procPat() {
        animParam('ParamBodyAngleZ', 0, -10, 150);
        animParam('ParamBodyAngleZ', -10, 12, 250, 160);
        animParam('ParamBodyAngleZ', 12, 0, 250, 420);
        animParam('ParamAngleZ', 0, 12, 150);
        animParam('ParamAngleZ', 12, -8, 250, 160);
        animParam('ParamAngleZ', -8, 0, 250, 420);
        animParam('ParamMouthOpenY', 0, 0.6, 120);
        animParam('ParamMouthOpenY', 0.6, 0, 300, 150);
    }

    // 歪头思考
    function procThink() {
        animParam('ParamAngleZ', 0, 14, 400);
        animParam('ParamEyeLOpen', 1, 0.6, 300);
    }

    // 点头 / 摇头
    function procReply(ok) {
        if (ok) {
            animParam('ParamAngleX', 0, 14, 180);
            animParam('ParamAngleX', 14, 0, 300, 200);
        } else {
            animParam('ParamAngleX', 0, -16, 150);
            animParam('ParamAngleX', -16, 16, 250, 150);
            animParam('ParamAngleX', 16, -12, 250, 400);
            animParam('ParamAngleX', -12, 0, 250, 650);
        }
    }

    // 待机轻摆
    function procIdleSway() {
        animParam('ParamAngleZ', 0, 6, 800);
        animParam('ParamAngleZ', 6, -6, 1600, 900);
        animParam('ParamAngleZ', -6, 0, 800, 2600);
    }

    // ---------- 加载与适配 ----------

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
            dbgLine('模型加载成功 ✅ 动作: ' + (hasMotions() ? '内置动作' : '程序化动画'));
            app.stage.addChild(model);
            fit();
            initTweener();
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
            if (hasMotions()) {
                safe(function () { model.motion('Idle'); });
            } else {
                procIdleSway();
            }
        }, 9000);
    }

    /* 暴露给原生的 API */
    window.Lingxi = {
        /* 拖拽中暂停渲染器，减少重绘、窗口移动更流畅 */
        setDragging: function (d) {
            try {
                if (app) {
                    if (d) app.ticker.stop();
                    else app.ticker.start();
                }
            } catch (e) { reportError('setDragging: ' + e.message); }
        },
        say: function (text) { showBubble(text, 5000); },
        showBubble: function (text, ms) { showBubble(text, ms || 4000); },
        hideBubble: function () {
            if (bubbleTimer) clearTimeout(bubbleTimer);
            if (bubbleEl) bubbleEl.style.display = 'none';
        },
        motion: function (name) { safe(function () { if (model) model.motion(name); }); },
        randomMotion: function () {
            if (!model || !ready) return;
            if (hasMotions()) safe(function () { model.motion('TapBody'); });
            else procTap();
        },
        pat: function () {
            if (!model || !ready) return;
            if (hasMotions()) {
                safe(function () {
                    model.motion('TapBody');
                    try { model.expression('F01'); } catch (e) {}
                });
            } else {
                procPat();
            }
        },
        think: function () {
            if (!model || !ready) return;
            if (hasMotions()) safe(function () { model.motion('Idle'); });
            else procThink();
        },
        reply: function (ok) {
            if (!model || !ready) return;
            if (hasMotions()) {
                safe(function () {
                    model.motion('TapBody');
                    try { model.expression(ok ? 'F03' : 'F08'); } catch (e) {}
                });
            } else {
                procReply(ok);
            }
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
