# 变更记录

本文件采用简化的 Keep a Changelog 结构。项目尚未发布正式版本，因此暂不使用版本号或发布日期。

## [未发布]

### 新增

- 建立 Kotlin + Jetpack Compose 单模块 Android 提词器应用。
- 支持本地台本、单层台本夹、Room 持久化和版本化富文本文档。
- 支持粗体、斜体、下划线、撤回、防抖自动保存和保存失败重试。
- 支持播放预设、字号、对齐、方向、镜像、速度/目标时间、倒计时和提词辅助。
- 支持沉浸式播放、暂停恢复、进度调整、完成状态和本地 Mock 控制端页面。
- 手机远控基础架构：提词端/控制端角色、密封连接状态、协议消息、命令校验与去重、Session Repository 和可注入的 Fake Transport；控制端命令经完整链路驱动真实播放（控制端 UI 与业务状态解耦，提词端状态快照与命令协调层就绪）。
- 真实一对一局域网远控：提词端 WebSocket Server + 控制端 Client（Java-WebSocket）、一次性二维码配对与握手（`teleprompter://pair` URI + ZXing 生成/扫码）、单控制端限制、心跳与断线自动重连（有限指数退避）、状态快照同步（250 ms 节流 + revision 乱序过滤）、真实播放命令（状态校验 + CommandResult），并接入 Setup 保存后启动（`SetupViewModel.flushNow` 门控）。
- 远控协议 v2：ClientHello/ServerAccepted/ServerRejected/CommandRequest/CommandResult/SnapshotUpdate/HeartbeatPing/Pong/DisconnectNotice/ProtocolError，`RemoteJsonCodec` 用 org.json 显式编解码并对缺失字段、非法数字、超长字符串、未知类型做结构化校验。
- 局域网地址解析：`LocalNetworkAddressProvider` 基于 ConnectivityManager 获取当前局域网 IPv4，避免 loopback/link-local，支持多候选地址。
- 远控测试：新增 JSON Codec、配对载荷、握手校验、Repository 会话、命令去重与结果、UI 状态映射、Setup 保存门控和真实 localhost WebSocket 集成测试。
- 支持 TXT 文件导入：系统文件选择器、UTF-8/UTF-16/GB18030 编码识别、5 MiB 大小限制，导入后转换为 `ScriptDocument` 并进入编辑页。
- 支持 DOCX 文件导入：OOXML 直接解析，只提取主文档中的普通正文段落，段落内换行与制表符转换为换行与空格；文字样式、表格、图片、目录、字段、超链接等结构全部跳过。
- 支持 DOC 文件导入：OLE2 二进制解析，只提取普通正文段落；含表格标记（`\x07`）的段落整体丢弃，字段（TOC/PAGE/DATE/HYPERLINK 等）通过控制字符状态机跳过。
- Word 导入安全限制：源文件 ≤ 20 MiB、DOCX 解压总量 ≤ 64 MiB、ZIP 条目与单条目上限、段落/字符数上限、XML 事件与字段嵌套上限，XML 禁用外部实体；加密、损坏、超大或结构过复杂文档返回明确错误且不创建台本。
- 支持 Markdown 文件导入：`.md` / `.markdown`，复用 TXT 编码识别（UTF-8/UTF-16/GB18030）、BOM 去除和 5 MiB 大小限制；一级标题（ATX `#` 或 Setext `===`）作为台本标题，二级至六级标题与 Setext 二级标题作为正文普通段落，空行分段、段内单换行保留。
- Markdown 特殊格式拒绝：粗体、斜体、删除线、列表、任务列表、引用、代码块/围栏/行内代码、链接、图片、自动链接、表格、HTML、YAML Front Matter、脚注、水平分隔线、数学公式均返回“暂不支持”错误，不部分解析、不创建台本。
- 增加中文和英文字符串资源、Compose Preview、JVM 测试和 Android 测试。

### 修改

