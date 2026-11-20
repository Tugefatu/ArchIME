# ArchIME

![Build](https://github.com/Tugefatu/ArchIME/actions/workflows/build.yml/badge.svg)

**ArchIME** 是一款以 Arch Linux 为视觉灵感的键盘输入法项目。它以标准 QWERTY 全键盘为基础，集成 Rime 与微软双拼方案，并采用简洁的深色 / 浅色主题。

> **开发状态：Android 原生引擎集成版。** Android 键盘界面、ARM64 Rime 原生引擎、微软双拼方案及词典已进入自动构建流程。首次启动部署、真实中文输入和 Redmi K80 实机兼容性仍待验证。手写识别、语音识别和 Linux 端仍在开发中。ArchIME 是独立项目，与 Arch Linux 官方没有隶属关系。

## 设计理念

ArchIME 希望做到三件事：

- **熟悉**：使用标准 26 键 QWERTY 布局，尽量不改变常用键位习惯。
- **清晰**：按键采用圆角矩形设计，间距规整，文字清楚，不添加多余装饰。
- **统一**：保留 Arch Linux 标志，其余功能图标保持简洁；深色与浅色主题使用一致的布局。

## 键盘布局

| 区域 | 设计 |
| --- | --- |
| 顶部功能栏 | Arch Linux 标志、Aa 大小写、手写入口、麦克风入口、收起键盘 |
| 第一行 | Q W E R T Y U I O P；数字 1–0 显示在对应字母键上方 |
| 第二行 | A S D F G H J K L；常用符号显示在对应字母键上方 |
| 第三行 | Shift、Z X C V B N M、退格键 |
| 底部功能行 | 123、逗号、语音入口、「双/英」模式切换、回车 |
| 最底部 | 键盘管理设置、剪贴板入口 |

中文输入使用 Rime 的 **Microsoft Shuangpin（微软双拼）** 方案 `double_pinyin_mspy`，并提供明月拼音词典及笔画反查数据。候选词、按键处理和上屏逻辑通过 ArchIME 自己的 JNI 桥接层连接 Rime。

## 视觉主题

### 深色主题
- 纯黑色键盘背景。
- 深灰色圆角按键、浅色文字。
- 适合低亮度环境使用。

### 浅色主题
- 浅灰色键盘背景。
- 白色圆角按键、深色文字。
- 保持干净、轻量的视觉效果。

主题界面目前按 Android 系统的深浅色配置切换。不同设备上的显示效果仍需实机检查。

## 平台计划

| 平台 | 计划方向 | 当前状态 |
| --- | --- | --- |
| Android | 原生输入法服务、QWERTY 键盘、主题与设置 | 可构建 ARM64 Debug APK |
| Linux | Fcitx5 / Rime 输入方案 | 规划中 |
| 中文输入 | Rime + 微软双拼 | 原生引擎和方案已打包；实机输入待验收 |

## 当前开发状态

**已完成 / 已具备**
- Android 输入法服务原型与标准 QWERTY 键盘界面。
- 深色 / 浅色主题基础适配。
- 顶部工具栏、底部功能区、候选词显示与剪贴板入口的界面布局。
- Android ARM64 的 Rime 原生静态引擎构建，并通过 ArchIME JNI 桥接层提供接口。
- 微软双拼方案、明月拼音词典、笔画词典和基础 Rime 配置打包。
- GitHub Actions 自动构建 Debug APK，并检查 ARM64 原生库和关键方案文件。
- 移除未使用的重复 JNI 共享库；当前 APK 体积约 21 MB（Debug 构建）。

**开发中 / 尚未完成**
- 在 Redmi K80 等真实 Android 设备上验证首次启动部署、Rime 初始化、微软双拼编码、候选词和选词上屏。
- 手写识别和语音识别。
- Linux 端 Fcitx5 集成。
- Android 实机兼容性、布局细节和长时间输入测试。

**重要说明：** APK 内已包含 ARM64 Rime 引擎及微软双拼方案数据，且原生库通过 ELF 架构和 JNI 符号检查；但这不等于已经在手机上完成运行时验收。若设备上 Rime 初始化失败，键盘会回退为英文输入，避免吞掉按键。手写与语音按钮目前属于界面入口，不应视为已实现的识别功能。

## 获取开发构建

目前可从 GitHub Actions 下载最近一次成功构建的 Debug APK：

1. 打开 [ArchIME Actions](https://github.com/Tugefatu/ArchIME/actions)。
2. 进入最新的成功构建记录。
3. 在页面下方的 **Artifacts** 区域下载 `ArchIME-debug-apk`。

Debug APK 仅用于开发测试。安装前请确认来源，并注意它尚未经过完整的设备兼容性验收。

## 参与项目

欢迎通过 GitHub Issues 提交问题、布局建议或功能想法，也欢迎提交 Pull Request。

- 仓库地址：[Tugefatu/ArchIME](https://github.com/Tugefatu/ArchIME)
- 构建记录：[GitHub Actions](https://github.com/Tugefatu/ArchIME/actions)

## 项目目标

ArchIME 的目标是成为一款拥有 Arch Linux 风格、支持微软双拼、兼顾 Android 与 Linux 使用场景的轻量键盘输入法。

**简洁的外观、可靠的输入、清楚的状态说明。** 这是 ArchIME 希望坚持的方向。

---

<p align="center">Made with care for a cleaner typing experience.</p>
