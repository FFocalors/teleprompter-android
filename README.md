# Teleprompter Android

Teleprompter Android 是一款面向 Android 手机和平板的开源提词器，采用 Kotlin 与 Jetpack Compose 开发，支持本地台本管理、轻量富文本编辑、播放样式配置和沉浸式提词播放。

> An open-source Android teleprompter built with Kotlin and Jetpack Compose, designed for local script management, lightweight rich-text editing, customizable playback, and phone/tablet use.

## 项目状态

项目处于早期开发阶段，尚未发布正式稳定版本，当前适合开发测试和功能体验。台本管理、编辑、播放设置、本地播放主流程和 TXT/DOC/DOCX/Markdown 文件导入已经接入真实本地数据层；手机远控已实现一对一局域网 WebSocket 通信、二维码配对、状态同步和播放控制（在可信 Wi-Fi 或个人热点下使用）；语音跟随仍未完成。

## 当前功能

- Android 手机和平板的响应式 Compose 界面，支持横屏和竖屏播放。
- 本地台本库和单层台本夹：新建、导入、编辑、重命名、移动、删除和筛选。
- Room 本地持久化，台本正文使用版本化富文本 JSON 保存。
- TXT 文件导入：通过系统文件选择器读取，支持 UTF-8/UTF-16/GB18030 编码，转换为 `ScriptDocument` 后由 Repository 原子创建台本。
- DOC/DOCX 文件导入：同一“导入文件”入口，格式由文件内容自动识别；只提取主文档中的普通正文段落，文字样式不保留，表格、图片、目录、字段、页眉页脚、批注脚注等结构直接跳过。
- Markdown 文件导入：支持 `.md` / `.markdown`，复用 TXT 的编码识别（UTF-8/UTF-16/GB18030）；一级标题（ATX `#` 或 Setext `===`）作为台本标题，其他标题作为正文段落；只支持纯文字、标题和普通正文，遇到粗体、斜体、列表、代码、链接、表格、HTML 等特殊格式时拒绝导入而非部分解析，文件限制 5 MiB。
- 轻量富文本编辑：选区级粗体、斜体、下划线、撤回和防抖自动保存。
- 中文正常语速预计时长，播放设置与全局默认设置分离保存。
- 播放预设、字号、文字对齐、方向、镜像、速度模式和目标时间模式。
- 倒计时、提词线/提词条、暂停、立即恢复、进度微调和长按退出。
- 播放预览与真实播放共用视口、排版和提词辅助规则。
- Preferences DataStore 保存全局默认设置和语言选择，预留简体中文与 English 资源。
- 手机远控（一对一同局域网）：提词端作为 WebSocket Server 生成一次性配对二维码，控制端扫码建立连接；支持协议握手、心跳、断线自动重连（有限指数退避）、单控制端限制、状态快照同步、真实播放命令和”设置保存后”远控启动。双端支持主动断开（提词端可断开当前控制端或完全停止远控，控制端可断开提词端），显式断开不会触发自动重连；控制端页面针对窄屏和字体缩放做了响应式优化。正式播放第一行按”开始时提词线状态”定位（开启时位于提词线下约 1.5 行、关闭时位于视口顶部约四分之一处），播放开始后移动/开关提词线不影响正文滚动；控制端”附近文字”按真实 `TextLayoutResult` 与稳定阅读锚点实时定位，不保留提词端视觉折行，显示区域高度固定。局域网传输当前不加密，请仅在可信 Wi-Fi 或个人热点使用。

## 开发计划

- 更完整的 Markdown 子集支持（有序列表、粗体/斜体、行内代码等）。
- 多设备控制、一对多同步和更精确的位置同步。
- 更完整的富文本编辑能力、重做和输入法组合样式。
- 语音跟随、长时间稳定性测试和无障碍回归。
- 适配未来 targetSdk 37 的局域网运行时权限。

## 技术栈

