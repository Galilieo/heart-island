<script setup>
// 后台页签。窄屏横滑时通过边缘渐隐 + 方向按钮提示「还有更多」，
// canScroll / atStart / atEnd 只是本地展示状态，不影响路由与权限。
// 滚动容器可聚焦（tabindex=0），支持 ←/→/Home/End 键盘滚动；
// 方向按钮点击按 70% 视宽步进，prefers-reduced-motion 下即时滚动无平滑动画。
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { RouterLink } from 'vue-router'

const navRef = ref(null)
const canScroll = ref(false)
const atStart = ref(true)
const atEnd = ref(false)

function updateScrollState() {
  const el = navRef.value
  if (!el) return
  canScroll.value = el.scrollWidth > el.clientWidth + 1
  atStart.value = el.scrollLeft <= 4
  atEnd.value = el.scrollLeft + el.clientWidth >= el.scrollWidth - 4
}

function motionSafe() {
  return !window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function scrollByStep(dir) {
  const el = navRef.value
  if (!el) return
  el.scrollBy({
    left: dir * Math.round(el.clientWidth * 0.7),
    behavior: motionSafe() ? 'smooth' : 'auto'
  })
}

function onNavKeydown(event) {
  const el = navRef.value
  if (!el) return
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    scrollByStep(1)
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    scrollByStep(-1)
  } else if (event.key === 'Home') {
    event.preventDefault()
    el.scrollTo({ left: 0, behavior: motionSafe() ? 'smooth' : 'auto' })
  } else if (event.key === 'End') {
    event.preventDefault()
    el.scrollTo({ left: el.scrollWidth, behavior: motionSafe() ? 'smooth' : 'auto' })
  }
}

onMounted(() => {
  updateScrollState()
  window.addEventListener('resize', updateScrollState)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateScrollState)
})
</script>

<template>
  <div
    class="admin-tabs-wrap"
    :class="{ 'is-scrollable': canScroll, 'is-at-start': atStart, 'is-at-end': atEnd }"
  >
    <!-- 方向按钮在 DOM 中先于 nav：键盘用户到达 tabs 区域时先遇到它，
         此时它必然可见且可操作；视觉上仍 absolute 贴在右缘。
         若放在 nav 之后，Tab 经过末位链接会触发自动滚动到底，
         按钮按「滚到底隐藏」语义先行隐藏，反而永远 Tab 不到。 -->
    <button
      type="button"
      class="admin-tabs__more"
      aria-label="向右滑动查看更多页签"
      @click="scrollByStep(1)"
    >
      <svg
        class="admin-tabs__more-icon"
        viewBox="0 0 16 16"
        width="16"
        height="16"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M6 3.5 10.5 8 6 12.5"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <nav
      ref="navRef"
      class="admin-tabs"
      aria-label="后台功能导航"
      tabindex="0"
      @scroll.passive="updateScrollState"
      @keydown="onNavKeydown"
    >
      <RouterLink to="/admin" class="admin-tabs__item">
        概览
      </RouterLink>
      <RouterLink to="/admin/users" class="admin-tabs__item">
        用户管理
      </RouterLink>
      <RouterLink to="/admin/posts" class="admin-tabs__item">
        帖子管理
      </RouterLink>
      <RouterLink to="/admin/replies" class="admin-tabs__item">
        回复管理
      </RouterLink>
      <RouterLink to="/admin/topics" class="admin-tabs__item">
        话题管理
      </RouterLink>
      <RouterLink to="/admin/ai-replies" class="admin-tabs__item">
        AI记录
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.admin-tabs-wrap {
  position: relative;
  width: fit-content;
  max-width: 100%;
}

.admin-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 100%;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-card);
}

