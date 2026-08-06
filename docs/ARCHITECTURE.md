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
- `data/importer/`：文件导入统一接口、TXT/Markdown/Word 解析、编码检测和导入协调。
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

## 手机远控（一对一同局域网）

远控层位于 `remote/` 和 `feature/remote/`。本阶段已经实现真实的一对一局域网 WebSocket 通信、二维码配对、握手、心跳、断线重连、状态快照同步和播放控制。

### 职责边界

- 提词端是唯一真实状态源：脚本、页面、播放设置和播放引擎都由提词端持有；控制端只发送命令请求，提词端校验、执行并返回最新快照。
- 远控模块不复制播放逻辑：控制命令经 `RemoteAppCoordinator` 转换为现有 `PlaybackEvent`/业务方法，快照从 `AppState` 与 `PlaybackEngineState` 派生。
- 页面与业务解耦：`RemoteScreen` 只接收 `RemoteUiState` 并发送 `RemoteUiAction`，不再直接修改 `AppState`。
- 网络层通过接口隔离：`RemoteTransport` 接口不暴露 WebSocket 类型；`WebSocketRemoteTransport` 是唯一接触 Java-WebSocket 的地方；`FakeRemoteTransport` 仅用于 JVM 单元测试和 Preview，不进入生产路径。

### 目录与数据流

```text
remote/model/      角色、连接状态（密封）、设备、会话状态、提词端快照
remote/protocol/   协议版本、消息（ClientHello/ServerAccepted/ServerRejected/CommandRequest/
                   CommandResult/SnapshotUpdate/HeartbeatPing/Pong/DisconnectNotice/ProtocolError）、
                   命令校验、RemoteJsonCodec（org.json 显式编解码）
remote/pairing/    配对载荷模型 + URI 编解码 + 安全随机凭据生成
remote/network/    LocalNetworkAddressProvider（ConnectivityManager 取当前局域网 IPv4）
remote/transport/  RemoteTransport 接口 + WebSocketRemoteTransport（Server/Client）+ FakeRemoteTransport
remote/session/    RemoteSessionRepository + DefaultRemoteSessionRepository + 快照工厂 + 会话凭据
feature/remote/    RemoteViewModel（UI 状态/动作）、RemoteScreen、RemoteUiMapper、RemoteQrGenerator
app/               RemoteAppCoordinator（命令→业务方法→结果）、RemoteStartPlaybackHandler、
                   AppContainer 注入（生产用真实 WebSocket Transport）
```

控制端命令的完整链路：

```text
RemoteScreen → RemoteViewModel → RemoteSessionRepository → WebSocketRemoteTransport
  → JSON 编解码 → Repository incoming command → RemoteAppCoordinator
  → 状态校验 → AppState / Setup 保存门控 / 导航
  → CommandResult + 新快照 → Repository → RemoteScreen
```

### 配对与握手

- 提词端 `startWaiting`：生成安全随机的 `sessionId` 与至少 128 bit 的 `pairingToken`（仅存内存），启动 WebSocket Server，把 `host + port + session + token + 过期时间` 编码成 `teleprompter://pair?...` 二维码（默认 5 分钟有效）。
- 控制端扫码：二维码扫描与相机权限的 ActivityResult launcher 创建在 NavHost 之上（与文件导入选择器一致，因为 NavHost 目的地不提供 `LocalActivityResultRegistryOwner`），扫码字符串经应用层待处理状态交给 `RemoteViewModel.onScannedContents`，由 `RemotePairingPayloadCodec` 校验 scheme、IPv4、端口、token、session、版本与过期时间；相机权限被拒绝时可手动输入。
- 提词端校验 `ClientHello`：协议版本 → session → token → 过期 → 单控制端 → 设备信息；失败返回 `ServerRejected` 并关闭该连接，不改变当前会话。
- 握手成功：提词端返回 `ServerAccepted`（唯一 `connectionId` + 内存 `resumeToken` + 当前快照），并立即消费配对 token（旧二维码失效）；控制端收到接受前不允许发送命令。
- 单控制端：已有控制端时新连接返回 `AlreadyConnected` 拒绝。

### 心跳、断线与重连

- 应用层心跳 5 秒一次；连续 15 秒未收到对端有效消息判定连接丢失。心跳不进入命令执行层，随连接生命周期取消。
- 提词端断线：本机播放不暂停、不退出、不返回台本库，仅更新连接状态；宽限期内保留原控制端身份与恢复凭据。
- 控制端断线：使用 `sessionId + resumeToken` 以 1s/2s/4s/8s 指数退避自动重连，总窗口不超过 30 秒；重连成功立即收到完整快照。

### 主动断开（区别于意外掉线）

三种情况明确区分：

