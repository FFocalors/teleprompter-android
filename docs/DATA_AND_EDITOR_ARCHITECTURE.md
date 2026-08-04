# Teleprompter 数据与编辑器架构

## 1. 总览

生产数据路径为：

```text
Compose Screen
  -> feature ViewModel (SavedStateHandle / UiState)
  -> ScriptRepository / ScriptFolderRepository / SettingsRepository
  -> Room DAO / Preferences DataStore
```

`TeleprompterApplication` 持有单例 `DefaultAppContainer`。容器延迟创建 `TeleprompterDatabase`、三个 Repository；ViewModel 通过 Navigation BackStackEntry 对应的 Factory 获得依赖。Composable 不直接访问 DAO 或 DataStore，也未引入 Hilt/Koin/Dagger。

## 2. Room 版本 1

数据库：`TeleprompterDatabase`，文件名 `teleprompter.db`，`exportSchema = true`。版本化 schema 位于：

```text
app/schemas/com.zhy20.teleprompter.data.local.TeleprompterDatabase/1.json
```

### ScriptFolderEntity

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | String | UUID 主键 |
| `name` | String | trim 后保存，唯一索引；查询按 NOCASE 检测冲突 |
| `createdAt` / `updatedAt` | Long | epoch millis |
| `sortOrder` | Int | 默认追加到末尾 |

仅支持一级台本夹，无 `parentFolderId`。默认排序为 `sortOrder ASC, createdAt ASC`。

### ScriptEntity

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | String | UUID 主键 |
| `title` | String | 允许重复，保存时 trim，空值恢复为本地化“未命名台本” |
| `folderId` | String? | 外键，null 表示未分类 |
| `documentJson` | String | 版本化富文本 JSON |
| `plainText` | String | 从文档派生，便于列表与后续搜索 |
| `wordCount` | Int | 非空白字符数 |
| `normalEstimatedDurationSeconds` | Long | 中文语速估算缓存 |
| `playbackSettingsJson` | String | 每篇台本独立设置 JSON |
| `createdAt` / `updatedAt` | Long | epoch millis |

台本默认排序为 `updatedAt DESC, title COLLATE NOCASE ASC`。`folderId` 与 `updatedAt` 建立索引。

## 3. DAO 与事务

`ScriptDao` 提供全部、指定台本夹、未分类和按 ID 的 Flow，以及快照、新建、完整更新、标题/正文/所属台本夹/播放设置局部更新和永久删除。

`ScriptFolderDao` 提供排序后的全部 Flow、按 ID 快照、新建、重命名、排序、删除、名称冲突与下一个排序号查询。

删除台本夹由 `RoomScriptFolderRepository.deleteAndUncategorizeScripts()` 在 `RoomDatabase.withTransaction` 中执行：先把该夹所有台本的 `folderId` 设为 null 并更新时间，再删除文件夹。不会删除台本或正文。

## 4. Repository 职责

- `RoomScriptRepository`：UUID、时间戳、默认标题、默认 PlaybackSettings 复制、文档/设置序列化、plainText、字数、中文语速时长、外键存在性和数据异常转换。
- `RoomScriptFolderRepository`：名称 trim/空值校验、NOCASE 冲突、排序和删夹事务，并组合台本 Flow 计算 `scriptCount`。
- `DataStoreSettingsRepository`：读取/写入全局 PlaybackSettings JSON 与语言；读取 IOException 时安全回落默认值。

已知数据异常转换为 `ScriptNotFoundException`、`FolderNotFoundException`、`EmptyFolderNameException`、`FolderNameConflictException` 或 `DataAccessException`。UI 映射为 Snackbar、确认对话框或页面错误状态。

## 5. ScriptDocument JSON

数据库格式不依赖 Compose `AnnotatedString` 或 HTML。当前 schemaVersion 为 1：

```json
{
  "schemaVersion": 1,
  "paragraphs": [
    {
      "id": "paragraph-0",
      "spans": [
        {
          "text": "示例正文",
          "bold": true,
          "italic": false,
          "underline": false
        }
      ]
    }
  ]
}
```

