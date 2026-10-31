<div align="center">

![HandyStudy banner](docs/hero.svg)

# HandyStudy · 手写伴学

**把手写练习、智能反馈和学习回顾放在同一张画布上。**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/xnbx2012/HandyStudy/releases/latest)
[![Release](https://img.shields.io/github/v/release/xnbx2012/HandyStudy?logo=github)](https://github.com/xnbx2012/HandyStudy/releases/latest)
[![Build](https://github.com/xnbx2012/HandyStudy/actions/workflows/release.yml/badge.svg)](https://github.com/xnbx2012/HandyStudy/actions/workflows/release.yml)
[![MIT](https://img.shields.io/badge/License-MIT-197d78)](LICENSE)

[下载 APK](https://github.com/xnbx2012/HandyStudy/releases/latest) · [English](README.en.md) · [问题反馈](https://github.com/xnbx2012/HandyStudy/issues)

</div>

## ✨ 功能一览

| ✍️ 自由书写 | 🧠 智能伴学 | 📚 学习管理 |
| :--- | :--- | :--- |
| 手写笔与键盘混合作答；支持压感、橡皮、撤销和缩放 | 多模态识别、评分、针对性解析与知识点整理 | 错题回顾、薄弱点、学习统计、自定义题库与 ZIP 导入 |

> AI 功能需要用户自行配置支持图片输入和 JSON 输出的模型服务与 API Key。Key 保存在设备本地，不随源码或 APK 分发。

## 📦 下载与安装

从 [Releases](https://github.com/xnbx2012/HandyStudy/releases/latest) 下载对应文件：

| 文件后缀 | 设备 |
| :--- | :--- |
| `android-universal.apk` | 通用 Android 8.0+，不含 vivo 原生预测库 |
| `android-arm64-v8a.apk` | ARM64 设备，包含 vivo/iQOO 手写笔增强库 |

安装后，在 **设置 → 智能伴学** 中添加自己的模型服务。公开版本内置原创示例题；其它题库可按 [题库格式](docs/BANK_FORMAT.md) 自行制作并导入。原始教材、题库扫描件和个人学习数据不属于本仓库或 Release。

## 🛠️ 从源码构建

需要 JDK 17+、Android SDK 36 与可访问 Google Maven、Maven Central、vivo Maven 的网络。

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleDebug -PstandardPenOnly
```

Windows 可运行 `powershell -ExecutionPolicy Bypass -File tools/build_apk.ps1`。APK 位于 `app/build/outputs/apk/debug/`。应用使用 Java、Android View、离线 KaTeX / Marked / DOMPurify。

## 🔐 隐私与开源范围

- API Key 由 Android Keystore 加密保存在设备本地；请勿把自己的配置、日志或导出文件提交到仓库。
- 公开仓库只包含应用源码、原创示例题库和构建所需资源；第三方教材及生成题库不在 MIT 授权范围内。
- Release 由 GitHub Actions 使用仓库私密签名配置生成；配置方式见 [发布说明](docs/RELEASING.md)。

## 🤝 参与

欢迎通过 [Issues](https://github.com/xnbx2012/HandyStudy/issues) 报告问题或提交 Pull Request。作者 **PeterZhong** · [xnbx2012@163.com](mailto:xnbx2012@163.com)。项目源码采用 [MIT License](LICENSE)；所附第三方库遵循各自许可证。