- **意外掉线**：进入自动重连，保留恢复凭据，控制端显示"正在重连"。
- **控制端主动断开**（`disconnectFromPrompter`）：发送 `DisconnectNotice` → 停止客户端 → 设置 `userInitiatedDisconnect` → 禁止自动重连 → 清除 `connectionId`/`resumeToken`/旧快照 → 返回扫码页面。
- **提词端主动断开**：
  - `disconnectController`（断开当前控制端）：发 `DisconnectNotice`、关闭旧连接、销毁旧凭据；Server 继续运行并轮换新 `sessionId`/`pairingToken`，生成新二维码回到等待状态；本机播放不受影响。
  - `stopHosting`（停止远控）：断开控制端、关闭 Server、停止心跳与等待任务、清除配对数据，返回 Disabled。

实现要点：`userInitiatedDisconnect` 标志在显式断开时置位并在新一次扫码/等待时重置；`sessionGeneration` 单调递增用于过滤旧连接的迟到回调；心跳与重连 Job 都在断开路径取消；`DisconnectNotice` 发送失败不阻止本地关闭。

### 状态快照与命令执行

- 每次页面/台本/播放状态/倒计时/速度变化立即发布快照；播放中逐帧更新限制为每 250 ms 一次。`revision` 单调递增，控制端忽略小于或等于当前 revision 的快照。
- 每条命令都有唯一 `commandId`，提词端去重（重复命令返回上次结果，缓存上限 256 条）。
- 命令按当前播放状态校验（如 `PausePlayback` 仅在 Playing 时执行），非法状态返回结构化 `CommandResult`，不强行改页面。
- 开始播放接入 Setup 保存门控：控制端 `StartPlayback` → 协调器校验位于对应 Setup 页 → `RemoteStartPlaybackHandler` 转发给可见的 `PersistentSetupScreen` → `SetupViewModel.flushNow()` 保存成功后才 `beginPlayback` + 导航；保存失败不导航并返回 `SetupSaveFailed`。

### 当前朗读文本（结构化阅读窗口）

- 控制端用户可见标题为"当前朗读文本"（内部字段仍叫 `nearbyText`/`readingText`，未做全仓库符号迁移）。数据从正式播放页的 `PrompterViewport` 真实 `TextLayoutResult` 生成，**字符 offset 一律来自该 TextLayoutResult 对应的同一份 canonical AnnotatedString**（`RichScriptText` 渲染文本），与可见文字零错位；网络/Repository 层只接收纯文本 + offset。
- 阅读锚点为本次播放开始时捕获的固定 `PlaybackReadingAnchor`（提词线开启用其位置、关闭用视口顶部 0.25），移动/开关视觉提词线不改变锚点，因此控制端文字不会因拖线跳段。
- 阅读窗口采用滞后策略：约 6 个视觉行上下文（窄屏/大字体 4 行，字符上限 360），阅读行在窗口内移动时只更新 `activeStart/activeEnd`，接近后沿 70% 时才向前滑动生成新窗口；窗口文本为原始文本连续切片，只保留源文本真实换行。
- 上报结构 `RemoteReadingText{ text, activeStart, activeEnd, sourceStartOffset, sourceEndOffset, layoutRevision }` 经快照 JSON 编解码传输，解码时对非法 offset 做边界钳制；`RemoteSnapshotFactory`/网络层不引用 Compose 类型。
- 控制端用 `AnnotatedString` 对 active 范围做轻量高亮，正文区域固定高度按 `lineHeight × 行数` 计算，进度条与时间行不随文本长度跳动。

### 播放初始位置（正式播放 vs 预览）

- `PlaybackLayoutCalculator` 显式区分 `PlaybackLayoutMode.Preview`（经典底部进入，`0.82` 起始）与 `PlaybackLayoutMode.LivePlayback`（使用捕获的 `PlaybackReadingAnchor`）。
- 正式播放开始：提词线开启 → 第一行位于锚点线下约 1.5 个真实行高处（按实测 `lineHeight` 计算）；提词线关闭 → 第一行位于正文视口顶部约四分之一处。
- 锚点在 Session 中不可变：后续移动/开关提词线只更新视觉辅助层与台本设置，不调用 `PlaybackEngine.reconfigure`，正文 progress/elapsed/remaining/scrollDistance 全部保持不变。
- 样式设置页实时预览仍走 Preview 路径，第一行位置、提词线逻辑、横竖屏画布均与修改前一致。

### 连接入口

- 首页/台本库保留进入远控页面的入口（`RemoteStatusEntryCard`）。
- 远控页首屏选择角色：本机作为提词端（显示二维码等待）或本机作为控制端（扫码/手动输入）。
- 样式设置页只显示只读连接状态卡片（`RemoteStatusReadOnlyCard`）。
- 播放页在断线/重连时显示"本机继续播放"提示。

### 安全与边界

- 局域网 WebSocket 当前明文传输，UI 和文档提示仅在可信 Wi-Fi 或个人热点使用；不把台本全文传输给控制端。
- 配对 token、resume token、connection id 都只保存在内存，不写入 Room/DataStore，不记录到日志。
- 控制端不支持多控制端、公网、云端、后台常驻；本阶段不新增前台服务。
- 未来升级 `targetSdk 37` 时需要重新适配局域网运行时权限。

