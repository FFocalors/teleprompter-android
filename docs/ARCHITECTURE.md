# 架构说明

## 总体结构

项目保持单个 `app` Gradle 模块，包名和 namespace 为 `com.zhy20.teleprompter`。代码按 UI、领域模型、数据访问和通用设计组件组织，不引入大型依赖注入框架。

```text
Compose Screen
    -> feature ViewModel / AppState
    -> Repository interface
    -> Room DAO or Preferences DataStore
```

`TeleprompterApplication` 创建 `DefaultAppContainer`，容器负责延迟创建 Room 数据库和 Repository。页面通过 ViewModel、SavedStateHandle 和导航参数中的 `scriptId` 恢复状态，Composable 不直接访问 DAO 或 DataStore。

## 工程目录

- `app/`：Android 入口、Application、导航和模块配置。
- `core/design/`：颜色、字体、间距、形状、主题、通用按钮、预设选择器和正文渲染。
- `core/model/`：`Script`、`ScriptFolder`、`PlaybackSettings`、富文本模型和播放状态/事件。
- `core/navigation/`：`library`、`editor/{scriptId}`、`setup/{scriptId}`、`prompter/{scriptId}`、`remote`、`settings` 路由。
- `core/util/`：时长格式化、中文语速估算、播放布局、播放引擎和触控策略。
- `data/local/`：Room Database、Entity 和 DAO。
- `data/repository/`：Room Repository、DataStore Repository 和领域异常映射。
- `data/serialization/`：版本化 `ScriptDocument` 和 `PlaybackSettings` JSON 序列化。
- `data/importer/`：文件导入统一接口、TXT 解析、编码检测和导入协调。
- `data/fake/`：只用于 Preview 和测试的 Mock 数据。
- `feature/`：台本库、编辑器、样式启动、提词播放、控制端和设置页面。
- `preview/`：统一 Mock 数据驱动的 Compose Preview。

## 核心领域模型

### Script 与 ScriptFolder

`Script` 保存标题、富文本正文、纯文本预览、所属台本夹、字数、中文正常语速预计时长、更新时间和独立播放设置。正文是 `ScriptContent`，由段落和带粗体/斜体/下划线标记的 `ScriptSpan` 组成，避免把未来富文本能力锁定为普通字符串。

`ScriptFolder` 只支持单层台本夹，包含名称、创建时间和派生的台本数量；不存在递归父子文件夹。

### PlaybackSettings

播放设置包括显示预设、背景色、文字色、字号、文字对齐、横竖屏、镜像、速度模式、速度倍率、目标时长、倒计时、提词辅助模式和提词线位置。全局设置是新建台本时复制的默认值，已有台本保留自己的设置。

### 播放状态与事件

播放状态由 `Idle`、`Preparing`、`Countdown`、`Playing`、`Paused`、`Finished` 和 `Exited` 表达；事件包括开始、暂停、立即恢复、倒计时恢复、速度调整、位置微调、结束和提词辅助切换。当前播放会话由 `PlaybackEngineState` 维护，页面通过 `AppState` 驱动本地状态。

## Compose 页面与 Design System

`AppTheme` 统一提供 `AppColors`、`AppTypography`、`AppSpacing`、`AppShapes` 和 `AppElevation`。页面使用 Material 3 组件和集中 Token，手机采用单栏/底部操作，平板横屏使用侧栏和双栏内容区。

`PrompterViewport` 同时服务样式页预览和正式播放，统一背景、状态区、正文排版、镜像、对齐和提词辅助。正式播放使用真实视口测量文本高度，预览使用目标设备画布缩放，避免两套排版规则产生明显差异。

## 数据持久化

Room 数据库 `TeleprompterDatabase` 当前为版本 1，schema 输出到 `app/schemas/` 并纳入版本控制。台本和台本夹由 DAO 以 Flow 暴露，Repository 负责 UUID、默认标题、派生纯文本、字数、预计时长、序列化和异常转换。删除台本夹通过事务将其台本移到未分类后再删除文件夹。

Preferences DataStore 保存全局默认播放设置和语言标签。读取异常时回落到默认设置，不影响台本正文。JSON schema 独立于 Room 版本，未知或损坏文档会安全回落到空段落。

## 编辑器保存流程