- 统一设置预览和真实播放的文本排版、滚动几何和提词线显示。
- 优化手机、平板、横屏和竖屏布局，以及播放控制栏的稳定位置。
- 使用 Preferences DataStore 保存全局默认播放设置和语言选择。
- 台本库主操作区同时显示”导入文件”与”新建台本”，手机改为底部双按钮操作栏，平板保持标题区右侧按钮。
- 控制端页面改用远控 ViewModel 与 Session 状态，移除演示状态选择器和直接修改 `AppState` 的逻辑；样式设置页远控卡片改为只读状态卡片，连接状态由 Session Repository 提供。
- 远控页新增角色选择（提词端/控制端）、二维码等待/扫码、手动连接输入和断线重连界面；开始播放改为”保存设置后启动”（本机与远控共用同一保存门控）。
- 控制端"当前朗读文本"改用**绝对阅读游标 + 阅读窗口**同步，废弃旧的"定位提词线附近视觉行 → 截取几行 → 发送字符串"算法作为状态源：新增 `PlaybackReadingTracker` 基于真实排版与固定阅读锚点计算全文绝对 UTF-16 阅读游标（首个 `lineBottom` 越过锚点的视觉行 + 行内亚字符进度，不再依赖 `getLineForVerticalPosition` 的选择语义）；新增 `ReadingWindowManager` 维护低频大窗口（目标约 700 字符、硬上限 1100，前后上下文约 30%/70%，前后向滞后阈值 18%/72%，窗口起止对齐自然换行且不切断 surrogate pair）。
- 阅读同步拆分为两类独立消息：低频 `ReadingWindowUpdate`（仅在窗口变化时发送）与高频 `ReadingCursorUpdate`（约 12–20 Hz、latest-only、相同游标不重发、Seek 跳变立即发送、重连后先发窗口再发游标），与普通快照的 250 ms 节流解耦。
- 控制端不再直接显示提词端字符串，而是新建 `ControllerReadingViewport`：用控制端自己的 `TextLayoutResult` 重新排版窗口文本（不保留平板视觉折行），把绝对游标映射到本机布局（`ControllerReadingViewportMath` 亚字符映射），用 `Animatable` 平滑滚动把阅读位置稳定在固定阅读锚点（约 28% 高度），区域高度固定约 5–6 行、短文本不缩小长文本不撑高；控制端不建立第二播放时钟。
- 协议新增 `ReadingWindowUpdate`/`ReadingCursorUpdate` 消息类型与 `RemoteJsonCodec` 编解码；`RemotePrompterSnapshot` 中的旧 `nearbyText`/`readingText` 字段标记为废弃且不再填充。

### 修复

- 修复旧"当前朗读文本"算法的五个根因：控制端仅显示约两行（视觉行窗口经手机重排后缩水）、文字越过阅读锚点后仍长时间滞留（`getLineForVerticalPosition` 在行间间隙的选择语义）、整块文本跳换（无连续滚动）、更新滞后（250 ms 快照节流）以及平板/手机视觉行不一致导致错位。
- 提词设置页底部"控制端状态"卡片接入真实远控入口：整张卡片可点击（带按压反馈与无障碍 click action），点击前先经 `SetupViewModel.flush()` 保存当前播放设置，保存成功才导航到现有远控页，保存失败不导航且不丢设置；与"本机开始播放"复用同一套离开前保存逻辑。
- 控制端"提词线附近文字"更名为"当前朗读文本"（中英文资源与 Preview 同步更新），并从静态小窗口升级为结构化阅读窗口 `RemoteReadingText`（窗口文本 + 当前朗读 active 范围 + 绝对源 offset + layout revision）。
- 阅读窗口扩大：约 6 个视觉行上下文（窄屏/大字体回退 4 行），字符上限 360；采用滞后窗口策略——阅读行在窗口内移动时只更新 active 范围、不整体替换文本，接近后沿（70%）时才向前滑动窗口。
- 统一字符 offset 数据源：阅读窗口始终从正式播放渲染的同一份 canonical AnnotatedString（`TextLayoutResult.layoutInput.text`）截取，与可见文本零错位；`TextLayoutResult` 只存在于 Compose 渲染层，上报到网络的是纯文本 + offset。
- 当前朗读范围仍以本次播放开始时的固定 reading anchor（提词线开启用其位置、关闭用视口顶部 0.25）定位，移动/开关视觉提词线不改变阅读锚点。
- 控制端"当前朗读文本"用 `AnnotatedString` 对 active 范围做轻量高亮（半透明主色背景 + 适中字重），固定高度按 `lineHeight × 行数` 计算，进度条与时间行不随文本长度跳动。
- 修复控制端扫码崩溃：把二维码扫描与相机权限的 ActivityResult launcher 提升到 NavHost 之上（与文件导入选择器一致），扫码结果经应用层待处理状态交给 RemoteViewModel 解析，避免在 NavHost 目的地内调用 `rememberLauncherForActivityResult`。
- 提词线调整与播放 Session 解耦：新增 `AppState.updateGuideOverlay`，播放中和暂停时调整 GuideMode/guideLinePosition 只更新视觉辅助层并持久化到当前台本设置，不再调用 `PlaybackEngine.reconfigure` 重建时间轴。
- 控制端页面重构：速度控制改为左右方形图标按钮 + 中央速度值（不再把 "−0.1×/+0.1×" 数字塞进按钮导致多余"0"）；微调/暂停/恢复/长按结束区域改为自适应布局（窄屏纵向排列），长按结束使用紧凑危险区；内容区可滚动并应用底部导航栏 Insets。
- 修复长正文预览从中部开始显示、播放起止位置不一致和正文排版被默认加粗的问题。
- 修复播放控制栏圆角反馈、暂停/完成状态触控和系统栏显隐导致的布局位移。
- 修复台本夹过多时首页侧栏管理区域被挤占的问题。

### 文档

- 增加 README、贡献指南、架构说明、项目状态和路线图。
- 增加 Apache License 2.0，并完善公开仓库安全忽略规则。
- 补充 TXT 文件导入的架构接入点、编码与大小限制说明。
- 补充 DOC/DOCX 导入的解析路径、安全限制与错误提示说明。
- 补充 Markdown 导入的支持子集、拒绝规则与标题转换说明。
- 补充手机远控基础架构的职责边界、目录与数据流，以及真实网络接入点说明。
