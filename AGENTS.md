# HandyStudy 开发指南（供各类 Agent 使用）

本文件适用于整个仓库。开始工作前先阅读本文件，再阅读任务涉及的源码和 `docs/` 中的专题文档。用户当前指令优先于本指南；遇到事实与文档不符时，以代码和可复现的运行结果为准，并同步修正文档。

## 项目定位与基本约束

HandyStudy（包名 `com.handymath.app`）是 Android 手写学习应用，支持题库、照片自定义题目、笔迹作答、AI 识别与批改、知识点记录和复习。项目使用 Java 17、Android Views、Gradle；没有独立后端。用户笔迹、草稿、题库和 API 凭据是需要保护的数据。

- 保持离线题库与本地书写可用；AI 功能需要用户自行配置服务商。
- 不把 API Key、签名密钥、个人日志、原始教材、测试题库或用户数据写入源码、提交或公开 APK。公开资源只允许项目原创的 `app/src/main/assets/banks/sample/` 与必要渲染资源。
- 不以清库、重装或删除草稿作为修复手段。改持久化结构时考虑旧版本数据的读取与迁移。
- 不暗中调用付费模型做验证。优先用固定响应和本地模拟；真实调用须在用户授权和明确费用范围内进行。

## 仓库结构

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/com/handymath/app/` | Android 应用代码，按现有类职责组织 |
| `app/src/main/assets/render/` | 本地 Markdown、KaTeX 与 HTML 净化资源 |
| `app/src/main/assets/banks/sample/` | 可公开分发的示例题库 |
| `app/src/androidTest/` | 设备端回归测试与 `MathInstrumentation` |
| `app/build.gradle` | SDK、依赖、版本、签名及 APK 架构配置 |
| `.github/workflows/release.yml` | GitHub Release 签名构建和发布 |
| `tools/build_apk.ps1` | Windows 本地 APK 构建入口 |
| `docs/` | 构建、测试、手写笔、发布等专题记录 |
| `README.md`、`README.en.md` | 中文、英文项目入口；面向用户，不放内部实现细节 |

主要代码入口：`MainActivity` 管理做题界面；`CustomQuestionActivity` 管理自定义题目；`SettingsActivity` 与 `AppSettings` 管理设置；`InkView`、`AnnotatedPaper`、`StylusAdapter` 等管理书写；`MathView`、`ThinkingPanel` 管理富文本与思考内容；`TutorClient`、`LlmTransport`、`ModelJson`、`FormulaFormat` 管理模型请求、解析与校验；`BankRepository`、`LearningDb`、`StudyStore` 管理题库和本地状态。先沿调用链定位责任类，不要把新逻辑堆进 Activity。

根目录的 `题库/`、`补充题库/`、`OCR_Output/`、`dist/`、`output/`、`tmp/` 和构建目录可能包含本地资料或生成物，不属于公开源码。提交前检查 `.gitignore`；不要用强制添加绕过忽略规则。

## 工作流程

1. `git status --short --branch` 确认分支和工作区，保留已有改动。阅读相关类、调用者、现有测试及对应文档，明确数据流和用户可见行为。
2. Bug 先建立可复现条件，记录设备、Android 版本、输入方式、题目媒体类型、设置和日志中的非敏感信息。性能问题区分主线程卡顿、图片解码、WebView 渲染、数据库及触摸分发；用证据定位热点。
3. 设计改动时明确默认值、旧数据兼容、失败行为、取消行为和无网络行为。优先做职责清楚的小改动，复用已有设置、存储与 UI 组件。
4. 对变更路径做针对性验证，再构建 APK。涉及滚动、手写笔、压感、性能或图片与 LaTeX 混排时，模拟器测试不能替代真机手势验证；如未实测，应明确记录。
5. 检查 `git diff --check`、`git diff`、新增文件清单和敏感内容。更新相关文档。只暂存本次需要的路径，不直接 `git add -A`。
6. 一次提交对应一项连贯改动。推送、打标签与发布按任务要求执行；普通开发提交不自动发布 Release。

## 代码与功能设计

- 遵循相邻 Java 文件的命名、缩进和结构，使用 UTF-8。类和方法保持单一职责；复杂流程用有意义的私有方法拆分。不要引入不必要的框架或依赖。
- Android UI 只在主线程更新；网络、图片解码、题库解包和耗时计算放在后台。异步回调检查 Activity 生命周期，关闭流、游标和图片资源，避免泄漏。
- 输入路径要轻：`ACTION_MOVE` 不做大图解码、整页 HTML 重排、同步磁盘写入或全量笔迹重算。预测点仅用于预览，不能保存成真实笔迹。保持手指滚动、手写笔书写、批注层和父级滚动的触摸分发清晰。
- 图片按显示尺寸采样；题面预览和传给模型的高质量原图分开管理。富文本使用本地资源和净化逻辑，检查图片与 LaTeX 同屏、长思考内容滚动、旋转及重进页面。
- 自定义照片题默认保留原图，自动识别设置默认关闭。仅在用户手动框选并点击识别，或明确开启自动识别时提取题目、参考答案、手写答案。三者在数据模型和模型提示中严格分开。
- 模型批改优先一次请求返回识别、得分、针对性解析、知识点与薄弱点。对 JSON 结构、评分范围与格式、公式规范做实际校验；格式错误时携带原上下文、上轮回复和具体诊断自动重试，次数由设置控制（默认 3 次）。网络错误和确实无法识别的笔迹应按各自原因处理，不伪装成格式错误。不要把无效评分直接展示为有效结果。
- 修改评分或历史数据时保持现有分数语义与持久化兼容。修改笔迹清空、撤销、重新批改、批注和草稿时验证内容不丢失，也不把解析批注误作学生答案。
- 新设置要有清楚名称、默认值、取值边界和持久化行为；旧安装升级后必须得到合理默认值。用户可见中文文案保持自然，必要时同步英文 README。

## 构建与验证

在 Windows PowerShell 中：

```powershell
$env:TEMP = 'C:\Temp'
$env:TMP = 'C:\Temp'
$env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir=C:\Temp'
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest --offline
```

本机 Android SDK 与依赖未缓存时去掉 `--offline`。也可用 `tools/build_apk.ps1`。按变更范围运行 `:app:lintDebug` 和设备测试；不要用仅能编译通过代替行为验证。设备测试示例：

```powershell
adb shell am instrument -w -e customInput true com.handymath.app.test/com.handymath.app.MathInstrumentation
adb shell am instrument -w -e photoInput true com.handymath.app.test/com.handymath.app.MathInstrumentation
adb shell am instrument -w -e stylusInput true com.handymath.app.test/com.handymath.app.MathInstrumentation
```

其他测试开关见 `app/src/androidTest/` 中的 `MathInstrumentation` 和 `docs/TESTING.md`。旧测试可能依赖未公开的大型题库；失败时先确认夹具条件，不能把缺失私有资源当作产品回归。验证报告写清测试命令、设备、结果，以及未覆盖的真机或付费接口部分。

## 提交、版本与发布

- 提交信息用简短祈使句描述实际改动，例如 `Fix thinking panel scrolling`、`Add custom photo OCR setting`；一次提交聚焦一个行为或一组紧密关联的修复。不要把 APK、构建产物、个人文件与源码混入同一提交。
- 发布时递增 `app/build.gradle` 的 `versionCode` 和 `versionName`，核对应用“关于”、README 与更新检查依赖的版本信息。标签必须是匹配版本的 `v<versionName>`。
- 发布工作流要求 GitHub Actions 签名 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。密钥只在安全存储中管理，保持同一签名证书以支持覆盖安装。详见 `docs/RELEASING.md`。
- Release 应包含 `HandyStudy-<version>-android-universal.apk`、`HandyStudy-<version>-android-arm64-v8a.apk` 和 `SHA256SUMS.txt`。发布后确认工作流、签名、文件名、下载链接及应用内检查更新可用。通用包不含厂商原生笔库；ARM64 包按构建配置提供增强支持。

## 交付标准

完成时说明改了什么、为什么、运行过哪些验证和结果、尚需哪种设备或条件复核。若未完成构建或真机验证，明确写出具体阻碍。任何引入的新配置、数据字段或用户操作都应有可发现的说明和相应回归覆盖。
