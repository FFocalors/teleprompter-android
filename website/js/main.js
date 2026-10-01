/**
 * 映辞 · Elocue 官网主交互逻辑 (main.js)
 */

document.addEventListener("DOMContentLoaded", () => {
  bindConfigData();
  initMobileNav();
  initDownloadHandlers();
  initCopyActions();
  initPrompterDemo();
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

  const hrefBindings = {
    githubRepoUrl: "githubRepoUrl",
    githubReleasesUrl: "githubReleasesUrl",
    gitcodeReleaseUrl: "gitcodeReleaseUrl",
  };

  Object.keys(hrefBindings).forEach((bindKey) => {
    document.querySelectorAll(`a[data-bind-href='${bindKey}']`).forEach((el) => {
      el.href = config[hrefBindings[bindKey]];
    });
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
 * 代码复制与 Toast 提示
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

/* ==========================================================================
   提词器播放屏复刻
   时间与位移模型照着工程走：
     core/model/ChineseSpeechDurationEstimator.kt  255 字/分基准 + 标点停顿
     core/util/PlaybackTiming.kt                   duration = 常规时长 / 倍速
     core/util/PlaybackEngine.kt                   进度按时间线性推进，
                                                   offset = start + (end - start) × progress
   ========================================================================== */

/** 与 ChineseSpeechDurationEstimator.kt 一致：255 单元/分，逗号 180ms、句末 350ms、段落 500ms */
function estimateNormalSeconds(text) {
  const chars = Array.from(text);
  let units = 0;
  let pauses = 0;

  for (let i = 0; i < chars.length; i++) {
    const ch = chars[i];
    if (/\s/.test(ch)) continue;

    if (/[A-Za-z0-9]/.test(ch)) {
      // 连续英文字母/数字串整体记 1.5 个单元
      if (!/[A-Za-z0-9]/.test(chars[i - 1] || "")) units += 1.5;
      continue;
    }

    units += 1;
    if (/[，、；,;]/.test(ch)) pauses += 0.18;
    else if (/[。！？!?]/.test(ch)) pauses += 0.35;
  }

  pauses += Math.max(0, text.split(/\n\s*\n/).length - 1) * 0.5;
  return units / 255 * 60 + pauses;
}

function formatClock(seconds) {
  const total = Math.max(0, Math.round(seconds));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return m + ":" + String(s).padStart(2, "0");
}

function initPrompterDemo() {
  const screen = document.getElementById("tpScreen");
  if (!screen) return;

  const viewport = document.getElementById("tpViewport");
  const script = document.getElementById("tpScript");
  const guide = document.getElementById("tpGuide");
  const playBtn = document.getElementById("tpPlay");
  const slowerBtn = document.getElementById("tpSlower");
  const fasterBtn = document.getElementById("tpFaster");
  const speedOut = document.getElementById("tpSpeed");
  const seekSlider = document.getElementById("tpSeek");
  const seekInput = document.getElementById("tpSeekInput");
  const percentOut = document.getElementById("tpPercent");
  const elapsedOut = document.getElementById("tpElapsed");
  const remainOut = document.getElementById("tpRemain");
  const statusBar = document.getElementById("tpStatusBar");
  const modeButtons = Array.from(document.querySelectorAll(".tp-mode"));
  const positionRow = document.getElementById("tpPositionRow");
  const guideSlider = document.getElementById("tpGuideSlider");
  const guideInput = document.getElementById("tpGuideInput");
  const card = document.querySelector(".tp-card");
  const exitBtn = document.getElementById("tpExit");
  const countdownSwitch = document.getElementById("tpCountdown");
  const countdownOverlay = document.getElementById("tpCountdownOverlay");
  const countdownNumber = document.getElementById("tpCountdownNumber");
  if (!script || !playBtn) return;

  const SPEED_MIN = 0.5;
  const SPEED_MAX = 2;
  const SPEED_STEP = 0.1;
  const HOLD_TO_EXIT_MS = 900;
  const COUNTDOWN_SECONDS = 3;

  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  let speed = 1;
  let progress = 0;
  let playing = false;
  let guideMode = "bar";
  let guidePos = 0.44;
  // 会话锚点：工程在会话开始时定格（AppState.kt:196-203），之后挪引导线只动带子不动文字
  let anchorFraction = 0.44;
  let anchorLines = 1.5;
  let finished = false;
  let countdownEnabled = false;
  let normalSeconds = estimateNormalSeconds(script.textContent || "");
  let duration = Math.max(1, normalSeconds);
  let segmentStartProgress = 0;
  let segmentStartedAt = 0;
  let rafId = null;
  let countdownTimer = null;

  // 测量一次就缓存：render() 每帧都跑，不能每帧读布局
  let metrics = { vh: 0, textH: 0, lineHeight: 0 };

  const t = (key) => (window.ElocueI18n ? window.ElocueI18n.t(key) : key);

  function measure() {
    metrics.vh = viewport.clientHeight;
    metrics.textH = script.getBoundingClientRect().height;
    metrics.lineHeight = parseFloat(getComputedStyle(script).lineHeight) || 0;
    applyGuideFloor();
  }

  /** 1dp 的像素值，和 prompter.css 里的 --u 保持同一套算法 */
  function unitPx() {
    return Math.max(screen.getBoundingClientRect().width / 1143, 0.75);
  }

  /**
   * 控制条浮在文字上方，引导线位置太低就会被卡片盖住。
   * 真机屏幕够高所以不会撞上，这里的阅读区比真机矮，窄屏时卡片还会变成两倍高，
   * 必须抬一个下限：卡片底 + 带子自身的半高 + 8dp 余量。
   * 按「暂停时最高」的卡片高度算，避免播放/暂停切换时引导线跳动。
   */
  function applyGuideFloor() {
    if (!card || !metrics.vh) return;
    const u = unitPx();
    const cardHeightDp = card.offsetHeight / u + (positionRow.hidden ? 70 : 0);
    const minDp = 64 + cardHeightDp + 28 + 8;
    const minPos = Math.min(0.75, minDp / (metrics.vh / u));
    if (guidePos < minPos - 1e-3) {
      guidePos = minPos;
      guideInput.value = String(Math.round(guidePos * 100));
    }
  }

  /** 提词位移的端点，PlaybackEngine.kt 的 PlaybackLayoutCalculator */
  function layout() {
    const { vh, textH, lineHeight } = metrics;
    // 引导条开着时首行落在锚点下方 1.5 行高；关掉时首行在视口 25% 处
    // （PlaybackEngine.kt:44-50、AppState.kt:197-203）
    const start =
      guideMode === "off" ? vh * 0.25 : vh * anchorFraction + anchorLines * lineHeight;
    const end = vh * 0.67 - textH; // FinalTextBottomFraction = 0.67
    return { start, end };
  }

  function render() {
    const { vh } = metrics;
    const guideY = vh * guidePos; // 引导条跟的是实时设置，文字跟的是冻结锚点
    const { start, end } = layout();
    const offset = start + (end - start) * progress;
    const pct = (progress * 100).toFixed(2) + "%";

    script.style.transform = `translate3d(0, ${offset.toFixed(2)}px, 0)`;
    guide.style.setProperty("--gy", guideY.toFixed(2) + "px");
    seekSlider.style.setProperty("--p", pct);
    statusBar.style.setProperty("--p", pct);

    percentOut.textContent = Math.round(progress * 100) + "%";
    elapsedOut.textContent = formatClock(duration * progress);
    remainOut.textContent = formatClock(duration * (1 - progress));

    // 引导线滑块的值域是 0.15~0.75，要映射回 0~100% 才是拇指位置
    const gp = ((guidePos - 0.15) / 0.6) * 100;
    guideSlider.style.setProperty("--p", gp.toFixed(2) + "%");
    screen.classList.toggle("is-finished", finished);

    const value = Math.round(progress * 1000);
    if (Number(seekInput.value) !== value) seekInput.value = String(value);
  }

  function syncSpeedUI() {
    speedOut.textContent = speed.toFixed(1);
  }

  function syncPlayUI() {
    playBtn.classList.toggle("is-playing", playing);
    playBtn.setAttribute("aria-pressed", String(playing));
    // 工程里这一行只在暂停时出现（PlaybackControlBar.kt:168）
    positionRow.hidden = playing;
    const key = playing ? "tp.pause" : "tp.play";
    playBtn.setAttribute("data-i18n-attr", "aria-label:" + key);
    playBtn.setAttribute("aria-label", t(key));
  }

  function syncGuideUI() {
    guide.classList.toggle("tp-guide--bar", guideMode === "bar");
    guide.classList.toggle("tp-guide--line", guideMode === "line");
    guide.classList.toggle("tp-guide--off", guideMode === "off");
    modeButtons.forEach((btn) => {
      btn.setAttribute("aria-pressed", String(btn.dataset.mode === guideMode));
    });
  }

  function resetTimeline() {
    segmentStartProgress = progress;
    segmentStartedAt = performance.now();
  }

  function setSpeed(next) {
    // 换速先把当前进度定格，再按新倍速重算总时长（等价于工程里的 seek）
    progress = currentProgress();
    speed = Math.min(SPEED_MAX, Math.max(SPEED_MIN, Math.round(next * 10) / 10));
    duration = Math.max(1, normalSeconds / speed);
    resetTimeline();
    syncSpeedUI();
    render();
  }

  function currentProgress() {
    if (!playing) return progress;
    const elapsed = performance.now() - segmentStartedAt;
    return Math.min(1, segmentStartProgress + elapsed / (duration * 1000));
  }

  function seekTo(next) {
    finished = false;
    progress = Math.min(1, Math.max(0, next));
    resetTimeline();
    render();
  }

  function setPlaying(next) {
    if (next === playing) return;
    playing = next;
    resetTimeline();
    syncPlayUI();
    if (playing) start(); else stop();
  }

  function start() {
    if (rafId !== null) return;
    segmentStartedAt = performance.now();
    rafId = requestAnimationFrame(tick);
  }

  function stop() {
    if (rafId === null) return;
    cancelAnimationFrame(rafId);
    rafId = null;
  }

  function tick(now) {
    const elapsed = now - segmentStartedAt;
    progress = Math.min(1, segmentStartProgress + elapsed / (duration * 1000));
    render();
    if (progress >= 1) {
      finished = true;
      setPlaying(false);
      render();
      // App 播完就停在结尾；网页上停住等于演示变成死图，所以展示一会儿再从头来
      window.setTimeout(() => {
        finished = false;
        seekTo(0);
        setPlaying(true);
      }, 2600);
      return;
    }
    rafId = requestAnimationFrame(tick);
  }

  /* --- 倒计时：工程里只在「暂停后继续」时出现（PrompterScreen.kt:318）------- */
  function cancelCountdown() {
    if (countdownTimer) {
      clearInterval(countdownTimer);
      countdownTimer = null;
    }
    countdownOverlay.hidden = true;
  }

  function runCountdownThenPlay() {
    let remaining = COUNTDOWN_SECONDS;
    countdownNumber.textContent = String(remaining);
    countdownOverlay.hidden = false;
    countdownTimer = setInterval(() => {
      remaining -= 1;
      if (remaining <= 0) {
        cancelCountdown();
        setPlaying(true);
        return;
      }
      countdownNumber.textContent = String(remaining);
    }, 1000);
  }

  /* --- 事件 --------------------------------------------------------------- */
  playBtn.addEventListener("click", () => {
    if (countdownTimer) {
      cancelCountdown();
      return;
    }
    if (playing) {
      setPlaying(false);
      return;
    }
    const resuming = progress > 0.001;
    // 倒计时只是一次数字变化，没有位移动效，所以降低动效时也照常给
    if (countdownEnabled && resuming) {
      runCountdownThenPlay();
    } else {
      setPlaying(true);
    }
  });

  slowerBtn.addEventListener("click", () => setSpeed(speed - SPEED_STEP));
  fasterBtn.addEventListener("click", () => setSpeed(speed + SPEED_STEP));

  seekInput.addEventListener("input", () => {
    setPlaying(false);
    seekTo(Number(seekInput.value) / 1000);
  });

  modeButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      guideMode = btn.dataset.mode || "bar";
      syncGuideUI();
      render();
    });
  });

  guideInput.addEventListener("input", () => {
    guidePos = Number(guideInput.value) / 100;
    render();
  });

  countdownSwitch.addEventListener("click", () => {
    countdownEnabled = !countdownEnabled;
    countdownSwitch.setAttribute("aria-checked", String(countdownEnabled));
  });

  /* 长按退出：工程里按住 900ms 结束播放（PlaybackControlBar.kt:341）。
     网页上没有「上一页」可退，等价动作是回到开头并停下。 */
  let holdRaf = null;
  let holdStart = 0;
  let holdDone = false;

  function endHold() {
    if (holdRaf) cancelAnimationFrame(holdRaf);
    holdRaf = null;
    exitBtn.classList.remove("is-holding");
    exitBtn.style.removeProperty("--hold");
  }

  exitBtn.addEventListener("pointerdown", () => {
    if (holdRaf) return;
    holdDone = false;
    holdStart = performance.now();
    exitBtn.classList.add("is-holding");

    const step = (now) => {
      const ratio = Math.min(1, (now - holdStart) / HOLD_TO_EXIT_MS);
      exitBtn.style.setProperty("--hold", ratio.toFixed(3));
      if (ratio >= 1) {
        holdDone = true;
        endHold();
        setPlaying(false);
        seekTo(0);
        return;
      }
      holdRaf = requestAnimationFrame(step);
    };
    holdRaf = requestAnimationFrame(step);
  });

  ["pointerup", "pointercancel", "pointerleave"].forEach((evt) => {
    exitBtn.addEventListener(evt, () => {
      if (!holdDone) endHold();
    });
  });

  /* --- 环境变化 ----------------------------------------------------------- */
  const remeasure = () => {
    measure();
    render();
  };

  /**
   * 起始位置：源码公式下进度 0 是「空带子 + 文字在下面 1.5 行」，
   * 开场左边会空一大块。这里直接把起点定在「读到一半」——带子里有当前行，
   * 带子上方用已读行把卡片下沿到带子之间填满。
   * 必须在首帧里量：卡片高度会随播放态变（暂停时多一行引导线位置），
   * 所以先按播放态（隐藏那一行）量，量完再还原。
   */
  function placeInitialProgress() {
    const restoreHidden = positionRow.hidden;
    positionRow.hidden = true;
    measure();

    anchorFraction = guidePos; // 会话锚点在此定格
    const { vh, lineHeight } = metrics;
    const { start, end } = layout();
    const span = start - end;

    if (span > 1) {
      const screenTop = screen.getBoundingClientRect().top;
      const cardBottomInViewport =
        (card.getBoundingClientRect().bottom - screenTop) / unitPx() - 64;
      const bandTop = vh * anchorFraction - lineHeight / 2;
      const linesAbove = Math.max(1, Math.round((bandTop - cardBottomInViewport) / lineHeight));
      const target = bandTop - linesAbove * lineHeight;
      progress = Math.min(0.6, Math.max(0, (start - target) / span));
      resetTimeline();
    }

    positionRow.hidden = restoreHidden;
    render();
  }

  if (window.ResizeObserver) {
    new ResizeObserver(remeasure).observe(viewport);
  } else {
    window.addEventListener("resize", remeasure, { passive: true });
  }

  // 切换语言会换掉演示文稿：重新计时并回到开头
  new MutationObserver(() => {
    normalSeconds = estimateNormalSeconds(script.textContent || "");
    duration = Math.max(1, normalSeconds / speed);
    progress = 0;
    resetTimeline();
    remeasure();
  }).observe(script, { childList: true, characterData: true, subtree: true });

  // 滚出视口就停下，别在看不见的地方空转
  if (window.IntersectionObserver) {
    new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          if (playing) start();
        } else {
          stop();
        }
      },
      { threshold: 0.12 }
    ).observe(screen);
  }

  syncSpeedUI();
  syncGuideUI();
  syncPlayUI();
  guideInput.value = String(Math.round(guidePos * 100));
  remeasure();

  // 等首帧布局落定再定起始位置，否则量到的是没排完版的文稿高度
  requestAnimationFrame(() => {
    placeInitialProgress();
    syncPlayUI();
    if (!reduceMotion.matches) setPlaying(true);
  });
}
