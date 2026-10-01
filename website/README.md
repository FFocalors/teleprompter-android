# 映辞 · Elocue (Teleprompter Android) 官网项目

这是开源 Android 提词器应用「**映辞 · Elocue**」的独立官方网站代码。

网站的视觉语言取自产品运行时的界面本身：近黑提词面、台词白，以及那条跟随语速的**红色阅读基准带**。这条带子是全站唯一的结构装置——首页标题落在带上，首屏右侧是一个可以真的按播放的迷你提词器。配色分工明确：**红色只用于结构与阅读带，橙色只用于可点击的控件**。

---

## 目录结构

```text
website/
├── index.html               # 官网首页（提词器实时演示、三组功能、三个取舍、下载）
├── details.html             # 技术档案页（技术路线、依赖清单、系统架构、游标算法、权限白皮书、源码构建）
├── README.md
├── css/
│   ├── main.css             # Design Tokens、字体声明、导航、页脚、按钮、弹窗、Toast、全站动效降级
│   ├── home.css             # 首页（首屏骨架、功能三行、取舍账本、下载面板）
│   ├── prompter.css         # 播放屏复刻组件（见下节）
│   └── details.css          # 详情页（流水线、表格、架构说明、代码块）
├── js/
│   ├── config.js            # 【统一配置中枢】版本号、下载地址、系统要求、GitHub/GitCode 链接
│   ├── i18n.js              # 四语言文案表（zh-CN / zh-TW / en / ja），按 key 字母序排列
│   ├── main.js              # 全局交互（配置注入、移动端抽屉、下载弹窗、代码复制、首屏提词器）
│   └── details.js           # 详情页交互（目录条滚动跟随高亮）
└── assets/
    ├── fonts/               # Archivo 拉丁子集（自托管，含 OFL 授权）
    ├── screenshots/         # 真机截图，由首页功能演示区引用
    ├── favicon.svg / .ico / .png
    └── logo.png
```

---

## 播放屏复刻组件 (css/prompter.css + js/main.js)

首页首屏那台可以真的按播放的提词器，是 Android 播放屏的 HTML 复刻。参数不是照着截图估的，全部来自工程源码：

| 来源 | 决定什么 |
| :--- | :--- |
| `feature/prompter/PlaybackControlBar.kt` | 控制条的两行布局、各控件尺寸、窄屏（<640dp）的堆叠分支 |
| `core/design/components/PrompterGuide.kt` | 引导条三态：关闭 / 3dp 横线 / 56dp 高亮条（引导色 26% 叠在文字**之上**） |
| `core/design/DesignSystem.kt` | 配色、圆角、间距常量 |
| `core/model/Models.kt` | 播放屏背景 `#121719`、正文字号 64sp、引导色 `#FF453A` |
| `core/model/ChineseSpeechDurationEstimator.kt` | 滚动时长：255 字/分基准 + 标点停顿 |
| `core/util/PlaybackEngine.kt` | `offset = start + (end - start) × progress` 的端点算法 |

### 尺寸单位与两个坑

组件内所有尺寸用 `--u` 表示 1dp，`--u = max(100cqi / 1143, 0.75px)`：容器查询单位保证等比缩放，1143dp 是真机截图实测的屏宽（3000px ÷ 2.625 密度），窄屏兜底到 0.75px 以免标签小到看不清。阅读区高度是页面自选的（`--tp-vh`，桌面 620dp、窄屏 760dp），因为真机的 744dp 放进首屏太占地方。

改这里要避开两件事：

1. **不要动 1143 这个基准**。它决定「每行中文字数」「字符与控件的比例」，动了就和真机对不上了。想让组件整体变大变小，改的是它在页面里占多宽。
2. **≥2dp 的描边一律用 `inset box-shadow`，不要用 `border`**。Chromium 会把小数 `border-width` 向下取整成整数 CSS px，而本组件 1dp ≈ 0.95px，所以 `border: 2px` 实际只画 1px，环和描边全部细一半。box-shadow 的 spread 接受小数且带抗锯齿。

### 与真机的已知差异

| 差异 | 原因 |
| :--- | :--- |
| 引导条绝对位置在内容区约 0.48，截图是 0.29 | 阅读区比真机矮；`applyGuideFloor()` 会按控制条卡片的实际高度抬一个下限，保证带子不被卡片盖住（窄屏卡片会堆成四行、高近一倍） |
| 开场进度约 29%（已用 0:15 左右）而不是 0:00 | 源码是「首行落在锚点**下方 1.5 行**」（`initialTextOffsetLines`），照做之后进度 0 时带子是空的、文字在下面一截；为了让静止状态也是「带子里有字」，初始进度直接落在首行进带子的位置 |
| 控制条常驻不隐藏 | App 播放 3 秒后会自动隐藏控制条、点一下才出现；网页上常驻，否则演示没有可操作的入口 |
| 播完 2.6 秒后自动回到开头重播 | App 播完停在结尾且主按钮变灰禁用；网页上停住等于演示变成死图 |
| 长按「✕」是回到开头并暂停 | App 里是退出播放，网页上没有上一页可退 |
| 演示文稿是产品介绍文案 | 不是截图里那份演讲稿 |
| 「已用」按 `进度 × 总时长` 推算 | App 的「已用」是累计播放时间，拖动进度后两者会不一致 |

已按源码实现、容易被误认为 bug 的两处：**引导条叠在文字之上**（26% 透明度，所以带子里的字偏粉），以及**锚点在会话开始时冻结**——暂停时拖动「提词线位置」只移动带子，文字不动。

---

## 字体

页面**不请求任何外部字体服务**。拉丁字形使用自托管的 Archivo 变量字体（仅拉丁子集，34 KB），中文交回系统中文字体（PingFang SC / HarmonyOS Sans SC / MiSans / 微软雅黑等）。

字体栈的顺序不能颠倒：把中文字体放在拉丁字体之前，英文的撇号与引号会取到全角字形。具体见 `css/main.css` 顶部的 `@font-face` 与 `:root` 中的 `--font-display` / `--font-sans`。

Archivo 采用 SIL Open Font License 1.1，授权全文见 `assets/fonts/OFL.txt`。

---

## 文案与多语言

所有可见文案都通过 `data-i18n` 属性绑定到 `js/i18n.js` 中的键。**改动 HTML 文案时必须同步修改四种语言对应的值**，否则切换语言会把新文案覆盖回旧翻译。

需要注意的约束：`i18n` 通过 `el.textContent` 写入，因此 `data-i18n` 元素内部不能再嵌套标签（会被清掉）。

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

`data-bind` 会把配置值写进页面。**注意 `data-bind` 在 i18n 之后执行**，所以同一个元素上不要同时挂 `data-bind` 和 `data-i18n`——否则无论切到哪种语言，都会显示配置里的中文值。

下载按钮的链接由 `gitcodeReleaseUrl` 注入；当该值为空时，点击会弹出「发布状态说明」弹窗而不是跳转。

---

## Tencent EdgeOne Pages 部署指南

本站点专为 **Tencent EdgeOne Pages** 优化设计，零编译依赖、零外部请求，开箱即用。

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

在仓库根目录执行：

```powershell
python -m http.server 8080 --directory website
```

浏览器访问 `http://localhost:8080`。
