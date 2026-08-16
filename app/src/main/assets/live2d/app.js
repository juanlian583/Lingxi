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

    var paramValues = {};

    function setParam(id, v) {
        paramValues[id] = v;
        try { model.internalModel.coreModel.setParameterValueById(id, v, 1.0); } catch (e) {}
    }

    function getParam(id, fallback) {
        return (id in paramValues) ? paramValues[id] : fallback;
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

    /* 表情定义（正确的库路径：internalModel.motionManager.expressionManager） */
    function getExpressionDefs() {
        try {
            var em = model.internalModel.motionManager.expressionManager;
            return (em && em.definitions) || null;
        } catch (e) { return null; }
    }

    function findExpressionFile(nameLike, defs) {
        if (defs) {
            for (var i = 0; i < defs.length; i++) {
                var n = ((defs[i].Name || '') + ' ' + (defs[i].File || ''));
                if (n.indexOf(nameLike) >= 0) return defs[i].File;
            }
        }
        // NOIR 内置表情兜底
        if (MODEL.toLowerCase().indexOf('noir') >= 0) {
            var map = { eyeclose: 'eyeclose.exp3.json', quanquan: 'quanquan.exp3.json',
                        tears: 'tears.exp3.json', white: 'white.exp3.json' };
            return map[nameLike] || null;
        }
        return null;
    }

    function getParamValue(id, fallback) {
        try { return model.internalModel.coreModel.getParameterValueById(id); }
        catch (e) { return fallback; }
    }

    /* 完全自主的表情管理：读当前值 -> tween 到表情值 -> 保持 -> tween 回原值（回归待机表情） */
    var exprResetTimer = null;
    var activeExprParams = null;

    function applyExpression(nameLike, holdMs) {
        if (!model) return;
        var defs = getExpressionDefs();
        var file = findExpressionFile(nameLike, defs);
        if (!file) return;
        var base = MODEL.indexOf('/') >= 0 ? MODEL.substring(0, MODEL.lastIndexOf('/')) : '';
        fetch((base ? base + '/' : '') + file).then(function (r) { return r.json(); })
            .then(function (exp) {
                try {
                    var params = (exp.Parameters || []).filter(function (p) {
                        return p.Id && p.Value !== undefined;
                    });
                    if (!params.length) return;
                    if (exprResetTimer) clearTimeout(exprResetTimer);
                    // 记录初始值，tween 到表情值
                    var list = [];
                    params.forEach(function (p) {
                        var init = getParamValue(p.Id, 0);
                        list.push({ id: p.Id, initial: init });
                        animParam(p.Id, init, p.Value, 250);
                    });
                    activeExprParams = list;
                    dbgLine('表情(自主): ' + file + ' -> ' + params.length + ' 个参数');
                    // 保持后 tween 回初始值（回归待机表情）
                    exprResetTimer = setTimeout(function () {
                        var l = activeExprParams;
                        activeExprParams = null;
                        if (l && l.length) {
                            l.forEach(function (e) {
                                animParam(e.id, getParamValue(e.id, e.initial), e.initial, 350);
                            });
                            dbgLine('表情已回归待机: ' + l.length + ' 个参数');
                        }
                    }, holdMs || 1600);
                } catch (e2) { reportError('表情应用异常: ' + e2.message); }
            })
            .catch(function (e3) { dbgLine('表情文件加载失败: ' + e3.message); });
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
            applyExpression(hasMotions ? 'F01' : 'quanquan', 1600);
            scheduleReturnToIdle(2500);
        },
        pat: function () {
            if (!model || !ready) return;
            if (hasMotions()) safe(function () { model.motion('TapBody'); });
            else procPat();
            applyExpression(hasMotions ? 'F01' : 'eyeclose', 1600);
            scheduleReturnToIdle(2500);
        },
        think: function () {
            if (!model || !ready) return;
            if (hasMotions()) safe(function () { model.motion('Idle'); });
            else procThink();
            applyExpression('quanquan', 4500);
            scheduleReturnToIdle(6000);
        },
        reply: function (ok) {
            if (!model || !ready) return;
            if (hasMotions()) safe(function () { model.motion('TapBody'); });
            else procReply(ok);
            applyExpression(hasMotions ? (ok ? 'F03' : 'F08') : (ok ? 'eyeclose' : 'tears'), 1600);
            scheduleReturnToIdle(3000);
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
