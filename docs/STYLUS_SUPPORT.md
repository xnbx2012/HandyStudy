# 手写笔适配（0.9.0）

## 设备通道

| 设备 | 实现 | 限制 |
|---|---|---|
| 小米 / Redmi / POCO | 专用设备路由，标准 Android 压感、按住侧键落笔擦除、悬浮、按键和 Jetpack 预测 | 用户尚无 App ID；没有集成或启用需授权的 Xiaomi Pencil Engine，不声称已支持其轻捏/侧旋等专属能力 |
| vivo / iQOO | 官方 `vivo:penengine-simplify:1.0.0.10`：预测、前台手势回调、可选书写振动 | 机型、笔型号、系统设置和服务决定可用能力；异常或不支持时回退 Android |
| 其它 Android | `androidx.input:input-motionprediction:1.0.0`、标准 MotionEvent 和笔尖/侧键擦除 | 不支持的传感轴返回正常的零值或兜底；硬件专属功能需真机检查 |
| 华为 | 保留原 M-Pencil 双击广播和系统开关适配，共享新的画布输入处理 | 本轮未增加 Pencil Engine |

## 输入与数据

- 使用实际 pointerId/索引，保留历史点、压感、倾斜角和方向；压感按设备 MotionRange 归一化。
- `ACTION_CANCEL` 和 Android 13+ `FLAG_CANCELED` 撤销本次接触的全部片段，包括压感橡皮切换时的临时片段。手掌指针被取消不会取消另一支有效笔迹。
- 笔优先于先到的手指；落笔及悬浮后短暂抑制手指误触。默认圆形笔尖保持原样；侧锋阴影需用户主动打开。
- `requestUnbufferedDispatch` 与预测改善显示响应；预测段会被新点替换或短时清除，永远不序列化、不导出、不用于评分。
- 在画布上放下纯文本可生成可编辑的文字，原生文字编辑框可使用 Android 的文字拖放和手写输入。
- 手势只在可用、前台且拥有焦点的做题/创建页处理，关闭开关或切到后台不消费厂商回调。书写振动默认关闭，并在抬笔、取消、失焦、暂停、销毁时停止。
- 答题页、题面批注和自定义题目共用同一套输入处理，已保存的旧三元素采样点继续兼容。

## 安装包与构建

`dist/HandyStudy-0.9.0-arm64.apk` 包含 vivo ARM64 原生预测库，适合当前小米、vivo/iQOO 等 ARM64 平板。默认 `app/build/outputs/apk/debug/app-debug.apk` 为此版本。

`dist/HandyStudy-0.9.0-standard.apk` 不包含 ARM64 原生库，保留 Android 预测及可用的 SDK Java 手势/振动，适合其它架构或旧设备；避免仅因厂商原生库而限制安装。

普通构建：`powershell -ExecutionPolicy Bypass -File tools/build_apk.ps1`。
通用兼容构建：`powershell -ExecutionPolicy Bypass -File tools/build_apk.ps1 -StandardPenOnly`。

vivo 文档示例为1.0.0.7，但其库在16 KB系统上触发兼容提示。已根据官方仓库元数据选择1.0.0.10，检查ELF LOAD对齐和RELRO结束边界均满足16 KB，并在模拟器启动验证不再弹出旧版本兼容提示。当前依赖不请求QUERY_ALL_PACKAGES，仅声明相关服务包可见性。

## 官方依据

- [小米客户端接入指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1884) 与 [开发准备](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1883)。
- [Android 触控笔质量指南](https://developer.android.com/develop/adaptive-apps/quality-guidelines/adaptive-app-quality/experiences/stylus?hl=zh-cn)、[高级触控笔功能](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/advanced-stylus-features?hl=zh-cn)、[Jetpack Input版本](https://developer.android.com/jetpack/androidx/releases/input)。
- [vivo 手写笔SDK 2.0接入指南](https://developers.vivo.com/doc/d/5eee499ab8584a52ba8d402a3c084469)，通过该官网的文档接口读取正文；[官方 Maven 元数据](https://repos.vivo.com.cn/maven/repository/external-lib/vivo/penengine-simplify/maven-metadata.xml)。

模拟器测试不能替代厂商真机。尚未验证小米/vivo实体笔的实际延迟、传感器校准、触控按键及振动效果。

## 可配置快捷操作与收藏（2026-10-31）

- 设置 → 书写方式：新增“显示笔触提示（悬浮 / 落笔圆圈）”，默认关闭。
- 设置 → 手写笔快捷动作：单击、双击、长按分别可选“切换笔 / 橡皮”“撤销”“无动作”“切换笔触类型（答题 / 批注）”。沿用旧快捷动作总开关。默认均为切换笔 / 橡皮。
- Android 14+ 标准笔按键使用系统单击 / 双击和长按时间阈值；双击和长按不会额外执行单击。暂停或失焦时清理等待中的按键回调。
- vivo SDK 的 2 映射双击，3 / 4 映射单击；华为双击广播仍尊重系统禁用设置。只上报按住侧键落笔的设备使用长按设置，其中切换笔 / 橡皮保留本次接触临时擦除的行为。
- 设置 → 多指手势：双指单击撤销、三指单击重做，两个开关独立且默认关闭。仅处理纯手指的短时轻点；超出移动阈值、取消、四指及只读 / 锁定画布不会触发。手指书写开启时，第二指加入会取消尚未提交的临时笔迹，再处理多指手势。
- 题目页提供“☆ 收藏 / ★ 已收藏”；首页“收藏”进入跨题库列表，可继续作答或取消收藏。SQLite learning.db 从版本 2 升级至 3，新建独立收藏表，按题库 ID 和题目 ID 去重，不修改答题草稿。题库已移除或题目不存在时显示错误并允许取消收藏。

验证：构建及 lintDebug 通过；模拟器 `-e inputSettings true` 覆盖默认值、独立手势开关、拖动 / 四指 / 锁定 / 只读排除、手指书写下撤销、笔快捷动作映射、标准按键单击 / 双击 / 长按互斥、收藏去重与数据库重开持久化，以及设置 / 收藏页面入口。`-e stylusInput true` 和 `-e regression true` 通过。实体笔的厂商事件仍需对应真机验证。
