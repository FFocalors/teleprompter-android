# Teleprompter Android 前端实现记录

## 1. 当前基线

- 保持单模块 `app`，`applicationId` / `namespace` 均为 `com.zhy20.teleprompter`。
- Gradle Wrapper 9.4.1、Android Gradle Plugin 9.2.1、Kotlin 2.2.10、Compose BOM 2026.02.01、Navigation Compose 2.9.6；未升级工具链、依赖仓库或模块结构。
- `compileSdk` 36.1、`targetSdk` 36、`minSdk` 26；Gradle 运行时使用 JDK 21，源码兼容 Java 11。
- 本文档保留前端实现中的设计决策和验证记录，公开仓库的当前状态以 `README.md` 和 `docs/PROJECT_STATUS.md` 为准。

## 2. 页面与设计映射

实现以低饱和深色界面、橙色主操作和响应式手机/平板信息架构为视觉依据，页面映射如下：

| 产品页面 | 路由 | Compose 页面 |
| --- | --- | --- |
| 台本库 | `library` | `LibraryScreen` |
| 台本编辑 | `editor/{scriptId}` | `EditorScreen` / `PersistentEditorScreen` |
| 样式与启动 | `setup/{scriptId}` | `SetupScreen` / `PersistentSetupScreen` |
| 提词播放 | `prompter/{scriptId}` | `PrompterScreen` |
| 控制端 | `remote` | `RemoteScreen` |

全局设置和语言页面由产品需求补齐；控制端连接在当前版本仍是本地 Mock 状态。

## 3. 已实现页面

- 台本库：Room Flow 驱动的 Loading/Empty/Content/Error 状态，全部/未分类/单层台本夹筛选；真实新建、重命名、移动与永久删除台本，以及新建、重命名、删除台本夹。删除台本夹会在事务中将台本移至未分类。
- 编辑页：按 `scriptId` 从 Repository 加载真实富文本，支持选区级粗体/斜体/下划线、撤回、700 ms 防抖自动保存、revision 防旧写回、失败重试、生命周期 flush、IME 避让与短文本滚动余量。
- 样式/启动页：显示预设、真实比例实时预览、字号、文字对齐、方向、镜像、节奏/目标时间、倒计时、提词线和控制端状态。
- 播放页：沉浸式显示、实际 Activity 方向锁定、模拟进度、紧凑时间信息、红色提词辅助、控制栏、暂停/恢复、完成和断线提示。
- 控制端：连接、等待、准备、播放、暂停、完成及 Mock 联动；附近文字预览遵循台本对齐方式。
- 全局设置与语言：Preferences DataStore 持久化新建台本默认样式、预设、文字对齐、方向、镜像、节奏、倒计时、提词辅助及中英文选择；全局默认值只在新建时复制，不覆盖已有台本。

## 4. 冷灰橙黄 Design System

统一入口：`core/design/DesignSystem.kt`。

| Token | 值 | 用途 |
| --- | --- | --- |
| `AppColors.Background` | `#222627` | 深色主背景 |
| `AppColors.Surface` | `#2A2F30` | 卡片、顶部栏、控制面板 |
| `AppColors.SurfaceRaised` | `#343A3B` | 抬升表面与次级区域 |
| `AppColors.Primary` | `#ED8F19` | 主操作、选中边框、强调 |
| `AppColors.PrimaryAlt` | `#FEE935` | 小范围等待/重点标识 |
| `AppColors.PrimaryPressed` | `#C97410` | 主按钮按下态 |
| `AppColors.OnPrimary` | `#1B1F20` | 橙底按钮文字和图标 |
| `AppColors.TextPrimary` | `#F5F7FA` | 非纯白冷白主文字 |
| `AppColors.TextSecondary` | `#C8CED3` | 次级冷灰白文字 |
| `AppColors.TextWeak` | `#8B9594` | 弱提示文字 |
| `AppColors.Border` | `#3D4445` | 卡片与次级按钮边界 |
| `AppColors.GuideLineBrightRed` | `#FF453A` | 深背景提词线/条 |
| `AppColors.GuideLineDeepRed` | `#C62828` | 浅背景提词线/条 |

