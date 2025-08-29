(function() {
  // 已绑定的目标元素集合，避免重复绑定
  // 使用一个简单标记在元素上，避免重复绑定
  const boundMarker = '__clb_bound_clb';

  // 灯箱 DOM 的标识
  const LIGHTBOX_ID = 'clb-lightbox';
  const CONTENT_ID = 'clb-content';
  let lightboxEl = null;
  let contentEl = null;
  let styleEl = null;
  let keyListenerBound = false;
  let fullscreenListenerBound = false;

  // 获取当前可以覆盖可视区域的容器（全屏时为 fullscreenElement，其它为 document.body）
  function getLightboxContainer() {
    return document.fullscreenElement || document.webkitFullscreenElement || document.body;
  }

  // 将灯箱移动到当前可视区域容器中（在进入/退出全屏时调用）
  function moveLightboxIfNeeded() {
    if (!lightboxEl) return;
    const target = getLightboxContainer();
    if (lightboxEl.parentElement !== target) {
      target.appendChild(lightboxEl);
    }
  }

  // 确保灯箱结构已就绪
  function ensureLightbox() {
    if (lightboxEl) {
      moveLightboxIfNeeded();
      return;
    }

    // 灯箱容器
    lightboxEl = document.createElement('div');
    lightboxEl.id = LIGHTBOX_ID;
    lightboxEl.className = 'clb-lightbox';
    lightboxEl.style.display = 'none';
    lightboxEl.setAttribute('aria-hidden', 'true');

    // 内容区域
    contentEl = document.createElement('div');
    contentEl.id = CONTENT_ID;
    contentEl.className = 'clb-lightbox-content';
    lightboxEl.appendChild(contentEl);

    document.body.appendChild(lightboxEl);

    // 基础样式
    styleEl = document.createElement('style');
    styleEl.id = 'clb-styles';
    styleEl.textContent = `
      .clb-lightbox {
        position: fixed;
        top: 0; left: 0;
        width: 100%; height: 100%;
        background: rgba(0,0,0,.85);
        display: none;
        align-items: center;
        justify-content: center;
        z-index: 9999;
        overflow: auto;
      }
      .clb-lightbox-content {
		background: var(--clb-content-bg, #ffffff);  
        max-width: var(--clb-max-width, 90vw);
        max-height: var(--clb-max-height, 90vh);
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 8px;
        border-radius: 6px;
      }
    `;
    document.head.appendChild(styleEl);

    // 点击灯箱遮罩关闭
    lightboxEl.addEventListener('click', function(e) {
      if (e.target === lightboxEl) closeLightbox();
    });

    // Esc 关闭
    if (!keyListenerBound) {
      document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') closeLightbox();
      });
      keyListenerBound = true;
    }

    function closeLightbox() {
      if (!lightboxEl) return;
      lightboxEl.style.display = 'none';
      lightboxEl.setAttribute('aria-hidden', 'true');
      if (contentEl) contentEl.innerHTML = '';
      document.body.style.overflow = '';
      moveLightboxIfNeeded();
    }

    moveLightboxIfNeeded();

    // 监听全屏变化，动态调整灯箱挂载点
    if (!fullscreenListenerBound) {
      const onFsChange = () => moveLightboxIfNeeded();
      document.addEventListener('fullscreenchange', onFsChange);
      document.addEventListener('webkitfullscreenchange', onFsChange);
      fullscreenListenerBound = true;
    }
  }

  // 公共 API：为指定选择器绑定放大查看
  // selectors: 字符串（可逗号分隔）或字符串数组
  // options: { maxWidth, maxHeight }
  window.enableContentLightboxForSelector = function(selectors, options) {
    // 归一化选择器
    const selList = [];
    if (typeof selectors === 'string') {
      selectors.split(',').forEach(s => {
        const t = s.trim();
        if (t) selList.push(t);
      });
    } else if (Array.isArray(selectors)) {
      selectors.forEach(s => {
        if (typeof s === 'string') selList.push(s);
      });
    } else {
      console.warn('enableContentLightboxForSelector: 需要传入选择器字符串或字符串数组');
      return;
    }

    const opts = Object.assign({ maxWidth: '90vw', maxHeight: '90vh' }, options || {});

    ensureLightbox();

    // 设置全局 CSS 变量用于最大宽高
    const root = document.documentElement;
    if (opts.maxWidth) root.style.setProperty('--clb-max-width', opts.maxWidth);
    if (opts.maxHeight) root.style.setProperty('--clb-max-height', opts.maxHeight);

    const boundList = [];

    selList.forEach(sel => {
      try {
        document.querySelectorAll(sel).forEach(el => {
          // 只对 img 或 svg 生效
          const tag = (el.tagName || '').toLowerCase();
          if (!(tag === 'img' || tag === 'svg')) return;

          // 防重复绑定
          if (el[boundMarker]) return;
          el[boundMarker] = true;

          el.style.cursor = 'zoom-in';
          const handler = function(e) {
            e.preventDefault();
            openFromNode(el);
          };
          el.addEventListener('click', handler);
          boundList.push({ el, handler });
        });
      } catch (err) {
        console.warn('enableContentLightboxForSelector: 处理选择器时出错', sel, err);
      }
    });

    function openFromNode(node) {
      ensureLightbox();
      moveLightboxIfNeeded();
      contentEl.innerHTML = '';
      const clone = node.cloneNode(true);
      if (clone && clone.tagName && clone.tagName.toLowerCase() === 'img') {
        clone.style.maxWidth = '90vw';
        clone.style.maxHeight = '90vh';
        clone.style.width = 'auto';
        clone.style.height = 'auto';
        clone.style.display
	} else if (clone && clone.tagName && clone.tagName.toLowerCase() === 'svg') {
		// 针对 Mermaid 生成的 SVG，放大到容器宽度
 // 1) 尝试从 viewBox 解析 intrinsic 宽高
  let intrinsicW = null, intrinsicH = null;
  const vb = clone.getAttribute('viewBox');
  if (vb) {
    const parts = vb.trim().split(/\s+/);
    if (parts.length === 4) {
      intrinsicW = parseFloat(parts[2]);
      intrinsicH = parseFloat(parts[3]);
    }
  }
  // 2) 回退到 width/height 属性
  if (!intrinsicW || !intrinsicH || intrinsicW <= 0 || intrinsicH <= 0) {
    const wAttr = clone.getAttribute('width');
    const hAttr = clone.getAttribute('height');
    if (wAttr) intrinsicW = parseFloat(wAttr);
    if (hAttr) intrinsicH = parseFloat(hAttr);
  }
  // 3) 兜底值
  if (!intrinsicW || !intrinsicH || intrinsicW <= 0 || intrinsicH <= 0) {
    intrinsicW = 800;
    intrinsicH = 600;
  }

  // 4) 计算最大尺寸并按纵横比缩放到一个合适的具体像素尺寸
  const maxW = Math.floor(window.innerWidth * 0.9);
  const maxH = Math.floor(window.innerHeight * 0.9);
  const scale = Math.min(maxW / intrinsicW, maxH / intrinsicH);
  const targetW = Math.max(1, Math.round(intrinsicW * scale));
  const targetH = Math.max(1, Math.round(intrinsicH * scale));

  clone.setAttribute('width', targetW);
  clone.setAttribute('height', targetH);
  clone.style.width = targetW + 'px';
  clone.style.height = targetH + 'px';
  clone.style.display = 'block';	
      } else {
        clone.style.display = 'block';
      }
      contentEl.appendChild(clone);
      lightboxEl.style.display = 'flex';
      lightboxEl.setAttribute('aria-hidden', 'false');
      document.body.style.overflow = 'hidden';
    }

    const api = {
      destroy: function() {
        boundList.forEach(({ el, handler }) => {
          el.removeEventListener('click', handler);
          if (el[boundMarker]) delete el[boundMarker];
        });
        boundList.length = 0;
      }
    };

    return api;
  };
})();