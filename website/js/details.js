/**
 * 映辞 · Elocue 详情页交互逻辑 (details.js)
 */

document.addEventListener("DOMContentLoaded", () => {
  initSubnavSpy();
});

/**
 * 目录条随滚动高亮当前小节。
 * 用 IntersectionObserver 而不是 offsetTop 比较——粘性头部会让 offsetTop 失真。
 */
function initSubnavSpy() {
  const links = Array.from(document.querySelectorAll(".subnav-item"));
  if (!links.length || !window.IntersectionObserver) return;

  const pairs = [];
  links.forEach((link) => {
    const href = link.getAttribute("href");
    if (!href || !href.startsWith("#")) return;
    const section = document.querySelector(href);
    if (section) pairs.push({ section, link });
  });

  if (!pairs.length) return;

  const visible = new Set();

  const setActive = (link) => {
    links.forEach((l) => l.classList.toggle("active", l === link));
  };

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          visible.add(entry.target);
        } else {
          visible.delete(entry.target);
        }
      });

      const current = pairs.find((p) => visible.has(p.section));
      if (current) setActive(current.link);
    },
    { rootMargin: "-20% 0px -70% 0px" }
  );

  pairs.forEach((p) => observer.observe(p.section));
}
