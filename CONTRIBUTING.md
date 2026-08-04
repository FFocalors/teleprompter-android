# 贡献指南

感谢你关注 Teleprompter Android。欢迎通过 Issue 报告问题、讨论需求，或提交 Pull Request 改进代码和文档。

## 开发环境

- Android Studio（支持 AGP 9.2.1）
- JDK 21 作为 Gradle 运行时
- Android SDK 36.1，最低运行版本 API 26
- Windows、macOS 或 Linux

## 本地验证

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Windows PowerShell 使用 `./gradlew.bat`。如需运行 Android 测试，请在已配置的模拟器或设备上执行 `./gradlew connectedDebugAndroidTest`。

## 分支与提交

- 从 `main` 创建描述清晰的功能分支，例如 `feature/script-import` 或 `fix/playback-layout`。
- 提交标题和正文优先使用中文。
- 标题应准确概括修改，正文分条说明改动内容和原因。
- 禁止使用“更新代码”“修复问题”等无法说明范围的空泛描述。
- 不要 squash 或重写他人的既有提交历史。

## Pull Request

- 描述改动内容、用户影响和必要的实现背景。
- 列出已执行的构建、测试和 lint 命令。
- 新增功能应补充对应的 JVM、Compose 或 Android 测试。
- UI 改动请说明手机、平板、横屏和竖屏的适配情况；有正式截图时再附图。
- 重大功能或存储格式变化，建议先通过 Issue 讨论方案。

## 代码约定

- 保持单模块 `app` 结构，不随意升级 Gradle、AGP、Kotlin 或 Compose。
- 页面、状态、模型、Repository 和通用组件按现有目录职责组织。
- 用户可见文字放入字符串资源，颜色、间距和形状优先使用 Design System Token。
- 不把数据库 DAO、DataStore 或网络访问直接写进 Composable。

## 安全与仓库卫生

- 不提交 `local.properties`、构建目录、IDE 本地状态、截图、日志、数据库文件或签名文件。
- 不提交 API Key、访问令牌、密码、私钥或包含个人路径的配置。
- 不在 Issue、提交信息或文档中公开本机目录和私有凭据。

如果发现安全问题，请不要在公开 Issue 中粘贴凭据或敏感内容，先通过仓库维护者提供的私下渠道联系。
