/**
 * 映辞 · Elocue 官网全局主交互逻辑 (main.js)
 * 现代极简设计 · 严禁 Emoji
 */

document.addEventListener("DOMContentLoaded", () => {
  bindConfigData();
  initMobileNav();
  initDownloadHandlers();
  initStudioTabs();
  initHeroStageInteractive();
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
 * 首屏真机硬件展示交互：
 * 点击哪个设备就给哪个特写，点击空白处恢复默认双机协同展示，无额外文字提示
 */
function initHeroStageInteractive() {
  const container = document.getElementById("heroStageContainer");
  if (!container) return;

  const prompterMonitor = document.getElementById("stagePrompterMonitor");
  const controllerPhone = document.getElementById("stageControllerPhone");

  const resetDefault = () => {
    container.classList.remove("mode-prompter", "mode-controller");
  };

  const showPrompterFocus = () => {
    container.classList.remove("mode-controller");
    container.classList.add("mode-prompter");
  };

  const showControllerFocus = () => {
    container.classList.remove("mode-prompter");
    container.classList.add("mode-controller");
  };

  // 1. 点击提词端大平板：切换全景特写，再次点击恢复默认
  if (prompterMonitor) {
    prompterMonitor.addEventListener("click", (e) => {
      e.stopPropagation();
      if (container.classList.contains("mode-prompter")) {
        resetDefault();
      } else {
        showPrompterFocus();
      }
    });
  }

  // 2. 点击手持控制端手机：切换手持特写，再次点击恢复默认
  if (controllerPhone) {
    controllerPhone.addEventListener("click", (e) => {
      e.stopPropagation();
      if (container.classList.contains("mode-controller")) {
        resetDefault();
      } else {
        showControllerFocus();
      }
    });
  }

  // 3. 点击舞台空白处恢复默认展示
  container.addEventListener("click", (e) => {
    if (e.target === container || e.target.classList.contains("hero-stage-glow")) {
      resetDefault();
    }
  });

  // 4. 点击页面其他任意空白处恢复默认展示
  document.addEventListener("click", (e) => {
    if (!container.contains(e.target)) {
      resetDefault();
    }
  });
}

