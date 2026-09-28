/**
 * 映辞 · Elocue 官网全局主交互逻辑 (main.js)
 * 现代极简设计 · 严禁 Emoji
 */

document.addEventListener("DOMContentLoaded", () => {
  bindConfigData();
  initMobileNav();
  initDownloadHandlers();
  initStudioTabs();
  initHeroStageScrollMotion();
  initCopyActions();
});

/**
 * 将 APP_CONFIG 中的集中配置注入到 HTML 对应节点
 */
function bindConfigData() {
  if (!window.APP_CONFIG) return;
  const config = window.APP_CONFIG;

  document.querySelectorAll("[data-bind]").forEach((el) => {
    const key = el.getAttribute("data-bind");
    if (config[key] !== undefined) {
      el.textContent = config[key];
    }
  });

  // 绑定仓库链接
  document.querySelectorAll("a[data-bind-href='githubRepoUrl']").forEach((el) => {
    el.href = config.githubRepoUrl;
  });

  // 绑定 Release 链接
  document.querySelectorAll("a[data-bind-href='githubReleasesUrl']").forEach((el) => {
    el.href = config.githubReleasesUrl;
  });

  // 绑定 GitCode 国内下载链接
  document.querySelectorAll("a[data-bind-href='gitcodeReleaseUrl']").forEach((el) => {
    el.href = config.gitcodeReleaseUrl;
  });
}

/**
 * 移动端导航抽屉
 */
function initMobileNav() {
  const toggleBtn = document.getElementById("menuToggle");
  const drawer = document.getElementById("mobileDrawer");

  if (!toggleBtn || !drawer) return;

  toggleBtn.addEventListener("click", () => {
    const isOpen = drawer.classList.toggle("open");
    toggleBtn.setAttribute("aria-expanded", isOpen);
    document.body.style.overflow = isOpen ? "hidden" : "";
  });

  drawer.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => {
      drawer.classList.remove("open");
      toggleBtn.setAttribute("aria-expanded", "false");
      document.body.style.overflow = "";
    });
  });
}

/**
 * 下载行为与模态框
 */
function initDownloadHandlers() {
  const gitcodeBtn = document.getElementById("btnDownloadGitcode");
  const modal = document.getElementById("downloadModal");
  const modalClose = document.getElementById("modalCloseBtn");
  const modalOk = document.getElementById("modalOkBtn");

  if (gitcodeBtn) {
    gitcodeBtn.href = window.APP_CONFIG?.gitcodeReleaseUrl || "javascript:void(0);";
    gitcodeBtn.addEventListener("click", (e) => {
      const config = window.APP_CONFIG;
      if (!config.gitcodeReleaseUrl || config.gitcodeReleaseUrl.trim() === "") {
        e.preventDefault();
        openModal(modal);
      }
    });
  }

  if (modalClose) modalClose.addEventListener("click", () => closeModal(modal));
  if (modalOk) modalOk.addEventListener("click", () => closeModal(modal));

  if (modal) {
    modal.addEventListener("click", (e) => {
      if (e.target === modal) closeModal(modal);
    });
  }
}

function openModal(modal) {
  if (!modal) return;
  modal.classList.add("open");
  document.body.style.overflow = "hidden";
}

function closeModal(modal) {
  if (!modal) return;
  modal.classList.remove("open");
  document.body.style.overflow = "";
}

/**
 * 产品工作台视口舞台 Tab 切换
 */
function initStudioTabs() {
  const tabs = document.querySelectorAll(".stage-tab-item");
  const panels = document.querySelectorAll(".stage-view-panel");

  if (!tabs.length || !panels.length) return;

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      const targetId = tab.getAttribute("data-target");

      tabs.forEach((t) => t.classList.remove("active"));
      panels.forEach((p) => p.classList.remove("active"));

      tab.classList.add("active");
      const targetPanel = document.getElementById(targetId);
      if (targetPanel) {
        targetPanel.classList.add("active");
      }
    });
  });
}

/**
 * 代码复制与极简 Toast 提示
 */
function initCopyActions() {
  document.querySelectorAll("[data-copy]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const text = btn.getAttribute("data-copy");
      if (!text) return;

      try {
        await navigator.clipboard.writeText(text);
        showToast("已复制到剪贴板");
      } catch (err) {
        const textarea = document.createElement("textarea");
        textarea.value = text;
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand("copy");
        document.body.removeChild(textarea);
        showToast("已复制到剪贴板");
      }
    });
  });
}

let toastTimeout = null;
function showToast(msg) {
  let toast = document.getElementById("siteToast");
  if (!toast) {
    toast = document.createElement("div");
    toast.id = "siteToast";
    toast.className = "toast-msg";
    document.body.appendChild(toast);
  }

  toast.textContent = msg;
  toast.classList.add("visible");

  if (toastTimeout) clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => {
    toast.classList.remove("visible");
  }, 2000);
}

/**
 * 移动端：随页面上下滑动自动顺滑切换小幅度特写
 * 桌面端则纯粹通过 CSS :hover 实现，无需任何点击或 JS 干扰
 */
function initHeroStageScrollMotion() {
  const container = document.getElementById("heroStageContainer");
  if (!container) return;

  const isMobileOrTablet = () => window.innerWidth <= 900;
  let ticking = false;

  const onScroll = () => {
    if (!isMobileOrTablet()) {
      container.classList.remove("scroll-focus-prompter", "scroll-focus-controller");
      return;
    }

    const rect = container.getBoundingClientRect();
    const winHeight = window.innerHeight;

    // 舞台中心相对于视口的垂直位置比率 (0 为视口顶部，1 为视口底部)
    const stageCenterRatio = (rect.top + rect.height * 0.5) / winHeight;

    // 当舞台处于视口中下部 (刚滑入核心视觉区 0.50 ~ 0.80): 平板小幅度微特写
    if (stageCenterRatio > 0.50 && stageCenterRatio <= 0.80) {
      container.classList.remove("scroll-focus-controller");
      container.classList.add("scroll-focus-prompter");
    }
    // 当舞台继续上移至视口中偏上位置 (0.20 ~ 0.50): 控制端手机小幅度微特写
    else if (stageCenterRatio >= 0.20 && stageCenterRatio <= 0.50) {
      container.classList.remove("scroll-focus-prompter");
      container.classList.add("scroll-focus-controller");
    }
    // 离开中段视觉核心区时，顺滑恢复默认静态错落
    else {
      container.classList.remove("scroll-focus-prompter", "scroll-focus-controller");
    }
  };

  window.addEventListener("scroll", () => {
    if (!ticking) {
      window.requestAnimationFrame(() => {
        onScroll();
        ticking = false;
      });
      ticking = true;
    }
  }, { passive: true });

  window.addEventListener("resize", onScroll, { passive: true });
  // 首次初始化
  onScroll();
}