### 当前边界

- 仅支持一对一、仅 IPv4、不支持 mDNS 自动发现、不支持公网/云端。
- 应用退到后台不承诺长期保持连接；回到前台后按当前状态尝试恢复。
- 双设备人工实测尚未在本仓库完成（需要两台 Android 设备）；真实 localhost WebSocket 集成测试已通过。

## 文件导入

文件导入走独立管道：Composable 仅启动系统文件选择器（`ActivityResultContracts.OpenDocument`，类型 `text/plain`、`application/octet-stream`、`application/msword`、Word OpenXML MIME、`text/markdown`、`text/x-markdown`）并把 `Uri` 交给 `LibraryViewModel`。ViewModel 通过 `UriFileMetadataReader` 读取 `OpenableColumns` 元信息和流，然后 `ScriptImportCoordinator` 调用 `ScriptImportManager` 选择 importer、校验大小、解析内容，最后 `ScriptRepository.createFromDocument()` 原子创建完整台本。

`ScriptImportManager` 默认注册四个 importer，按文件扩展名和 MIME 匹配，内容与扩展名冲突时以实际内容为准：

- `PlainTextScriptImporter`：检测 UTF-8（含 BOM）、UTF-16 LE/BE（含 BOM）和 GB18030，统一换行，按连续空行切分段落并保留段内单换行。文件上限 5 MiB。
- `DocxScriptImporter`：把 DOCX 作为 ZIP 读取，只解析 `word/document.xml`（XML 拉取解析，禁用外部实体/DTD）。通过一组简单深度计数器跳过表格、内容控件、字段（`fldSimple`/`fldChar`/超链接）、绘图、图片、对象和文本框；只把主文档中直接的普通 `w:p` 提取为单个无样式 `ScriptSpan`，`w:br` 转换行、`w:tab` 转空格。源文件 ≤ 20 MiB，解压总量 ≤ 64 MiB。
- `DocScriptImporter`：用最小 OLE2/CFB 解析器读取 `.doc`，按 FIB 定位 `WordDocument` 与 `0Table/1Table` 中的 CLX 片段表，解码 16 位（UTF-16LE）与压缩 8 位片段。先在完整文本上运行字段状态机（`0x13` 进字段、`0x15` 出、支持嵌套）跳过所有自动字段，再按 `\r` 切分段落并丢弃含 `\x07` 表格标记的整个段落；每个普通段落输出一个无样式 `ScriptSpan`。
- `MarkdownScriptImporter`：复用 TXT 的读取与编码识别（UTF-8/UTF-16/GB18030、BOM 去除、5 MiB 限制），解析交给纯 JVM 的 `MarkdownSubsetParser`。第一个一级标题（ATX `#` 或 Setext `===`）作为台本标题并从正文移除；二级至六级标题与 Setext 二级标题转为正文普通段落；空行分段、段内单换行保留。粗体、斜体、删除线、代码、列表、引用、链接、图片、表格、HTML、YAML Front Matter、脚注、水平分隔线和数学公式等特殊格式直接拒绝（`UnsupportedMarkdownSyntax`），不做部分降级解析。

所有 importer 都是纯 JVM 可测的；`TextEncodingDetector` 严格解码，不产生替换字符。导入失败映射为 `ScriptImportState.Error`（区分不支持格式、损坏、加密、过大、过于复杂、无法读取、保存失败、Markdown 特殊格式），通过 Snackbar 提示；用户取消系统选择器不显示错误。导入成功后沿用现有导航进入 `editor/{scriptId}`，任何失败都不创建台本。

## 测试

- JVM 测试覆盖模型、中文语速、富文本映射、序列化、编辑器保存、播放引擎、播放布局和触控策略。
- 远控 JVM 测试覆盖 JSON Codec、配对载荷、握手校验、Repository 会话、命令去重与结果、UI 状态映射、Setup 保存门控，以及**真实 localhost WebSocket 集成**（Server/Client 经真实 Socket 完成握手、命令、快照和断开）。
- AndroidTest 覆盖 Room 内存数据库、Compose 视口、提词辅助和播放触控。
- Preview 使用 `data/fake` 中的统一 Mock 数据，不访问真实数据库或网络。

## 当前边界

TXT、DOCX、DOC 与 Markdown（纯文字子集）文件导入已完成，Word 仅提取普通正文段落（样式、表格、图片、目录、字段等均跳过）；更完整的 Markdown 语法支持尚未实现。手机远控已实现一对一局域网 WebSocket、二维码配对、握手、心跳、断线重连、单控制端限制、状态快照同步和真实播放命令，并接入 Setup 保存后启动；仅支持 IPv4 与单控制端，不支持公网/云端/mDNS 自动发现，局域网 WebSocket 当前不加密，双设备人工实测尚未在本仓库完成。