`ScriptDocumentSerializer` 集中编码/解码。无效 JSON、未知 schemaVersion 或缺失结构会记录 warning 并返回包含一个空段落的安全文档，不会让页面崩溃。未来迁移应新增 schemaVersion 分支，并在 Room 版本迁移中批量转换需要改变存储语义的数据。

## 6. PlaybackSettings JSON 与 DataStore

`PlaybackSettingsSerializer` schemaVersion 为 1，保存：背景色、文字色、显示预设 ID、字号、方向、文字对齐、镜像、节奏模式、速度倍率、目标时长、倒计时、GuideMode 和辅助位置。解析时对字号、速度、时长和位置做安全范围约束；失败时使用调用方默认值。

Preferences DataStore 文件为 `teleprompter_settings`，键为 `playback_defaults_json` 与 `language_tag`。新建台本调用 `SettingsRepository.settings.first()` 复制当时的全局默认；之后全局设置变化不会更新已有台本。启动页只修改当前 `ScriptEntity.playbackSettingsJson`。

## 7. 编辑器状态机

`EditorUiState` 包含 `scriptId`、标题、`RichTextEditorState`（文档、选区、撤回栈）、Loading/Dirty、SaveState、`editRevision`、`savedRevision` 与错误。

```text
首次 Room 数据 -> Initial
编辑 -> revision + 1 -> Dirty + Saving
700 ms 无新编辑 -> 保存该 revision
保存成功且仍为最新 revision -> SavedAfterEdit / 绿色
保存成功但已有更新 revision -> 保持新草稿为 Dirty
保存失败 -> Error / 红色可重试
```

标题与正文使用同一 revision 快照。旧任务被取消；保存通过 Mutex 串行化。即使旧写入先完成，也只有与当前 `editRevision` 相同的快照能更新绿色状态，最新 revision 随后写入，不会被旧回执标记为已保存。

首次加载后，Room 的后续 emission 被视为保存回执；只要编辑会话已经建立，就不会无条件用数据库对象重建 TextFieldValue，从而保护本地 dirty 草稿。

返回、进入启动页、Lifecycle `ON_STOP`、Composable 释放会调用 `flush()`；ViewModel 清理时也会进行有时限的最后保存。失败时保持 Error，不执行返回/进入启动页回调，用户可点击红色保存图标重试。

## 8. 撤回

`RichTextEditorState` 在文本或样式变更前保存 `RichTextSnapshot(document, selection)`，会话内最多保留 80 步。撤回同时恢复段落、文字、粗体/斜体/下划线和选区；单纯移动光标不进入历史。关闭编辑页后撤回栈不持久化，本阶段不含重做。

## 9. 页面接入

- `LibraryViewModel` 组合台本与台本夹 Flow，输出 Loading/Empty/Content/Error，并执行全部 CRUD。
- `EditorViewModel` 使用 `SavedStateHandle["scriptId"]` 加载、编辑和保存真实文档。
- `SetupViewModel` 按 scriptId 加载每篇台本设置，700 ms 防抖写 Room，开始播放前 flush。
- Prompter 路由重新按 ID 加载真实台本，Activity 重建后不依赖导航参数中的复杂对象。
- `SettingsViewModel` 为 DataStore 提供乐观 UI 状态与延迟写入。
- `AppState` 继续管理播放引擎 Session 与远控演示状态；生产构造不含 Fake 数据。

台本在编辑/设置/播放期间被删除时，对应 Flow 发出 null，页面显示“台本不存在”并允许返回。

## 10. Fake 数据范围

`data/fake/FakeData.kt` 只被 `preview/AppPreviews.kt` 和测试代码显式使用。正式首次启动数据库为空，不执行 seed，也没有隐藏 debug 样例。

## 11. 文件导入

TXT、DOCX 与 DOC 导入已在 `data/importer/` 中实现，接入点如下：

