/**
 * 映辞 · Elocue 详情页交互逻辑 (details.js)
 */

document.addEventListener("DOMContentLoaded", () => {
  initStickyNavSpy();
});

/**
 * 详情页粘性锚点导航根据滚动自动高亮
 */
function initStickyNavSpy() {
  const navLinks = document.querySelectorAll(".details-nav-link");
  const sections = [];

  navLinks.forEach((link) => {
    const targetId = link.getAttribute("href");
    if (targetId && targetId.startsWith("#")) {
      const sec = document.querySelector(targetId);
      if (sec) {
        sections.push({ el: sec, link: link });
      }
    }
  });

  if (!sections.length) return;

  const onScroll = () => {
    const scrollPos = window.scrollY + 140; // 偏移量

    for (let i = sections.length - 1; i >= 0; i--) {
      const item = sections[i];
      if (item.el.offsetTop <= scrollPos) {
        navLinks.forEach((l) => l.classList.remove("active"));
        item.link.classList.add("active");
        break;
      }
    }
  };

  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();
}