- 主按钮为橙底深色文字，并有较深橙色按下态；黄色只保留为小范围强调，未大面积铺底。
- 卡片、次级按钮和导航使用深灰层级、冷白文字和清晰细边框，不使用大阴影。
- 应用内普通文字不再使用米色 `#FFF2DF` 或纯白 `#FFFFFF`；播放区的文字由显示预设单独决定。
- `AppTypography`、`AppSpacing`、`AppShapes`、`AppElevation` 仍集中管理。

## 5. 显示预设、GuideMode 与旧数据

`DisplayPreset` 包含 `id`、`name`、`backgroundColor`、`textColor`、`previewLabel`、`guideLineDarkModeColor`、`guideLineLightModeColor`。不支持自定义颜色。

| ID | 名称 | 背景 / 文字 |
| --- | --- | --- |
| `black_white` | 黑底白字（默认） | `#121719` / `#F5F7FA` |
| `white_black` | 白底黑字 | `#EFF2F4` / `#202426` |
| `deep_blue_white` | 深蓝底白字 | `#1D3550` / `#F5F7FA` |
| `deep_green_white` | 深绿底白字 | `#1E443B` / `#F5F7FA` |

- 已删除“橙底深灰字”：该组合在长时间提词中干扰性高，且橙色应留给应用强调而非播放底色。
- `DisplayPresetPicker` 在样式页和全局设置复用。每张卡展示名称、真实背景/文字色、示例“台本 Aa”和橙色选中边框。
- 旧颜色设置会按 RGB 距离映射至最近安全预设，无法解析时回落黑底白字；不会恢复任何自定义色 UI。
- 提词辅助统一为 `GuideMode.Off`、`GuideMode.Line`、`GuideMode.HighlightBar`。渲染层不再组合 enabled/style/visible 标记：关闭不渲染，横线只渲染 3 dp 红线，提词条只渲染单行高度的半透明红色区域且没有边线。
- `guideModeFromLegacy()` 是旧 enabled/style 数据的单向迁移入口；设置预览与播放页共用 `PrompterGuide`，不会残留上一模式的元素。
- 提词辅助按播放背景亮度选择亮红或深红，默认位于屏幕上方约四分之一。

## 6. 文字对齐、实时预览与播放方向

### 文字对齐

- `PlaybackSettings` 新增 `textAlignment: PlaybackTextAlignment`，枚举为 `Start`、`Center`、`End`，默认左对齐。
- 样式页与全局设置均提供三个等宽分段按钮；所有文案在中英文资源中维护。
- `toComposeTextAlign()` 将纯 Kotlin 领域模型映射为 Compose `TextAlign`，供样式预览、播放页和控制端附近文字复用。

### 真实播放优先的预览

- `PrompterViewport` 是设置预览与正式播放共用的画面骨架：背景、顶部状态区、正文视口、左右边距、字号、行高、对齐、镜像、GuideMode 与富文本渲染均由它统一处理。
- 长正文使用无界高度获取真实排版尺寸时，`wrapContentHeight` 必须显式指定 `Alignment.Top`；其默认 `CenterVertically` 会把高于视口的正文居中，即使引擎偏移为 `0` 也会从台本中段显示。
- 运行态使用真实视口；预览态以当前设备在所选方向下的目标宽高建立虚拟播放画布，再整体缩放到预览卡片。该画布与播放页使用相同的大屏断点、字号、行高、边距、状态区和提词线比例，而非根据卡片宽度重新排版。
- 正文视口从状态区下方开始。预览固定从台本第一行和预览区顶部展示，并只对最终可见片段使用 `TextOverflow.Ellipsis`；正式播放使用完整正文和 `TextOverflow.Clip`，第一段从正文区底部进入并向上滚动。
- 镜像设置为“正常 / 镜像”双选。`PlaybackMirrorPolicy` 明确只对 `ScriptContent` 返回 `scaleX = -1`；状态信息、进度、控制浮层、提词辅助和手势坐标不镜像。

### 标准中文语速预计