```text
Compose（系统文件选择器）
  -> LibraryViewModel（UriFileMetadataReader 读元信息/流）
  -> ScriptImportCoordinator -> ScriptImportManager
  -> 具体 Importer（解析为 ScriptDocument）
  -> ScriptRepository.createFromDocument()（原子创建完整台本）
  -> Room 保存 -> 台本库刷新 -> 进入 editor/{scriptId}
```

- `ScriptImportManager` 根据文件扩展名和 MIME 选择 importer，内容与扩展名冲突时以实际内容为准；统一异常映射（不支持格式/损坏/加密/过大/过于复杂/无法读取/保存失败）。未来 Markdown importer 只需实现 `ScriptImporter` 接口并注册，不影响导航或数据库。
- `PlainTextScriptImporter`：`TextEncodingDetector` 支持 UTF-8（含 BOM）、UTF-16 LE/BE（含 BOM）和 GB18030 回退，严格解码不产生替换字符；统一换行，按连续空行切分段落、保留段内单换行；5 MiB 上限。
- `DocxScriptImporter`：把 DOCX 作为 ZIP，用 `XmlPullParser` 只读 `word/document.xml`（禁用外部实体/DTD）。段落 → `ScriptBlock.Paragraph`，Run 样式（粗体/斜体/下划线）→ `ScriptSpanStyle`，相邻同样式 span 合并；表格每行输出一个段落、单元格用制表符分隔；超链接只保留显示文字；Run 内 `w:br` 保留换行、`w:tab` 保留制表符。忽略图片、页眉页脚、批注、脚注尾注、文本框、公式、SmartArt、图表和页面布局。
- `DocScriptImporter`：`Ole2CompoundFile` 最小 CFB 解析器读取 `WordDocument` 与 `0Table/1Table`，`DocFibParser` 解析 FIB（`csw`/`cslw` 为 2 字节计数）和 CLX 片段表，解码 16 位（UTF-16LE）与压缩 8 位片段（`fc` 为实际偏移的 2 倍）。`\r` 切分段落，`\x07` 转制表符分隔单元格；控制字符清理后写入正文。基础样式仅在可靠可用时保留，否则降级为纯文本。
- Word 导入限制集中在 `WordImportLimits`：源文件 ≤ 20 MiB、DOCX 解压总量 ≤ 64 MiB、ZIP 条目 ≤ 4096、单条目 ≤ 16 MiB、段落 ≤ 50,000、Run ≤ 200,000、表格单元格 ≤ 100,000、最终字符 ≤ 2,000,000。加密文档、损坏文件、伪装文件均返回明确错误。
- `ScriptRepository.createFromDocument()` 一次插入完整 `ScriptEntity`：校验 `folderId`、标题 trim 与默认标题回退、序列化正文、派生 plainText/wordCount、估算中文语速时长、复制全局默认播放设置、`createdAt`/`updatedAt` 同源。
- 导入失败不会留下空台本或半成品；UI 通过 `ScriptImportState` 阻止重复提交并显示 Snackbar 错误。
- 只读取用户主动选择的文件：不申请存储权限、不复制原文件、不调用 `takePersistableUriPermission()`，导入完成后与原文件无关联。

## 12. 迁移原则与已知限制

- 不使用 `fallbackToDestructiveMigration()` 作为长期方案。数据库版本增加时提供显式 Migration，并保留所有历史 schema JSON。
- JSON schema 与 Room schema 分别版本化；字段语义变化时先兼容读取旧 JSON，再通过迁移或后台重写升级。
- 当前保存标题与正文是两个串行 DAO 更新，不是单 SQL 原子更新；失败会显示 Error，重试为幂等写入。后续可增加 `@Transaction` 的编辑快照更新。
- 当前轻量富文本编辑器不含重做、输入样式继承、跨段格式工具栏或 IME composition 专项逻辑。
- 文件导入已支持 TXT、DOCX 与 DOC；Markdown、批量导入、文件导出和原文件复制尚未实现。Word 导入不扩展富文本数据库结构（不新增样式字段），仅映射模型已支持的粗体/斜体/下划线。
- 本阶段仍不包含回收站、多级文件夹、搜索、云同步或真实远控。
