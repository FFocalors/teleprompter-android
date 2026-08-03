# Teleprompter Android 前端交接

## 1. 当前基线

- 保持单模块 `app`，`applicationId` / `namespace` 均为 `com.zhy20.teleprompter`。
- Gradle Wrapper 9.4.1、Android Gradle Plugin 9.2.1、Kotlin 2.2.10、Compose BOM 2026.02.01、Navigation Compose 2.9.6；本轮未升级工具链或仓库配置。
- `compileSdk` 36.1、`targetSdk` 36、`minSdk` 26、Java 11。
- 现有 Git 历史保留。本轮只创建本地提交，不会 push。

## 2. Figma 实际读取范围

设计入口为 Figma Make 文件“开始设计提示词”。初始实现读取了根节点 `0:1`、最新可访问 Version 4 的页面源码/样式，并逐页核对。

| Figma Make 页面 | 路由 | Compose 页面 |
| --- | --- | --- |
| Dashboard / 台本库 | `/` | `LibraryScreen` |
| Script Editor / 编辑 | `/editor/:id` | `EditorScreen` |
| Style Setup / 样式与启动 | `/style/:id` | `SetupScreen` |
| Prompter / 播放 | `/prompter/:id` | `PrompterScreen` |
| Remote / 控制端 | `/remote` | `RemoteScreen` |

该 Make 文件未暴露可枚举的 Variables、独立 Component 集或完整原型连接数据。全局设置、语言页、暂停态和控制端状态依同一产品说明补齐。本轮为定向视觉与交互修订，未重新拉取或覆盖 Figma。

## 3. 已实现页面

- 台本库：全部/未分类/单层台本夹筛选、响应式卡片、新建台本和台本夹、编辑、播放、控制端状态、设置和空状态。
- 编辑页：标题与轻量富文本正文、选区级粗体/斜体/下划线、撤回、自动保存图标、固定工具栏、IME 避让和短文本滚动余量。
- 样式/启动页：固定显示预设、实时预览、字号、方向、镜像、速度或目标时间、倒计时、红色提词线/条、位置和控制端状态。
- 播放页：沉浸式显示、模拟进度、紧凑时间信息、红色提词辅助、控制栏、暂停/恢复、完成和断线继续。
- 控制端：连接、等待、准备、播放、暂停、完成及速度/进度/结束的共享 Mock 联动。
- 全局设置与语言：默认播放配置、简体中文和 English 的会话切换。

## 4. 橙灰 Design System

统一入口：`core/design/DesignSystem.kt`。

| Token | 值 | 用途 |
| --- | --- | --- |
| `AppColors.Background` | `#0B0708` | 应用主背景 |
| `AppColors.Surface` | `#171415` | 卡片、顶部栏、控制面板 |
| `AppColors.SurfaceRaised` | `#232020` | 抬升表面、次级区域 |
| `AppColors.Primary` | `#FF8A00` | 主操作、选中态、强调 |
| `AppColors.PrimaryPressed` | `#D97000` | 主按钮按下态 |
| `AppColors.OnPrimary` | `#363636` | 橙底主按钮文字/图标 |
| `AppColors.TextPrimary` | `#FFF2DF` | 主文字与常规图标 |
| `AppColors.TextSecondary` | `#FFF2DF`（透明） | 次级文字 |
| `AppColors.Border` | `#363636` | 次级按钮、卡片边界 |
| `AppColors.GuideLineBrightRed` | `#FF3B30` | 深色播放背景的提词线/条 |
| `AppColors.GuideLineDeepRed` | `#C62828` | 浅色播放背景的提词线/条 |

- `PrimaryButton` 使用橙底、深灰文字，并以 `MutableInteractionSource` 提供较深的橙色按下态。
- 次级按钮和卡片采用深灰边界与白粉色文字；选中态仅使用橙色细边/低透明橙色，不大面积铺橙。
- `AppTypography`、`AppSpacing`、`AppShapes`、`AppElevation` 继续集中管理；`AppSpacing.EditorTail` 专用于编辑页尾部滚动空间。
- 播放文本区域不继承应用主题，而始终使用当前 `DisplayPreset` 的背景与文字色。

