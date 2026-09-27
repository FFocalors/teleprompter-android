/**
 * 映辞 · Elocue (Teleprompter Android)
 * 官网全局集中配置文件
 * 
 * 以后发布新版本时，仅需修改本文件中的配置项，
 * 首页与更多详情页中的版本号、下载链接、发布状态与系统要求将自动全站同步更新。
 */
const APP_CONFIG = {
  // 产品基本信息
  appName: "映辞 · Elocue",
  englishName: "Teleprompter Android",
  tagline: "为创作者而生的现代化开源 Android 提词器",
  subTagline: "纯本地运行 · 丰富文档导入 · 选区富文本排版 · 沉浸式视口播放 · 局域网双机远控",
  
  // 版本发布信息（当前处于开发中，未发布正式 Release 安装包）
  version: "开发中",
  versionCode: null,
  releaseStatus: "开发中",
  releaseBadge: "Dev",
  releaseDate: "",
  apkSize: "",
  
  // 平台与兼容性要求
  minAndroidVersion: "Android 8.0 (API 26) 及以上",
  targetAndroidVersion: "Android 16 (API 36)",
  compileSdkVersion: "36.1",
  architecture: "arm64-v8a / armeabi-v7a / x86_64",
  
  // 开源与许可
  license: "Apache-2.0 License",
  githubRepoUrl: "https://github.com/FFocalors/teleprompter-android",
  githubReleasesUrl: "https://github.com/FFocalors/teleprompter-android/releases",
  
  // 国内下载渠道
  gitcodeReleaseUrl: "https://gitcode.com/FFocalors/teleprompter-android/releases",
  
  // 核心功能点统计或提炼
  highlights: [
    { title: "纯本地优先", desc: "台本数据与播放配置完整保存在设备本地，无云端中转，零隐私泄漏风险。" },
    { title: "多格式导入", desc: "原生解析 TXT、Word (DOC/DOCX) 与 Markdown，多字符编码智能适配。" },
    { title: "沉浸视口播放", desc: "精确视口排版，支持速度模式、目标时间模式与提词箱分光镜镜像翻转。" },
    { title: "局域网双机远控", desc: "无需公网与账号，扫码即连。基于绝对阅读游标与滑动窗口的平滑同步。" },
    { title: "开源透明无广告", desc: "基于 Apache-2.0 协议完全开源，零广告、零第三方跟踪、零冗余权限申请。" }
  ]
};

// 暴露为全局只读对象，确保在浏览器全局作用域安全访问
if (typeof window !== "undefined") {
  window.APP_CONFIG = Object.freeze(APP_CONFIG);
}
