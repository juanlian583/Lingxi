#!/usr/bin/env node
/* 灵汐 Live2D 渲染层回归测试：
   在模拟浏览器环境里真实执行 app.js，验证：
   1) 顶层无运行时错误（防 v1.6.7 式编辑事故）
   2) 模型加载路径走通
   3) 表情应用/恢复完整流程（防 hasMotions 缺括号式逻辑 bug）
   用法: node build-tools/test-live2d-js.js */
const fs = require('fs');
const path = require('path');
const code = fs.readFileSync(path.join(__dirname, '..', 'app/src/main/assets/live2d/app.js'), 'utf-8');

const el = { appendChild(){}, textContent:'', style:{}, scrollTop:0, scrollHeight:0,
  addEventListener(){}, clientWidth:170, clientHeight:184, getContext(){ return {}; } };
global.window = global;
global.addEventListener = () => {};
global.location = { href: 'http://127.0.0.1:1/index.html', search: '' };
global.document = { getElementById: () => el, addEventListener(){},
  documentElement: { clientWidth: 170, clientHeight: 184 } };
global.URLSearchParams = class { constructor(){ } get(){ return null; } };
global.Live2DCubismCore = {};
global.fetch = async () => ({ json: async () => ({ Parameters: [{ Id: 'Param40', Value: -1 }] }) });
const events = [];
const mockModel = {
  internalModel: {
    width: 1500, height: 1200,
    motionManager: {
      definitions: [],
      expressionManager: {
        definitions: [{ Name: 'eyeclose', File: 'eyeclose.exp3.json' }],
        resetExpression(){ events.push('resetExpression'); }
      }
    },
    coreModel: { setParameterValueById(id, v){ events.push('setParam:' + id + '=' + v); },
                 getParameterValueById(){ return 0; } }
  },
  scale: { set(){} }, anchor: { set(){} }, x: 0, y: 0,
  motion(){ events.push('motion'); }, expression(n){ events.push('expression:' + n); }
};
global.PIXI = {
  VERSION: '5.3.12', RENDERER_TYPE: { WEBGL: 1, CANVAS: 2 },
  Ticker: { shared: { add(){}, remove(){}, stop(){}, start(){} } },
  Application: class {
    constructor(){ this.renderer = { type: 1, resize(){} }; this.stage = { addChild(){} };
      this.ticker = { add(fn){ global.__tick = fn; }, remove(){}, stop(){}, start(){} }; }
  },
  live2d: { Live2DModel: { registerTicker(){}, from: async () => mockModel } }
};

let failed = false;
function fail(msg) { console.error('✗ ' + msg); failed = true; }

try {
  eval(code);
  console.log('✓ 顶层执行无错误');
} catch (e) {
  fail('顶层运行时错误: ' + e.message);
  process.exit(1);
}

setTimeout(() => {
  try {
    // 验证模型已就绪
    if (!global.Lingxi || !global.ready) { /* ready 是闭包变量，这里通过行为验证 */ }
    global.Lingxi.pat();
    console.log('✓ pat() 调用无异常');
    setTimeout(() => {
      // 等 fetch 微任务完成后驱动 ticker，让表情参数 tween 推进到目标值
      if (global.__tick) { for (let i = 0; i < 40; i++) global.__tick(); }
      const okApply = events.includes('expression:eyeclose');
      const okParam = events.includes('setParam:Param40=-1');
      const okReset = events.includes('resetExpression');
      okApply ? console.log('✓ 库 API 表情应用') : fail('表情库 API 应用缺失');
      okParam ? console.log('✓ 直接参数应用') : fail('表情参数应用缺失');
      okReset ? console.log('✓ 库级表情恢复') : fail('表情恢复缺失');
      if (failed) process.exit(1);
      console.log('✅ Live2D JS 回归测试全部通过');
      process.exit(0);
    }, 2200);
  } catch (e) {
    fail('反应调用异常: ' + e.message);
    process.exit(1);
  }
}, 200);