## 5. 显示预设与旧数据兼容

`DisplayPreset` 只包含 `id`、`name`、`backgroundColor`、`textColor`、`previewLabel`、`guideLineDarkModeColor` 和 `guideLineLightModeColor`，不再有 `isCustom` 或自定义色入口。

| ID | 名称 | 背景 / 文字 |
| --- | --- | --- |
| `black_white` | 黑底白字 | `#111319` / `#FFF2DF` |
| `white_black` | 白底黑字 | `#F1F3F6` / `#171A22` |
| `deep_blue_white` | 深蓝底白字 | `#273B55` / `#FFF2DF` |
| `deep_green_white` | 深绿底白字 | `#28483F` / `#FFF2DF` |
| `orange_charcoal` | 橙底深灰字 | `#FF8A00` / `#363636` |

- `DisplayPresetPicker` 同时用于样式页和全局默认设置：每张卡显示名称、真实背景/文字色、示例“台本 Aa”和明确的选中边框。
- 已移除自定义背景/字体颜色、颜色圆点、十六进制输入和“自定义”卡片；相关字符串资源也已删除。
- `PlaybackSettings.normalizedToDisplayPreset()` 会优先保留可识别的预设；旧背景/文字组合则按 RGB 距离映射到最接近的安全预设，无法匹配时回落为黑底白字，不会崩溃。

## 6. 提词线、进度与播放触控

### 红色提词辅助

- `DisplayPreset.guideLineColorForBackground()` 依背景亮度自动选择：深背景 `#FF3B30`，浅背景 `#C62828`。
- 横线模式是固定的 3 dp 红线；荧光模式为约 56 dp 高、28% 透明的红色阅读条，并带 3 dp 实线边缘。
- 默认位置约为屏幕上方四分之一；播放中锁定，暂停后可由现有控件调整并保存至 `PlaybackSettings`。

### 顶部进度与控制面板

- 播放页已删除全宽 `LinearProgressIndicator`，顶部只保留左侧百分比和右侧“已用 / 剩余”文字；镜像仅作用于正文，状态文字不镜像。
- 进度调整只在点击呼出的控制面板中出现，并使用 Material `Slider` 的可见滑块手柄，不会和提词线混淆。

### 边缘死区

- `PlaybackTouchPolicy` 在 App 内容坐标中建立中央有效矩形：左右约 8.5%（约束为 32–96 dp），上下约 6.5%（约束为 24–72 dp），并针对极窄尺寸封顶。
- `PlaybackTouchGestures` 的最内层 `PointerInput` 在 Main pass 先消费边缘 down、move、up；外层点击和纵向拖动识别器因此不会接收边缘手势。
- 仅在 **播放中且控制栏隐藏** 时挂载该手势层。中央区域：单击打开控制栏、双击暂停、纵向拖动小幅 Seek；控制栏显示或暂停时手势层移除，面板、暂停按钮、进度和提词线位置调整可使用完整 App 内容区。
- 实现只处理 App 内容，不试图拦截 Android 的系统返回、主页或导航手势。

## 7. 编辑页滚动、IME 与保存状态

### 短文本与键盘

- 固定的 `EditorHeader` 位于外层，正文只使用一个可控的 `ScrollState`，避免双重滚动竞争。
- `BasicTextField` 最小高度填满可见正文区，之后附加 `AppSpacing.EditorTail`；即使仅三行正文也可将文字上移。
- 正文容器应用 `imePadding()`、`navigationBarsPadding()`；编辑器获取焦点、IME 出现或选区改变时以 `BringIntoViewRequester` 在短延迟后请求可见区域，光标已经可见时不会强制跳动。

### 纯图标保存状态机

