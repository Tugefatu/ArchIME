# ArchIME

![Build](https://github.com/Tugefatu/ArchIME/actions/workflows/build.yml/badge.svg)

**ArchIME** 是一款以 Arch Linux 为视觉灵感的键盘输入法项目。它以标准 QWERTY 全键盘为基础，计划提供微软双拼中文输入，并采用简洁的深色 / 浅色主题。

> **开发状态：早期原型。** Android 输入法界面和自动构建流程已建立；真正的中文输入引擎、手写识别、语音识别等功能仍在开发中。ArchIME 是独立项目，与 Arch Linux 官方没有隶属关系。

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

中文模式计划采用**微软双拼**方案。按键上的辅助提示用于帮助查看键位；最终键位规则和实际输入行为以集成后的 Rime 方案为准。

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
| Android | 原生输入法服务、QWERTY 键盘、主题与设置 | 已有可构建的早期原型 |
| Linux | Fcitx5 / Rime 输入方案 | 规划中 |
| 中文输入 | Rime + 微软双拼 | 尚未完成集成 |

## 当前开发状态

**已完成 / 已具备**
- Android 输入法服务原型。
- 标准 QWERTY 键盘界面。
- 深色 / 浅色主题基础适配。
- 顶部工具栏、底部功能区与剪贴板入口的界面布局。
- GitHub Actions 自动构建 Debug APK 的流程。

**开发中 / 尚未完成**
- 集成真正的 Rime 引擎和词库。
- 中文双拼的编码处理、候选词展示、选词上屏与完整输入链路。
- 手写识别和语音识别。
- Linux 端 Fcitx5 集成。
- Android 实机兼容性、布局细节和长时间输入测试。

**重要说明：** 当前 Debug APK 的构建成功只代表项目通过了自动编译，不代表中文双拼已经可用，也不代表已在所有 Android 设备上完成验证。手写与语音按钮目前属于界面入口，不应视为已实现的识别功能。

## 获取开发构建

目前可从 GitHub Actions 下载最近一次成功构建的 Debug APK：

1. 打开 [ArchIME Actions](https://github.com/Tugefatu/ArchIME/actions)。
2. 进入最新的成功构建记录。
3. 在页面下方的 **Artifacts** 区域下载 `ArchIME-debug-apk`。

Debug APK 仅用于开发测试。安装前请确认来源，并注意它尚未经过完整的中文输入和设备兼容性验收。

## 参与项目

欢迎通过 GitHub Issues 提交问题、布局建议或功能想法，也欢迎提交 Pull Request。

- 仓库地址：[Tugefatu/ArchIME](https://github.com/Tugefatu/ArchIME)
- 构建记录：[GitHub Actions](https://github.com/Tugefatu/ArchIME/actions)

## 项目目标

ArchIME 的目标是成为一款拥有 Arch Linux 风格、支持微软双拼、兼顾 Android 与 Linux 使用场景的轻量键盘输入法。

**简洁的外观、可靠的输入、清楚的状态说明。** 这是 ArchIME 希望坚持的方向。

---

<p align="center">Made with care for a cleaner typing experience.</p>