- `ChineseSpeechDurationEstimator` 是唯一的正常语速来源，标准值为 `STANDARD_CHINESE_UNITS_PER_MINUTE = 255`；850 个中文等效字符的基础时长为 200 秒（3:20）。
- 每个中文字符、数字和其他可朗读字符计 1 单位；连续英文单词计 1.5 单位；空格不计。逗号/顿号/分号增加 180 ms，自然句末标点增加 350 ms，连续段落换行增加 500 ms。
- 估算输入始终为 `ScriptDocument.plainText()`，富文本样式不会重复计数。空文档为 0 秒，其余结果四舍五入到秒且至少为 1 秒。
- 首页、设置页、播放引擎、控制端和 Mock 数据都通过当前 `ScriptDocument` 重新估算；`Script.normalEstimatedDurationSeconds` 仅作为同步更新的缓存。字体、方向和对齐改变实际滚动距离，但不会改变正常朗读时长。

### 统一富文本映射

- `ScriptAnnotatedStringMapper` 是 `ScriptDocument -> AnnotatedString` 的唯一转换层，编辑器、预览、播放与控制端均通过 `RichScriptText` 复用它。
- 每个 Span 都显式写入 `FontWeight.Normal/Bold`、`FontStyle.Normal/Italic` 和 `TextDecoration.None/Underline`。此前编辑器使用默认 Bold 的 `headlineMedium`，而普通 Span 没有覆盖字重，导致全文看起来加粗；现在编辑器基础样式和普通 Span 都明确为 Normal。

### 实际横竖屏播放