.admin-tabs:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.admin-tabs__item {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 8px 14px;
  border-radius: var(--radius-2);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.admin-tabs__item:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.admin-tabs__item.router-link-active {
  background: var(--brand);
  color: #fff;
}

/* 方向提示按钮：桌面永远不出现 */
.admin-tabs__more {
  display: none;
}

/* 窄屏：tab 条横向滑动，不换行挤压；右缘渐隐 + 方向按钮提示可滑 */
@media (max-width: 768px) {
  .admin-tabs-wrap {
    width: 100%;
  }

  /* 右缘渐隐：滚到底消失 */
  .admin-tabs-wrap::after {
    content: '';
    position: absolute;
    top: 1px;
    right: 0;
    bottom: 1px;
    width: 56px;
    border-radius: 0 var(--radius-3) var(--radius-3) 0;
    background: linear-gradient(90deg, transparent, var(--bg-base) 78%);
    pointer-events: none;
    opacity: 0;
    transition: opacity var(--t-base) var(--ease-soft);
  }

  /* 左缘渐隐：离开起点后出现，提示可以滑回去 */
  .admin-tabs-wrap::before {
    content: '';
    position: absolute;
    top: 1px;
    left: 0;
    bottom: 1px;
    width: 28px;
    border-radius: var(--radius-3) 0 0 var(--radius-3);
    background: linear-gradient(270deg, transparent, var(--bg-base) 78%);
    pointer-events: none;
    opacity: 0;
    transition: opacity var(--t-base) var(--ease-soft);
    z-index: 1;
  }

  .admin-tabs-wrap.is-scrollable:not(.is-at-end)::after {
    opacity: 1;
  }

  .admin-tabs-wrap.is-scrollable:not(.is-at-start)::before {
    opacity: 1;
  }

  .admin-tabs {
    width: 100%;
    flex-wrap: nowrap;
    gap: 6px;
    padding: 6px;
    overflow-x: auto;
    scrollbar-width: none;
    scroll-snap-type: x proximity;
    -webkit-overflow-scrolling: touch;
  }

  /* 可滑时在滚动区内右侧预留 56px：静止态下方向按钮不会压住任何文字 */
  .admin-tabs-wrap.is-scrollable .admin-tabs {
    padding-right: 56px;
  }

  .admin-tabs::-webkit-scrollbar {
    display: none;
  }

  .admin-tabs__item {
    min-height: 44px;
    padding: 8px 16px;
    font-size: var(--fs-sm);
    scroll-snap-align: start;
  }

  /* 方向按钮：命中区真实 44×44（触摸目标），视觉圆形克制为内部 34px；
     贴在右缘渐隐区内，滚到底隐藏 */
  .admin-tabs__more {
    position: absolute;
    right: 2px;
    top: 50%;
    z-index: 2;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    margin-top: -22px;
    padding: 0;
    border: none;
    border-radius: 50%;
    background: transparent;
    color: var(--brand-deep);
    cursor: pointer;
    opacity: 0;
    visibility: hidden;
    transform: translateX(4px);
    transition: opacity var(--t-base) var(--ease-soft),
      transform var(--t-base) var(--ease-soft),
      visibility var(--t-base);
  }

  /* 视觉圆形：34px，命中区之外的透明部分仍可点击 */
  .admin-tabs__more::before {
    content: '';
    position: absolute;
    inset: 5px;
    border: 1px solid var(--line);
    border-radius: 50%;
    background: var(--bg-card);
    box-shadow: var(--shadow-sm);
    transition: background var(--t-fast) var(--ease-soft);
  }

  .admin-tabs__more-icon {
    position: relative;
    z-index: 1;
  }

  .admin-tabs-wrap.is-scrollable:not(.is-at-end) .admin-tabs__more {
    opacity: 1;
    visibility: visible;
    transform: translateX(0);
  }

  .admin-tabs__more:hover::before {
    background: var(--brand-soft);
  }

  .admin-tabs__more:focus-visible {
    outline: 2px solid var(--brand);
    outline-offset: 1px;
  }
}
</style>
