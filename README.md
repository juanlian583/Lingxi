# 灵汐 Lingxi 🐳

**桌面 AI 桌宠 · DeepSeek娘**

一个可以在桌面上互动的虚拟形象桌宠 Android 应用：蓝发鲸鱼女仆「灵汐」会显示在所有应用和桌面之上，单击互动、双击摸头、拖拽移动，还能接入 AI 陪你聊天。

- 应用名：**灵汐**
- 包名：`com.lingxi.pet`
- 最低系统：Android 8.0（API 26）
- 无第三方依赖，纯 Android 框架实现，APK 约 3 MB

## ✨ 功能

| 功能 | 说明 |
| --- | --- |
| 🖥️ 桌面悬浮窗 | 前台服务 + 系统悬浮窗，显示在所有应用之上，可随意拖拽 |
| 👆 单击互动 | 随机挥手 / 跳跃 / 点头，并弹出气泡说话 |
| 💗 双击摸头 | 播放跳跃动画 + 爱心特效 + 语音撒娇 |
| 🐳 AI 对话 | OpenAI 兼容接口（默认 DeepSeek API），悬浮窗内直接聊天，回复带气泡 + 语音朗读 |
| 🎨 形象资源联网下载 | 默认从 GitHub 下载 DeepSeek娘 精灵图资源，支持自定义 URL 换形象 |
| 📏 可调大小 | 100~300dp 滑块调节 |
| 🔔 开机自启 | 开机后自动把灵汐放回桌面 |

## 🎭 形象资源

默认形象是社区二创的 **DeepSeek娘（蓝发鲸鱼女仆）** 精灵图，来自开源同人项目 [xpy12367/codex-pet-DeepSeek-girl](https://github.com/xpy12367/codex-pet-DeepSeek-girl)（1536×2288，8×11 精灵图，9 组动画 + 16 方向注视）。

- 首次联网启动时可在应用内点击「下载/更新形象」从网上拉取最新资源；
- 应用同时内置了一份离线备份，无网络也能正常使用；
- 也支持把形象资源 URL 换成任意符合 `spritesheet.webp` 格式的地址。

> 本应用为 DeepSeek 的**非官方同人项目**，与 DeepSeek 官方无隶属或背书关系。

## 🤖 AI 接入

在「AI 设置」中填写：

- **接口地址**：默认 `https://api.deepseek.com`（兼容任意 OpenAI 格式接口）
- **API Key**：DeepSeek 开放平台申请
- **模型**：默认 `deepseek-chat`
- **人设提示词**：默认蓝发鲸鱼女仆人设，可自由修改

不填 API Key 也能玩：灵汐会离线卖萌应答。

## 📦 构建 APK

### 方式一：直接用发布包

`dist/lingxi-v1.0.apk` 是已签名可直接安装的 APK（侧载安装，开启「允许安装未知来源」）。

### 方式二：Android Studio

1. 用 Android Studio 打开本项目根目录；
2. 等待 Gradle 同步完成，点 Run ▶ 即可。

### 方式三：命令行（本项目自带脚本）

需要：JDK 17、Android SDK（platform 34 + build-tools 34）、aarch64 或 x86_64 的 `aapt2`。

```bash
./build-tools/build.sh
# 产物：dist/lingxi-v1.0.apk
```

## 🗂️ 项目结构

```
app/src/main/
├── AndroidManifest.xml
├── assets/               # 内置 DeepSeek娘 精灵图
├── java/com/lingxi/pet/
│   ├── MainActivity.java     # 主界面：预览/开关/AI配置/资源管理
│   ├── PetService.java       # 悬浮窗前台服务
│   ├── PetOverlayView.java   # 悬浮窗视图（拖拽）
│   ├── PetStage.java         # 舞台：角色+气泡
│   ├── PetView.java          # 角色视图（手势/爱心）
│   ├── PetAnimator.java      # 精灵图逐帧动画引擎
│   ├── PetResources.java     # 资源下载/缓存
│   ├── PetConfig.java        # 配置中心
│   ├── AiClient.java         # AI 对话客户端
│   ├── ChatDialog.java       # 悬浮聊天窗
│   ├── SpeechHelper.java     # TTS 语音
│   └── BootReceiver.java     # 开机自启
└── res/                  # 布局/主题/图标
```

## 📄 许可

- 应用代码：MIT License（见 `LICENSE`）
- 形象资源：来自 [codex-pet-DeepSeek-girl](https://github.com/xpy12367/codex-pet-DeepSeek-girl) 同人项目，仅供个人娱乐使用
