<div align="center">

![HandyStudy banner](docs/hero.svg)

# HandyStudy

**Handwriting, focused feedback, and review in one Android workspace.**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/xnbx2012/HandyStudy/releases/latest)
[![Release](https://img.shields.io/github/v/release/xnbx2012/HandyStudy?logo=github)](https://github.com/xnbx2012/HandyStudy/releases/latest)
[![Build](https://github.com/xnbx2012/HandyStudy/actions/workflows/release.yml/badge.svg)](https://github.com/xnbx2012/HandyStudy/actions/workflows/release.yml)
[![MIT](https://img.shields.io/badge/License-MIT-197d78)](LICENSE)

[Download](https://github.com/xnbx2012/HandyStudy/releases/latest) · [简体中文](README.md) · [Issues](https://github.com/xnbx2012/HandyStudy/issues)

</div>

## ✨ Features

| ✍️ Write | 🧠 Learn | 📚 Review |
| :--- | :--- | :--- |
| Stylus and typed answers, pressure, eraser, undo and zoom | Multimodal answer recognition, grading, tailored explanations and topic tags | Mistake review, weak areas, study statistics, custom questions and ZIP bank import |

> AI features require your own image-capable, JSON-capable model endpoint and API key. Keys are stored on your device and are never bundled in source or APKs.

## 📦 Install

Download an APK from [Releases](https://github.com/xnbx2012/HandyStudy/releases/latest):

| Asset suffix | Device |
| :--- | :--- |
| `android-universal.apk` | Android 8.0+, without the vivo native prediction library |
| `android-arm64-v8a.apk` | ARM64 devices, with vivo/iQOO stylus enhancement |

Configure your model under **Settings → AI companion**. The public app includes an original sample bank. You can create and import other banks using the [bank format](docs/BANK_FORMAT.md). Copyrighted textbooks, scanned question banks, and personal study data are excluded from this repository and releases.

## 🛠️ Build

Install JDK 17+, Android SDK 37 and dependencies from Google Maven, Maven Central and vivo Maven.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleDebug -PstandardPenOnly
```

On Windows, run `powershell -ExecutionPolicy Bypass -File tools/build_apk.ps1`. Output is under `app/build/outputs/apk/debug/`. The app uses Java, Android View, and offline KaTeX / Marked / DOMPurify.

## 🔐 Privacy and scope

- Android Keystore encrypts API keys locally. Never commit your configuration, logs or exports.
- This repository includes app source, an original sample bank and build resources. Third-party educational content is not covered by the MIT license.
- GitHub Actions signs release assets with repository secrets; see [release setup](docs/RELEASING.md).

## 🤝 Contributing

Issues and pull requests are welcome. Author: **PeterZhong** · [xnbx2012@163.com](mailto:xnbx2012@163.com). App source is [MIT licensed](LICENSE); bundled third-party libraries retain their own licenses.