- `PrompterScreen` 从 `LocalView.context` 获取真实宿主 Activity（不会受语言本地化 Context 影响），进入播放后将 `requestedOrientation` 设为 `SCREEN_ORIENTATION_PORTRAIT` 或 `SCREEN_ORIENTATION_LANDSCAPE`。
- 方向设置与恢复使用分离的 Effect：进入/设置变化时请求目标方向，离开播放页时恢复进入前策略，不会因 Effect 重启提前恢复。
- `MainActivity` 在 manifest 处理 `orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden`；`rememberAppState()` 另用 Saver 保存当前台本、播放设置、状态、有效已用时间和语义进度，配置重建后重新测量但不从头播放。
- targetSdk 36 下，`MainActivity` 还声明 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`，暂时退出 Android 16 大屏忽略方向请求的兼容行为；Android 17/API 37 将取消该退出能力，届时需重新评估平板方向策略。
- 样式页保存的方向在 `AppState.beginPlayback()` 再次选择台本时仍保留，因此预览比例、实际播放和暂停背景文本一致。

## 7. 测量驱动的播放引擎与控制浮层

- `PlaybackEngineState` 是播放单一状态源，包含 `layoutReady`、`requiresScrolling`、播放状态、有效已用时间、语义进度、像素偏移、总距离、速度、目标时间、实际时长及开始/结束偏移。
- `PrompterLayoutMetrics` 统一记录完整视口、状态区高度、正文视口、正文上下边界、真实文本高度、Guide 位置和开始/结束偏移。`RichScriptText.onTextLayout` 返回真实排版高度；播放引擎只接收正文视口高度，不会将正文或 Guide 滚进顶部状态区。
- 顶部状态区为运行时约 56 dp（大屏约 64 dp），系统栏采用沉浸式临时覆盖，不参与正文和控制栏重新测量；左侧已用时长、右侧剩余时长和条件进度条固定在该区域。正文从其下方开始，文本高度不超过正文视口时不滚动、不自动完成，文本块在底部保留约 8% 安全余量。
- 播放开始偏移为 `viewportHeight * 0.82`，使第一行完整出现在正文区底部附近；结束偏移为 `viewportHeight * 0.67 - textHeight`，使最后一行停在正文下三分之一处。短台本也沿同一时间轴从底部向上移动。
- `AppState.startPlaybackFromBeginning()` 是样式页与控制端共用的新播放入口，会重新创建 Engine Session；`PlaybackEngineState.isStartingFromBeginning` 显式标识首次播放，倒计时和第一移动帧均保持原点。恢复倒计时不会获得该标记，因此保留原位置。
- 自动滚动由 `withFrameNanos` 提供单调时间，但位置按“时间差 / 实际总时长”计算，不按帧累加像素。因此刷新率、掉帧不会改变同一时间点的位置。
- 速度模式以正常预计时长除以倍率得到实际时长；目标时间模式直接使用用户目标时长。变速前先结算旧时间锚点，再以当前位置建立新锚点，位置和已用时间都不清零。
- 暂停先结算当前帧再冻结；立即恢复或倒计时恢复从相同位置建立新锚点，倒计时不计入有效已用时间。滑块和纵向微调期间设置 `isManualAdjusting`，松手后从新位置继续。
- 长台本到达末尾后进入 Finished、固定在结束偏移且不自动退出；播放页不显示完成弹窗，只保留非模态顶部任务栏供用户退出。短台本的零滚动距离不会触发 Finished。
- 左上常驻显示已用时间，右上显示剩余时间。只有布局完成、长台本正在播放且未手动调整时，右上才显示有限宽度的橙色轨道进度条；暂停、倒计时、短台本和完成状态均隐藏。
- 播放控制统一由 `PlaybackControlBar` 渲染，`ControlBarMode` 区分 `Hidden`、`Playing`、`Paused` 和 `Finished`；同一顶部半透明卡片使用固定槽位放置长按退出、速度组、播放/暂停和 3 秒倒计时开关，播放与暂停切换时不重排主要控件。播放态约 3 秒无操作自动隐藏；开启倒计时开关后点击播放键进入全屏倒计时，完成态保留位置调节和长按退出。
- `controlsVisible` 与 `PlaybackState` 独立管理：进入暂停默认显示控制栏，暂停时不会自动隐藏；中央正文单击只切换暂停控制栏显隐，隐藏时仍保持暂停、进度、提词线和顶部已用/剩余时间不变。播放恢复后重新启用约 3 秒自动隐藏规则。
- 播放中边缘触摸由最内层 `PointerInput` 消费，中央区域才支持点击、双击和纵向微调；暂停和完成态使用独立中央触控策略，单击显示/隐藏控制栏、上下滑动调整语义进度，边缘仍保持死区，暂停控制栏内部事件优先由控件消费，不穿透为正文单击。
- 正文镜像位于独立 graphics layer；状态、提词辅助和浮层是平级渲染层，触摸语义保持正常。
- `roundedClickable` 先按控件 Shape 裁剪再附着点击反馈；可点击卡片、选择项、预设卡和侧栏项目的鼠标 Hover/按压层与圆角外框一致，主/次/文字按钮同样在 Modifier 层裁剪反馈。

## 8. 真实编辑与保存交互

- 编辑器使用单一 `ScrollState`、IME/导航栏 Padding、尾部空间和 `BringIntoViewRequester`，短文本与键盘同时出现时仍可上滑并保持光标可见。
- 编辑器可见文本使用与播放完全相同的 `ScriptAnnotatedStringMapper`；普通、粗体、斜体、下划线及组合样式可在同一正文中明确区分，工具栏状态不会把整个文档临时渲染为粗体。
- 保存状态为纯图标：Initial 中性、Saving 不闪烁、Saved 绿色、Error 红色可重试；辅助功能文案在资源中提供。
- `EditorViewModel` 首次加载 Room 后建立本地草稿，后续数据库回执不会无条件重建编辑器状态，因此不会覆盖正在输入的 dirty 内容。
- 标题和 `ScriptDocument` 共用 `editRevision` / `savedRevision`。停止输入 700 ms 后保存；只有保存快照仍是最新 revision 时才显示绿色。返回、进入提词设置、Activity `ON_STOP`、Composable 释放和 ViewModel 清理均会尝试 flush。
- 撤回栈属于单次编辑会话，最多保留 80 个文档与选区快照；可恢复文字、段落、样式和选区，不持久化重做历史。

## 9. 工程结构、导航与持久化状态

```text
com.zhy20.teleprompter
├── app                 # TeleprompterApplication、AppContainer、导航与播放 Session
├── core/design          # Token、主题、通用组件、文字渲染
├── core/model           # 纯 Kotlin 模型、富文本与播放设置
├── core/navigation      # 路由
├── core/util            # 格式化、触控策略、预览尺寸规则
├── data/local           # Room Database、Entity、DAO
├── data/repository      # Repository 接口、Room/DataStore 实现
├── data/serialization   # 版本化 ScriptDocument / PlaybackSettings JSON
├── data/fake            # 仅 Preview 与测试使用的 Mock 数据
├── feature              # 页面及按功能拆分的 ViewModel
└── preview              # Compose Preview
```

路由：`library`、`editor/{scriptId}`、`setup/{scriptId}`、`prompter/{scriptId}`、`remote`、`settings`、`settings/language`。只传 `scriptId`；页面通过 `SavedStateHandle` 和 Repository 重建。`AppState` 只承担本地播放 Session、远控演示状态和当前已加载台本桥接，不再为生产首页提供 Fake 数据。

Room 数据库版本为 1，schema 固定输出到 `app/schemas/com.zhy20.teleprompter.data.local.TeleprompterDatabase/1.json` 并进入版本控制。首次运行不插入示例台本，也没有 destructive migration。完整数据层说明见 `docs/DATA_AND_EDITOR_ARCHITECTURE.md`。

## 10. Preview、测试与验证

- Preview 覆盖：四种显示预设的设置页、横屏/竖屏真实虚拟播放预览、局部格式编辑器、横屏/竖屏播放、顶部状态区、红线/红色提示条、控制端和设置页。
- 单元测试增加版本化富文本/播放设置 JSON、无效 JSON 安全降级，以及编辑器 dirty、防抖、flush、失败重试、Room 回流保护和撤回状态测试；既有语速、富文本映射、GuideMode、播放引擎和触控策略继续保留。
- AndroidTest 增加 Room 内存数据库端到端覆盖：新建/更新/删除台本、台本夹创建/重命名/冲突、台本移动、Flow 更新、全局默认复制、纯样式修改保持预计时长，以及删除台本夹事务。既有 Compose UI 测试继续覆盖播放交互和画面边界；构建会编译 AndroidTest APK。
- Android 16 模拟器的触摸注入使用 Espresso 3.7.0；这是测试依赖的兼容性修正，未调整 Gradle、AGP、Kotlin 或 Compose 工具链。

## 11. 构建与运行

PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```