- Kotlin 2.2.10、Gradle Kotlin DSL、Gradle Wrapper 9.4.1。
- Android Gradle Plugin 9.2.1，Compose BOM 2026.02.01，Material 3。
- Navigation Compose、Kotlin Coroutines/Flow、Room 2.8.4、Preferences DataStore 1.2.1。
- JUnit、Compose UI Test、AndroidX Test 和 Room 测试数据库。

## 构建要求

- Android Studio 需要支持 AGP 9.2.1。
- Gradle 运行时使用 JDK 21；项目 Java source/target compatibility 为 11。
- Android SDK compileSdk 36.1，targetSdk 36，minSdk 26。
- Windows、macOS 和 Linux 均可使用 Gradle Wrapper 构建；首次构建需要可访问依赖仓库。

## 构建与验证

```bash
git clone https://github.com/FFocalors/teleprompter-android.git
cd teleprompter-android
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

也可以使用 Android Studio 打开仓库根目录，等待 Gradle Sync 后运行 `app` 模块。Debug APK 输出在 `app/build/outputs/apk/debug/`，该目录不会进入 Git。

## 项目结构

```text
app/
├── src/main/java/com/zhy20/teleprompter/
│   ├── app/                 # Application、AppContainer、导航与播放 Session
│   ├── core/                # Design System、模型、导航、工具和文字渲染
│   ├── data/                # Room、DataStore、Repository、序列化、文件导入与 Mock 数据
│   ├── feature/             # library、editor、setup、prompter、remote、settings
│   └── preview/             # Compose Preview
├── schemas/                 # Room 导出的版本化 schema
└── src/test、src/androidTest # JVM 单元测试和 Android 测试
docs/                        # 架构、状态、路线图和历史实现记录
```

## 数据与隐私

台本正文、播放设置和编辑状态默认保存在本地设备。当前版本不要求登录、不上传台本正文，也不依赖云同步；项目没有内置账号、广告或遥测服务。手机远控只在一对一同局域网设备之间建立 WebSocket 连接：提词端显示一次性配对二维码，控制端扫码后仅通过局域网传输协议消息，不使用云端、公网或第三方服务器；配对令牌只保存在内存，不写入 Room 或 DataStore，应用进程结束即失效。请仅在可信 Wi-Fi 或个人热点使用（局域网 WebSocket 当前不加密），不要把台本全文视为通过不可信网络传输的安全数据。

TXT 文件导入通过 Android Storage Access Framework 只读取用户本次主动选择的文件，不申请任何存储权限，不复制原文件，也不长期持有文件 URI 权限。导入完成后应用只保存转换后的台本内容，与原文件不再关联。

DOC 与 DOCX 导入遵循同样的隐私原则：仅读取用户主动选择的文件，解析完成后只保存转换后的普通正文段落（文字样式与表格、图片、目录、字段等结构不保留），原文件不复制、不持续关联。Word 文件解析全部在本地完成，不联网、不上传。

Markdown 导入同样只读取用户主动选择的文件：仅支持纯文字与标题，遇到不支持的格式会提示”暂不支持”并拒绝导入，不会把原始 Markdown 标记作为正文保存；解析后的 `ScriptDocument` 与原 `.md` 文件不再关联。

扫码通过 zxing-android-embedded 在设备本地完成，不把相机画面上传到任何服务。

## 文档

- [架构说明](docs/ARCHITECTURE.md)
- [项目状态](docs/PROJECT_STATUS.md)
- [开发路线图](docs/ROADMAP.md)
- [数据与编辑器架构](docs/DATA_AND_EDITOR_ARCHITECTURE.md)
- [前端实现记录](docs/FRONTEND_HANDOFF.md)
- [贡献指南](CONTRIBUTING.md)
- [变更记录](CHANGELOG.md)

## 许可证

本项目采用 [Apache License 2.0](LICENSE)。

## 截图

正式产品截图将在后续补充；当前仓库不引用本机截图或开发工具窗口截图。
