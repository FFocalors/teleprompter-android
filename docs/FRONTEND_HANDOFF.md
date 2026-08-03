# Teleprompter Android 前端交接

## 1. 当前基线

- 保持单模块 `app`，`applicationId` / `namespace` 均为 `com.zhy20.teleprompter`。
- Gradle Wrapper 9.4.1、Android Gradle Plugin 9.2.1、Kotlin 2.2.10、Compose BOM 2026.02.01、Navigation Compose 2.9.6；未升级工具链、依赖仓库或模块结构。
- `compileSdk` 36.1、`targetSdk` 36、`minSdk` 26、Java 11。
- Git 历史保留；前端提交只在本地创建，不会自动 push。

## 2. Figma 实际读取范围

初始实现读取 Figma Make 文件“开始设计提示词”的根节点 `0:1`、最新可访问 Version 4 页面源码/样式，并逐页核对。

| Figma Make 页面 | 路由 | Compose 页面 |
| --- | --- | --- |
| Dashboard / 台本库 | `/` | `LibraryScreen` |
| Script Editor / 编辑 | `/editor/:id` | `EditorScreen` |
| Style Setup / 样式与启动 | `/style/:id` | `SetupScreen` |
| Prompter / 播放 | `/prompter/:id` | `PrompterScreen` |
| Remote / 控制端 | `/remote` | `RemoteScreen` |

该 Make 文件未暴露可枚举 Variables、独立 Component 集或完整原型连接元数据。全局设置、语言、暂停和控制端状态依产品说明补齐。后续定向优化不重新拉取或覆盖 Figma。

## 3. 已实现页面

- 台本库：全部/未分类/单层台本夹筛选、响应式卡片、新建、编辑、播放、控制端状态、设置和空状态。
- 编辑页：轻量富文本、选区级粗体/斜体/下划线、撤回、纯图标自动保存、固定工具栏、IME 避让与短文本滚动余量。
- 样式/启动页：显示预设、真实比例实时预览、字号、文字对齐、方向、镜像、节奏/目标时间、倒计时、提词线和控制端状态。
- 播放页：沉浸式显示、实际 Activity 方向锁定、模拟进度、紧凑时间信息、红色提词辅助、控制栏、暂停/恢复、完成和断线提示。
- 控制端：连接、等待、准备、播放、暂停、完成及 Mock 联动；附近文字预览遵循台本对齐方式。
- 全局设置与语言：新建台本默认样式、预设、文字对齐、方向、镜像及中英文会话切换。

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

## 5. 显示预设、提词线与旧数据

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
- 提词线按播放背景亮度选择亮红或深红。横线为 3 dp；提示条为半透明红色阅读带并含实线边缘，默认在屏幕上方约四分之一。

## 6. 文字对齐、实时预览与播放方向

### 文字对齐

- `PlaybackSettings` 新增 `textAlignment: PlaybackTextAlignment`，枚举为 `Start`、`Center`、`End`，默认左对齐。
- 样式页与全局设置均提供三个等宽分段按钮；所有文案在中英文资源中维护。
- `toComposeTextAlign()` 将纯 Kotlin 领域模型映射为 Compose `TextAlign`，供样式预览、播放页和控制端附近文字复用。

### 真实播放优先的预览

- `SetupPreview` 用 `PlaybackPreviewLayout` 根据方向选择 9:16 或 16:9 的比例，并在可用宽高内取最大尺寸，而不是固定的居中缩略框。
- 预览沿用最终播放的背景、文字、镜像、左右边距、文字对齐和红色提词辅助；预览区会尽量铺满所属区域。
- `maxVisibleLines()` 按可用高度和字号计算 3–12 行；`RichScriptText` 使用 `TextOverflow.Ellipsis`，长台本在底部自然显示省略号，短台本完整展示。

### 实际横竖屏播放

- `PrompterScreen` 从 `LocalView.context` 获取真实宿主 Activity（不会受语言本地化 Context 影响），进入播放后将 `requestedOrientation` 设为 `SCREEN_ORIENTATION_PORTRAIT` 或 `SCREEN_ORIENTATION_LANDSCAPE`。
- 离开播放页时恢复进入前的方向请求。`MainActivity` 在 manifest 声明 `orientation|screenSize|keyboardHidden` 配置变化处理，以免锁定方向时 Activity 重建、Mock 状态或导航回到首页。
- 样式页保存的方向在 `AppState.beginPlayback()` 再次选择台本时仍保留，因此预览比例、实际播放和暂停背景文本一致。

## 7. 既有播放与编辑交互

- 播放页顶部没有全宽进度条，仅显示百分比与已用/剩余时间；进度滑块仅出现在控制面板。
- 播放中边缘触摸由最内层 `PointerInput` 消费，中央区域才支持点击、双击和纵向微调；暂停或显示控制栏后恢复完整 App 内容区操作。
- 编辑器使用单一 `ScrollState`、IME/导航栏 Padding、尾部空间和 `BringIntoViewRequester`，短文本与键盘同时出现时仍可上滑并保持光标可见。
- 保存状态为纯图标：Initial 中性、Saving 不闪烁、Saved 绿色、Error 红色可重试；辅助功能文案在资源中提供。

## 8. 工程结构、导航与 Mock 状态

```text
com.zhy20.teleprompter
├── app                 # TeleprompterApp、可替换 AppState
├── core/design          # Token、主题、通用组件、文字渲染
├── core/model           # 纯 Kotlin 模型、富文本与播放设置
├── core/navigation      # 路由
├── core/util            # 格式化、触控策略、预览尺寸规则
├── data/fake            # 统一 Mock 数据
├── feature              # library/editor/setup/prompter/remote/settings
└── preview              # Compose Preview
```

路由：`library`、`editor/{scriptId}`、`setup/{scriptId}`、`prompter/{scriptId}`、`remote`、`settings`、`settings/language`。只传 `scriptId`；`AppState` 保存内存台本、播放设置、方向、对齐、进度、连接和保存状态，后续可替换为 Repository/Session。

## 9. Preview、测试与验证

- Preview 覆盖：四种显示预设的设置页、左/中/右对齐预览、横屏/竖屏播放、编辑器与保存状态、红线/红色提示条、控制端和设置页。
- 单元测试覆盖：四个预设、旧颜色回退、四种预设的红线对比、文字对齐映射、方向/对齐在开始播放后不被覆盖、预览比例和可见行数规则、富文本、保存状态与触控边缘死区。
- Android Compose UI 测试覆盖播放中央点击与边缘事件消费。构建会编译 AndroidTest APK。
- Android 16 模拟器的触摸注入使用 Espresso 3.7.0；这是测试依赖的兼容性修正，未调整 Gradle、AGP、Kotlin 或 Compose 工具链。

## 10. 构建与运行

PowerShell：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。Android Studio 直接打开仓库根目录、等待现有 Gradle Sync 后运行 `app`；不要仅因版本提示升级工具链。

## 11. 尚未实现与建议下一阶段

- 未实现 Room/DataStore、文件导入与解析、正式富文本控件、精确滚动播放引擎、局域网发现/二维码/网络通信、语音、账号和云端。
- 真实实现可用 Repository/Session 替换 `AppState`，并继续复用 `PlaybackEvent` 与 `ScriptContent` 的 block/span 结构。
- 实际滚动引擎应基于文字布局、时间轴和滚动位置；未来富文本控件可提供精确光标 bounds，替代当前输入字段级 `BringIntoViewRequester`。
- 建议继续补充真实设备截图回归、TalkBack、大字体、输入法矩阵与联网远控测试。
