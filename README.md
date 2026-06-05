# DanmakuPlayer

为了弹幕库（DanmakuFlow Engine）而做的播放器底板项目，目前正在开发中。

## 包结构

```
com.danmaku.flow/          ← 弹幕引擎（DanmakuFlow Engine）
├── model/                 # 数据模型
├── parser/                # 弹幕解析
├── engine/                # 时间轴与调度核心
├── renderer/              # 渲染层（CanvasRenderer 等）
├── bridge/
│   ├── api/               # 桥接接口（PlayerClockProvider, DanmakuRenderer 等）
│   └── mpv/               # mpv 适配实现（MpvClockBridge）
└── controller/            # 控制入口（DanmakuController）

com.danmakuplayer/         ← 播放器底板
├── manager/               # mpv 播放管理
├── overlay/               # 叠加层实现
├── ui/                    # Jetpack Compose UI
└── MainActivity / ViewModel
```