`EditorViewModel` 维护标题、`RichTextEditorState`、选区、撤回栈、脏状态和保存状态。文本或样式修改会增加 revision，700 ms 无新输入后保存当前快照；旧保存回执不能覆盖更新后的脏草稿。离开页面、生命周期停止和 ViewModel 清理时会尝试 flush，失败显示可重试状态。

## 播放视口与播放引擎

`ChineseSpeechDurationEstimator` 从 `ScriptContent.plainText()` 计算中文正常语速预计时长，避免富文本样式重复计数。`PlaybackLayoutCalculator` 根据真实状态区、正文视口和排版高度计算开始偏移、结束偏移和滚动距离。

`PlaybackEngine` 使用单调时间和播放段锚点推进进度，不按帧累计像素，因此刷新率变化不会改变时间轴。速度模式根据正常时长和倍率计算实际时长，目标时间模式直接使用目标时长。暂停、恢复、倒计时恢复和手动调整都会重新建立锚点，保持当前位置连续。

播放页根据设置请求 Activity 方向，进入播放时隐藏系统栏并使用临时覆盖行为；状态区和控制栏使用固定布局，不因系统栏短暂出现而重新排版。正文镜像只作用于脚本文本，状态信息、控制浮层、提词辅助和触摸语义保持正常方向。

## 文件导入

文件导入走独立管道：Composable 仅启动系统文件选择器（`ActivityResultContracts.OpenDocument`，类型 `text/plain`、`application/octet-stream`、`application/msword`、Word OpenXML MIME）并把 `Uri` 交给 `LibraryViewModel`。ViewModel 通过 `UriFileMetadataReader` 读取 `OpenableColumns` 元信息和流，然后 `ScriptImportCoordinator` 调用 `ScriptImportManager` 选择 importer、校验大小、解析内容，最后 `ScriptRepository.createFromDocument()` 原子创建完整台本。

`ScriptImportManager` 默认注册三个 importer，按文件扩展名和 MIME 匹配，内容与扩展名冲突时以实际内容为准：

- `PlainTextScriptImporter`：检测 UTF-8（含 BOM）、UTF-16 LE/BE（含 BOM）和 GB18030，统一换行，按连续空行切分段落并保留段内单换行。文件上限 5 MiB。
- `DocxScriptImporter`：把 DOCX 作为 ZIP 读取，只解析 `word/document.xml`（XML 拉取解析，禁用外部实体/DTD）。映射正文段落、Run 内换行与制表符、粗体/斜体/下划线、表格（行内制表符分隔单元格）、超链接显示文字和列表文字；忽略图片、页眉页脚、批注、脚注尾注、文本框、公式和页面布局。源文件 ≤ 20 MiB，解压总量 ≤ 64 MiB。
- `DocScriptImporter`：用最小 OLE2/CFB 解析器读取 `.doc`，按 FIB 定位 `WordDocument` 与 `0Table/1Table` 中的 CLX 片段表，解码 16 位（UTF-16LE）与压缩 8 位片段；按 `\r` 切分段落、`\x07` 处理表格单元格。格式不可恢复时降级为纯文本，正文可用优先于格式完整。

所有 importer 都是纯 JVM 可测的；`TextEncodingDetector` 严格解码，不产生替换字符。导入失败映射为 `ScriptImportState.Error`（区分不支持格式、损坏、加密、过大、过于复杂、无法读取、保存失败），通过 Snackbar 提示；用户取消系统选择器不显示错误。导入成功后沿用现有导航进入 `editor/{scriptId}`，任何失败都不创建台本。

## 测试

- JVM 测试覆盖模型、中文语速、富文本映射、序列化、编辑器保存、播放引擎、播放布局和触控策略。
- AndroidTest 覆盖 Room 内存数据库、Compose 视口、提词辅助和播放触控。
- Preview 使用 `data/fake` 中的统一 Mock 数据，不访问真实数据库或网络。

## 当前边界

TXT、DOCX 与 DOC 文件导入已完成；Markdown 导入尚未实现。真实局域网发现、二维码配对、WebSocket/TCP/UDP、远控同步、语音识别、账号和云端均未接入。控制端页面目前是本地 Mock 状态，后续可在不改变页面模型和事件接口的前提下替换为真实 Repository/Session。
