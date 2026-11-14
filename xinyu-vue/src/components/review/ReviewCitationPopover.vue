<script setup>
// 引用原文浮层：桌面端在信纸内贴近日期浮动（自动上翻防溢出），移动端（<=768px）从底部弹出。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BaseTag from '../ui/BaseTag.vue'
import { computeCitationPopoverLayout, formatChineseDate } from '../../utils/review'
import { moodTone } from '../../utils/mood'

const props = defineProps({
  source: {
    type: Object,
    default: null
  },
  anchorEl: {
    type: Object,
    default: null
  },
  paperEl: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close', 'openRecord'])

const dialogRef = ref(null)
const textRef = ref(null)
const expanded = ref(false)
const isOverflowing = ref(false)
const useSheet = ref(false)
const layout = ref({ left: 0, top: 0, above: false, arrowX: 24 })

let intersectionObserver = null

const dateLabel = computed(() => formatChineseDate(props.source?.date) || String(props.source?.date || ''))

const popoverStyle = computed(() => {
  if (useSheet.value) {
    return undefined
  }
  return {
    left: `${layout.value.left}px`,
    top: `${layout.value.top}px`,
    '--arrow-x': `${layout.value.arrowX}px`
  }
})

function disconnectObserver() {
  if (intersectionObserver) {
    intersectionObserver.disconnect()
    intersectionObserver = null
  }
}

function updateViewportMode() {
  useSheet.value = typeof window !== 'undefined' && window.innerWidth <= 768
}

function placePopover() {
  if (useSheet.value || !dialogRef.value || !props.anchorEl || !props.paperEl) {
    return
  }
  layout.value = computeCitationPopoverLayout({
    letterRect: props.paperEl.getBoundingClientRect(),
    tagRect: props.anchorEl.getBoundingClientRect(),
    popWidth: dialogRef.value.offsetWidth,
    popHeight: dialogRef.value.offsetHeight,
    viewportHeight: window.innerHeight
  })
}

async function syncAfterSourceChange() {
  disconnectObserver()
  if (!props.source) {
    return
  }

  updateViewportMode()
  expanded.value = false
  isOverflowing.value = false
  await nextTick()

  if (textRef.value) {
    isOverflowing.value = textRef.value.scrollHeight > textRef.value.clientHeight + 2
  }
  await nextTick()
  placePopover()

  if (!useSheet.value && props.anchorEl && typeof IntersectionObserver !== 'undefined') {
    intersectionObserver = new IntersectionObserver((entries) => {
      if (entries[0] && !entries[0].isIntersecting) {
        emit('close', false)
      }
    })
    intersectionObserver.observe(props.anchorEl)
  }

  dialogRef.value?.focus({ preventScroll: true })
}

async function toggleExpand() {
  expanded.value = !expanded.value
  await nextTick()
  placePopover()
}

function handleKeydown(event) {
  if (event.key === 'Escape' && props.source) {
    emit('close', true)
  }
}

function handleDocumentClick() {
  if (props.source) {
    emit('close', false)
  }
}

function handleResize() {
  if (props.source) {
    emit('close', false)
  }
}

watch(
  () => [props.source?.recordId, props.source?.ref, props.anchorEl],
  () => {
    syncAfterSourceChange()
  },
  { immediate: true }
)

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('click', handleDocumentClick)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  disconnectObserver()
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', handleDocumentClick)
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <!-- 手机端底部抽屉传送到 body，避免被 AppShell 主栏堆叠上下文压在底部导航栏下方；桌面端留在信纸容器内相对定位。 -->
  <Teleport to="body" :disabled="!useSheet">
    <template v-if="source">
      <div v-if="useSheet" class="review-citation__scrim" @click="emit('close', true)" />

      <div
        ref="dialogRef"
        role="dialog"
        tabindex="-1"
        :aria-label="`${dateLabel}的记录`"
        class="review-citation__dialog"
        :class="useSheet ? 'review-citation__sheet' : ['review-citation__note', { 'is-above': layout.above }]"
        :style="popoverStyle"
        @click.stop
      >
        <div v-if="useSheet" class="review-citation__grab" aria-hidden="true" />

        <div class="review-citation__top">
          <span class="review-citation__date">{{ dateLabel }} {{ source.weekday }}</span>
          <BaseTag :tone="moodTone(source.moodType)">{{ source.moodType }}</BaseTag>
          <button
            type="button"
            class="review-citation__close"
            aria-label="关闭"
            @click="emit('close', true)"
          >
            ✕
          </button>
        </div>

        <p
          ref="textRef"
          class="review-citation__content"
          :class="{ 'is-clamped': !expanded, 'is-open': expanded }"
        >{{ source.content }}</p>

        <div class="review-citation__actions">
          <button
            v-if="isOverflowing"
            type="button"
            class="review-citation__more"
            @click="toggleExpand"
          >
            {{ expanded ? '收起' : '展开全文' }}
          </button>
          <button
            type="button"
            class="review-citation__go"
            @click="emit('openRecord', source.recordId)"
          >
            打开这条记录 →
          </button>
        </div>
      </div>
    </template>
  </Teleport>
</template>

<style scoped>
.review-citation__note {
  position: absolute;
  z-index: 20;
  width: min(320px, calc(100% - 32px));
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--line-strong);
  border-radius: 14px;
  box-shadow: var(--shadow-md);
  font-size: 15px;
  line-height: 1.75;
  color: var(--ink-1);
  outline: none;
}

.review-citation__note::before {
  content: "";
  position: absolute;
  left: var(--arrow-x, 24px);
  top: -7px;
  width: 12px;
  height: 12px;
  background: var(--bg-card);
  border-left: 1px solid var(--line-strong);
  border-top: 1px solid var(--line-strong);
  transform: rotate(45deg);
}

.review-citation__note.is-above::before {
  top: auto;
  bottom: -7px;
  border-left: 0;
  border-top: 0;
  border-right: 1px solid var(--line-strong);
  border-bottom: 1px solid var(--line-strong);
}

.review-citation__scrim {
  position: fixed;
  inset: 0;
  z-index: var(--z-dialog);
  background: var(--bg-overlay);
}

.review-citation__sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: calc(var(--z-dialog) + 1);
  max-height: 70vh;
  overflow: auto;
  padding: 8px 18px calc(22px + env(safe-area-inset-bottom, 0px));
  background: var(--bg-card);
  border-radius: 18px 18px 0 0;
  box-shadow: var(--shadow-lg);
  font-size: 14.5px;
  line-height: 1.75;
  color: var(--ink-1);
  outline: none;
}

.review-citation__grab {
  width: 36px;
  height: 4px;
  margin: 0 auto 10px;
  border-radius: 2px;
  background: var(--line-strong);
}

.review-citation__top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.review-citation__date {
  font-size: 14px;
  color: var(--ink-2);
}

.review-citation__close {
  margin-left: auto;
  padding: 2px 6px;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--ink-3);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.review-citation__close:focus-visible {
  outline: 2px solid var(--brand);
}

.review-citation__content {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.review-citation__content.is-clamped {
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.review-citation__content.is-open {
  max-height: 260px;
  overflow: auto;
}

.review-citation__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.review-citation__more,
.review-citation__go {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  color: var(--brand-deep);
  cursor: pointer;
}

.review-citation__more {
  font-size: 12px;
}

.review-citation__go {
  margin-left: auto;
  font-size: 13px;
}

.review-citation__more:hover,
.review-citation__go:hover {
  text-decoration: underline;
}
</style>