Debug APK 会生成在 `app/build/outputs/apk/debug/`，该目录不会进入 Git。Android Studio 直接打开仓库根目录、等待 Gradle Sync 后运行 `app`；不要仅因版本提示升级工具链。

## 12. 尚未实现、限制与建议下一阶段

- 已实现 Room、DataStore、真实台本/台本夹 CRUD、富文本与播放设置持久化。仍未实现 TXT/DOCX/Markdown 导入、文件选择器、局域网发现/二维码/网络通信、语音、账号、云端、回收站和多级台本夹。
- 当前富文本输入仍是基于 `BasicTextField` 的轻量实现；支持选择后加粗/斜体/下划线、粘贴、删除、替换和撤回，但没有完整富文本编辑器的输入法组合样式、重做或格式继承工具。
- 当前播放引擎已按 Compose 文本布局和时间轴运行，但仍属于本地前端会话；后续业务层应持久化 Session 快照，并由真实播放服务/远控事件驱动同一 reducer。
- `ChineseSpeechDurationEstimator` 是产品级启发式估算，不会替代未来逐词计时、语速训练或语音识别；导入富文本/多语种内容接入后应在同一模块扩展单位与停顿规则。
- 预览为保持可读性允许末尾省略号和缩放后的虚拟画布；正式播放不截断正文。后续可补充截图回归，覆盖更多字体缩放和 OEM Insets 组合。
- 未来富文本控件可提供逐段或逐词位置，用于控制端“附近文字”的精确窗口及更细粒度语义进度；当前进度仍以整段总滚动距离为基准。
- 建议继续补充真实设备截图回归、TalkBack、大字体、输入法矩阵与联网远控测试。
