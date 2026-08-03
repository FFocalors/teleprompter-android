# Teleprompter Android 前端交接

## 1. 当前基线与审计结论

- 单模块 `app`，`applicationId` / `namespace` 均保持为 `com.zhy20.teleprompter`。
- Gradle Wrapper：9.4.1；Android Gradle Plugin：9.2.1；Kotlin：2.2.10。
- Compose BOM：2026.02.01；Navigation Compose：2.9.6。
- `compileSdk`：36.1；`targetSdk`：36；`minSdk`：26；Java 兼容级别：11。
- `settings.gradle.kts` 中现有 Google、Maven Central 与镜像仓库配置均保留。
- 本机 Android SDK 最高安装到 36.1。原 `androidx.core:core-ktx:1.19.0` 要求 compileSdk 37，因此仅将 Core KTX 调整到缓存中可用且兼容 compileSdk 36 的 1.18.0；未升级 Gradle、AGP、Kotlin 或 Compose。
- 当前目录及其父目录没有 `.git` 元数据，`git status` 返回“not a git repository”。因此无法给出真实工作树差异或提交状态，也未执行任何 reset、clean、commit 或 push。
- 本机环境变量 `JAVA_HOME` 指向了 JDK 的 `bin` 目录，命令行验证时临时使用 Android Studio 自带 JBR；没有写入项目或本机配置。

## 2. Figma 实际读取范围

设计入口为 Figma Make 文件“开始设计提示词”，读取了根节点 `0:1` 的设计上下文、Make 最新可访问的 Version 4 预览及其页面源码/样式，并在浏览器中逐页核对。

Version 4 实际提供五个可访问路由：

| Figma Make 页面 | 路由 | Compose 实现 |
| --- | --- | --- |
| Dashboard / 台本库 | `/` | `LibraryScreen` |
| Script Editor / 编辑 | `/editor/:id` | `EditorScreen` |
| Style Setup / 样式与启动 | `/style/:id` | `SetupScreen` |
| Prompter / 播放 | `/prompter/:id` | `PrompterScreen` |
| Remote / 控制端 | `/remote` | `RemoteScreen` |

该 Make 文件没有暴露可枚举的 Figma Variables、独立 Component 集或完整原型连接元数据。全局设置、语言页、完整暂停态及控制端的全部状态没有独立成可读取 Frame；这些页面与状态严格按同一文件附带的提词器产品说明补齐，并复用已读取的 Version 4 设计 Token，没有另造一套视觉风格。

## 3. 已实现页面

- 首页 / 台本库：全部、未分类、单层台本夹筛选，响应式卡片网格，新建台本、台本夹对话框、编辑、开始提词、控制端状态卡、设置入口和空状态。
- 台本编辑页：标题与正文输入、加粗状态、单步撤回模拟、自动保存状态、进入提词设置。
- 样式 / 启动页：实时预览、背景色、文字色、字号、方向、镜像、速度/目标时间、预计时长、倒计时、提词线开关/样式/位置、控制端状态和开始播放。
- 播放页：沉浸式显示、模拟自动进度、进度与时间、提词线、控制栏显隐、暂停、立即恢复、倒计时恢复、进度和提词线位置调整、结束与完成、断线继续提示。
- 控制端：未连接、等待、连接成功、断线；首页等待、准备、倒计时、播放、暂停、完成；速度、进度、暂停、恢复及长按结束模拟。
- 全局设置：新台本默认颜色、字号、方向、镜像、滚动模式、倒计时、提词线和语言入口。
- 语言设置：简体中文与 English 的应用内会话切换；首次启动固定为简体中文。

## 4. Design Token

统一入口位于 `core/design/DesignSystem.kt`：

- `AppColors`：背景 `#141622`、卡片 `#1B1D2A`、抬升表面 `#222536`、品牌色 `#3F6987`、次级蓝灰 `#3E4C6B`、主文字 `#F2F5FA`、次级文字 `#C4CBD6`、弱文字 `#9AA3B2`、边框 `#3A4560`，以及低饱和成功/警告/错误色。
- `AppColorOptions`：播放背景和文字的集中色板。
- `AppTypography`：系统 Sans Serif 字体层级；时间和业务格式由 `core/util` 集中处理。
- `AppSpacing`：4、8、12、16、24、32、40 dp。
- `AppShapes`：6、8、12、16、24 dp 圆角层级。
- `AppElevation`：0、1、6 dp。
- `AppTheme`：固定深色 Material 3 主题，不使用纯白、亮蓝、发光、玻璃或大面积渐变。

## 5. 工程目录

```text
com.zhy20.teleprompter
├── MainActivity.kt                 # 入口、深色系统栏
├── app
│   ├── TeleprompterApp.kt          # NavHost 与应用容器
│   └── AppState.kt                 # 可替换 Mock 会话状态
├── core
│   ├── design                      # Token、主题、通用组件
│   ├── model                       # 纯 Kotlin 数据/事件模型
│   ├── navigation                  # 路由常量
│   └── util                        # 日期、时长格式
├── data/fake                       # 统一 Mock 数据
├── feature
│   ├── library
│   ├── editor
│   ├── setup
│   ├── prompter
│   ├── remote
│   └── settings
└── preview                         # 九个主要 Preview
```

页面、状态、模型和共用组件已经分离；没有增加 Gradle 子模块、依赖注入框架或后端模板。当前没有超过 320 行的 Kotlin 页面文件。

## 6. 导航结构