`SaveState` 为 `Initial`、`Saving`、`Saved`、`Error`；`SaveIndicatorState` 将其映射为图标呈现：

- Initial：白粉色保存图标；不显示任何保存文字。
- Saving：保持中性颜色，无旋转或文字闪烁。
- Saved：成功后显示绿色对勾；连续输入再次保存期间保持成功色，成功后稳定为绿色。
- Error：红色刷新图标；点击可模拟重试。

动态 `contentDescription` 位于中英文资源中，分别供屏幕阅读器读取未修改、保存中、已保存和保存失败；视觉界面不再出现“已保存 / Saving / Saved”等文字。

## 8. 工程结构、导航与 Mock 状态

```text
com.zhy20.teleprompter
├── app                 # TeleprompterApp、可替换 AppState
├── core/design          # Token、主题、通用组件、DisplayPresetPicker
├── core/model           # 纯 Kotlin 模型、SaveIndicatorState、富文本状态
├── core/navigation      # 路由
├── core/util            # 格式化、PlaybackTouchPolicy
├── data/fake            # 统一 Mock 数据
├── feature              # library/editor/setup/prompter/remote/settings
└── preview              # 主要 Compose Preview
```

路由：`library`、`editor/{scriptId}`、`setup/{scriptId}`、`prompter/{scriptId}`、`remote`、`settings`、`settings/language`。只传递 `scriptId`，业务配置由 `AppState` 维护。

`AppState` 仍是可替换的内存 Mock 会话：保存当前台本、富文本草稿、播放设置、全局默认值、进度、播放/连接/保存状态和语言。`updatePlaybackSettings` 与选中台本都会正规化预设，控制端与提词端共享同一状态模拟联动。

## 9. 测试、Preview 与验证

- 单元测试：富文本操作、播放设置和预设原子更新/旧颜色回退、明暗红色提词线选择、保存图标状态机、触控区域边界/播放与控制栏状态。
- 新增 Android Compose UI 测试 `PlaybackTouchGesturesTest`：验证中央点击回调、边缘点击与边缘纵向拖动会在 PointerInput 层被消费。
- Preview 已覆盖橙灰首页与编辑页、短文本编辑、保存图标中性/成功/错误、黑底白字/白底黑字/橙底深灰字播放、红线与红色提示条、控制端和设置页。
- 本轮按同会话要求不启动模拟器；仪器测试源码会参与 `assembleDebugAndroidTest` 编译验证。后续在真实设备或 AVD 上应重点回归三行文本+IME、中心/边缘触摸、横竖屏和字体缩放。

## 10. 构建与运行

PowerShell：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。Android Studio 可直接打开仓库根目录、等待既有 Gradle Sync 后运行 `app`。不要仅因版本提示升级工具链。

## 11. 尚未实现与接入点

- 未实现 Room/DataStore、文件导入与解析、正式富文本控件、真实滚动播放引擎、局域网发现/二维码/网络通信、语音、账号和云端。
- 真实实现可用 Repository/Session 替换 `AppState`，并继续以 `PlaybackEvent` 作为播放和远控的共同输入；`ScriptContent` 的 block/span 结构应保留，避免降级为纯文本或 HTML。
- 实际播放引擎需要接入排版高度、时间轴和滚动位置；真实远控应把共享 Mock 状态替换为可靠的连接和重连会话。

## 12. 已知限制与建议下一阶段

- 编辑器的可见性请求基于整个输入字段而非精确字符矩形；对于极长正文或复杂富文本布局，正式编辑器应提供精确光标 bounds。
- 当前自动保存、进度、连接和断线均为内存模拟，进程重启会丢失状态。
- 新的 Compose UI 测试已编译但未在本轮启动设备执行；建议下一阶段增加已连接设备的仪器测试、截图回归、TalkBack、大字体和输入法矩阵。
- Figma Make 是 Web 原型，Compose 保留其信息架构并采用 Android 响应式布局与 Insets，不复制网页绝对坐标。
