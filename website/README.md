# 映辞 · Elocue (Teleprompter Android) 官网项目

这是开源 Android 提词器应用「**映辞 · Elocue**」的独立官方网站代码。

网站采用**现代极简工具应用设计风格（参考 Linear / Raycast / Zed 极简工业质感）**，零 Emoji、纯净灰阶与精密排版，全面展现产品真实功能与技术实力。

---

## 目录结构

```text
website/
├── index.html               # 官网首页（Studio 提词舞台、Bento Grid 便当盒特性、系统指标、下载终端）
├── details.html             # 更多详情页（工程规范档案、技术拓扑、16项核心依赖、游标同步算法、权限白皮书）
├── README.md                # 官网使用与部署指南
├── css/
│   ├── main.css             # 全局极简灰阶 Design Tokens、1px 细线边框、导航、页脚、模态框、Toast
│   ├── home.css             # 首页专用样式（Studio 视口舞台、Bento Grid 便当盒网格、下载终端卡片）
│   └── details.css          # 详情页专用样式（技术路线流程、依赖矩阵表格、架构拓扑卡片）
├── js/
│   ├── config.js            # 【统一配置中枢】全站版本号、下载地址、系统要求、GitHub/GitCode 链接
│   ├── main.js              # 全局交互（数据绑定、移动端抽屉、下载弹窗、代码复制）
│   └── details.js           # 详情页交互（浮动粘性锚点导航滚动跟随高亮）
└── assets/
    ├── favicon.svg          # 矢量图标
    ├── logo.png             # 高清应用 Logo 图标
    └── screenshots/         # 截图存放目录（预留供后续放置高清实机截图）
        └── .gitkeep
```

---

## 版本与下载链接集中管理

发布新版本或更新下载链接时，**只需修改 `website/js/config.js`**：

```javascript
const APP_CONFIG = {
  appName: "映辞 · Elocue",
  englishName: "Teleprompter Android",
  version: "开发中",
  versionCode: null,
  releaseStatus: "开发中",
  apkSize: "",
  minAndroidVersion: "Android 8.0 (API 26) 及以上",
  githubRepoUrl: "https://github.com/FFocalors/teleprompter-android",
  githubReleasesUrl: "https://github.com/FFocalors/teleprompter-android/releases",
  gitcodeReleaseUrl: "https://gitcode.com/FFocalors/teleprompter-android/releases",
};
```

---

## Tencent EdgeOne Pages 部署指南

本站点专为 **Tencent EdgeOne Pages** 优化设计，零编译依赖，开箱即用。

在腾讯云 EdgeOne Pages 控制台创建站点并关联 Git 仓库后，按如下方式配置：

| 配置字段 | 填写推荐值 | 说明 |
| :--- | :--- | :--- |
| **Framework Preset (框架预设)** | `None` / `自定义` / `静态网站` | 无构建需求，纯静态直出 |
| **Root Directory (根目录)** | `website` | 指向仓库内的官网子目录 |
| **Install Command (安装命令)** | *(留空)* | 无需执行任何依赖安装 |
| **Build Command (构建命令)** | *(留空)* | 无需执行任何编译打包命令 |
| **Output Directory (输出目录)** | `.` 或 *(留空)* | 直接以 `website` 目录作为站点根路径 |

---

## 本地启动预览

在终端进入 `website` 目录：
```powershell
python -m http.server 8080 --directory website
```
浏览器访问：`http://localhost:8080`