```text
library
├── editor/{scriptId}
│   └── setup/{scriptId}
│       └── prompter/{scriptId}
├── setup/{scriptId}
├── remote
└── settings
    └── settings/language
```

导航参数只传 `scriptId`；播放页退出直接弹回已有样式页，避免重复压栈。

## 7. 核心模型

- `Script`、`ScriptFolder`、`PlaybackSettings`。
- `ScriptContent` → `ScriptBlock.Paragraph` → `ScriptSpan`，当前已能表达段落和局部加粗，不把未来格式锁死为单一 String。
- `PlaybackState`：Idle、Preparing、Countdown、Playing、Paused、Finished、Exited。
- `RemoteConnectionState`：Disconnected、Waiting、Connected、ConnectionLost。
- `PlaybackEvent`：开始、暂停、立即/倒计时恢复、速度增减、小步进退、任意进度、结束、提词线开关和样式变更。

## 8. Mock 状态实现

`AppState` 是单一可替换的内存会话，维护台本、当前台本、编辑草稿、播放配置、全局默认值、播放状态、提词端所在页面、控制端连接、进度、保存状态和语言选择。

当前事件只在内存中驱动 Compose 重组。控制端与提词端共享同一个 `AppState`，用于演示准备、播放、暂停、进度和断线联动；没有伪装成真实网络实现。

## 9. 响应式与系统适配

- 以可用宽度判断紧凑/宽屏布局，不照搬网页像素坐标。
- 手机竖屏为单栏，样式预览在上、设置在下、开始按钮固定底部。
- 手机横屏和大宽度设备自动使用更宽内容或双栏。
- 平板横屏为固定侧栏 + 自适应一/两列台本卡片；样式页为预览/设置双栏。
- 平板竖屏回落到宽单栏，保留固定开始按钮。
- 全局处理安全区；播放页隐藏系统栏；固定深色系统栏图标，避免跟随设备浅色主题产生低对比度。
- 主要按钮和图标点击区为约 48 dp 或以上；长英文按钮允许两行或使用紧凑标签。

已在 Pixel 10 Pro 与 Pixel Tablet AVD 安装检查手机竖屏、平板横屏和平板竖屏。横竖屏均未发现明显横向溢出。

## 10. 国际化与 Preview

- 默认资源：`res/values/strings.xml`（简体中文）。
- 英文资源：`res/values-en/strings.xml`。
- 应用控件文案均来自字符串资源；Mock 台本正文属于演示内容，因此保留在 `FakeData`。
- 已提供：手机首页、平板横屏首页、编辑、手机样式、平板样式、播放中、暂停、控制端播放中、设置页 Preview。

语言页通过应用范围的 Compose 配置在当前会话即时切换资源 Locale；进程重启后恢复简体中文。正式实现时应改接 Android 应用级语言 API 和持久化。

## 11. 尚未实现的真实功能与接入点

- Room / DataStore 或其他持久化。
- TXT、DOCX、Markdown 解析和系统文件选择器。
- 正式富文本编辑、选区级加粗、可靠撤回栈。
- 基于排版高度和时间轴的正式滚动播放引擎。
- 局域网发现、二维码、WebSocket/TCP/UDP、远控同步和重连。
- 语音识别、账号、云端。
- 台本夹创建、自动保存和语言选择目前只做前端/会话实现，没有落盘。

建议后续将 `AppState` 拆成稳定的 UI state/event 接口与真实 Repository/Session 实现，保持现有 `PlaybackEvent` 作为播放和远控共同输入；将 `ScriptContent` 映射到数据库或文档格式时保留 block/span 结构。

## 12. 构建与运行

PowerShell：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

Android Studio 可直接打开当前根目录、等待现有 Gradle Sync 完成后运行 `app`。不要为版本提示升级工具链；若恢复到 Core KTX 1.19.0，需先安装 compileSdk 37 并单独评估升级范围。

## 13. 已知视觉差异、问题与技术债

- Figma Make 是 Web 原型；Compose 实现保留视觉与信息架构，但使用响应式布局、Android Insets 和触控尺寸，没有复制固定坐标。
- 设计指定 Noto Sans SC / JetBrains Mono 的意图已映射为系统 Sans Serif 与统一时间格式，未打包额外字体文件，因此不同 Android 系统的字形会略有差异。
- 二维码目前使用 Material 图标和“演示二维码”说明；应用启动图标仍为初始化模板资源。
- 播放文字为视觉模拟，进度按定时器推进，不代表正式滚动精度。
- 暂停页的长按结束已实现交互，但尚未增加长按进度反馈或触觉反馈。
- 单元测试覆盖富文本基础结构、播放默认值和 Mock 台本夹计数；后续仍需增加 Compose UI/截图回归、进程恢复、字体缩放和无障碍测试。
- Lint 的依赖版本提示刻意保留，因为当前阶段明确禁止无需求升级工具链；其余少量未使用资源/复数建议可在下一轮国际化整理时处理。

## 14. 推荐下一阶段

1. 定义 Repository、编辑会话、播放会话和远控会话接口，将 `AppState` 的 Mock 数据源替换为可测试实现。
2. 接入 Room/DataStore 与应用级语言持久化，建立数据库迁移和进程恢复测试。
3. 实现 block/span 富文本编辑与文件导入管线。
4. 实现基于实际文本布局的播放引擎，再接入远控协议和断线恢复。
5. 补充 Compose UI 测试、关键设备截图回归、TalkBack 和大字体测试。
