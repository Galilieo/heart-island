<script setup>
// 回顾回信区：统一承载 12 种页面状态、横线信纸、虚线日期引用浮层与危机纯文字支持卡。
import { computed, nextTick, ref, watch } from 'vue'
import Xiaoya from '../ui/Xiaoya.vue'
import BaseButton from '../ui/BaseButton.vue'
import ReviewCitationPopover from './ReviewCitationPopover.vue'
import { formatChineseDate, formatRangeShort, parseLetterSegments } from '../../utils/review'

const props = defineProps({
  viewState: {
    type: String,
    required: true
  },
  mode: {
    type: String,
    default: 'week'
  },
  pageData: {
    type: Object,
    default: null
  },
  submitting: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['generate', 'regenerate', 'retry', 'refresh', 'openRecord'])

const SHOWS_LETTER_STATES = new Set(['ready', 'stale_edited', 'failed_with_report'])

const paperRef = ref(null)
const activeCitation = ref(null)

const report = computed(() => props.pageData?.report || null)
const sources = computed(() => report.value?.sources || [])
const sourceByRef = computed(() => new Map(sources.value.map((item) => [item.ref, item])))
const showLetter = computed(() => Boolean(report.value?.body && SHOWS_LETTER_STATES.has(props.viewState)))

const bodyParagraphs = computed(() => {
  const raw = report.value?.body
  if (!raw) {
    return []
  }
  return raw
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => parseLetterSegments(line, sources.value))
})

const psSegments = computed(() => {
  const ps = report.value?.ps
  if (!ps) {
    return []
  }
  return parseLetterSegments(ps, sources.value)
})

const supportParagraphs = computed(() => {
  const raw = report.value?.body
  if (!raw) {
    return []
  }
  return raw
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
})

const showAiEnabledHint = computed(() => {
  if (!report.value || !props.pageData) {
    return false
  }
  const used = Number(report.value.aiEnabledCount || 0)
  const total = Number(props.pageData.total || 0)
  return used > 0 && total > used
})

const headerMeta = computed(() => {
  if (!report.value) {
    return ''
  }
  const parts = []
  if (props.mode === 'recent30' && report.value.rangeStart && report.value.rangeEnd) {
    parts.push(`统计 ${formatRangeShort(report.value.rangeStart, report.value.rangeEnd)}`)
  }
  if (report.value.generatedAt) {
    const datePart = String(report.value.generatedAt).slice(0, 10)
    if (/^\d{4}-\d{2}-\d{2}$/.test(datePart)) {
      const [, month, day] = datePart.split('-').map(Number)
      parts.push(`${month} 月 ${day} 日写`)
    }
  }
  return parts.join(' · ')
})

function citationAriaLabel(refItem) {
  const source = sourceByRef.value.get(refItem.ref)
  const dateText = formatChineseDate(source?.date) || refItem.label
  return `查看 ${dateText} 的记录`
}

function toggleCitation(event, citeKey, refCode) {
  if (activeCitation.value?.citeKey === citeKey) {
    closeCitation(true)
    return
  }
  const source = sourceByRef.value.get(refCode)
  if (!source) {
    return
  }
  activeCitation.value = {
    citeKey,
    source,
    anchorEl: event.currentTarget
  }
}

function closeCitation(returnFocus = false) {
  const previousAnchor = activeCitation.value?.anchorEl
  activeCitation.value = null
  if (returnFocus && previousAnchor) {
    nextTick(() => previousAnchor.focus({ preventScroll: true }))
  }
}

function handleOpenRecord(recordId) {
  activeCitation.value = null
  emit('openRecord', recordId)
}

watch(
  () => [props.viewState, report.value?.id, report.value?.revision],
  () => {
    activeCitation.value = null
  }
)
</script>

<template>
  <section class="review-letter" :aria-label="mode === 'week' ? '周报回信' : '近 30 天回信'">
    <!-- 危机分流：严禁渲染小芽形象或头像，只保留温和稳重的纯文字支持卡片 -->
    <div v-if="viewState === 'crisis'" class="review-letter__support" data-testid="review-crisis-card">
      <div class="review-letter__support-title">陪你待一会儿</div>
      <div class="review-letter__support-body">
        <p v-for="(paragraph, idx) in supportParagraphs" :key="idx">{{ paragraph }}</p>
      </div>
    </div>

    <!-- 尚未出现信纸前的空态 / 提示态 / 生成态 -->
    <div v-else-if="!showLetter" class="review-letter__state">
      <Xiaoya
        :expression="viewState === 'generating' || viewState === 'polling_timeout' ? 'thinking' : viewState === 'not_generated' ? 'reply' : 'calm'"
        :animated="viewState === 'generating'"
        :size="72"
      />

      <template v-if="viewState === 'week_not_finished'">
        <h3 class="review-letter__state-title">这周还没过完</h3>
        <p class="review-letter__state-desc">周日之后再来拆这一周的信。</p>
      </template>

      <template v-else-if="viewState === 'empty'">
        <h3 class="review-letter__state-title">{{ mode === 'week' ? '这一周还没有心情记录' : '最近 30 天还没有心情记录' }}</h3>
        <p class="review-letter__state-desc">先多写几条日记再来看，小芽会替你好好收着。</p>
      </template>

      <template v-else-if="viewState === 'no_ai_enabled'">
        <h3 class="review-letter__state-title">这一段的记录都只留给了自己</h3>
        <p class="review-letter__state-desc">这些记录没有开启小芽回应，小芽不会替你读它们。</p>
      </template>

      <template v-else-if="viewState === 'not_generated'">
        <h3 class="review-letter__state-title">
          {{ mode === 'week' ? '这一周的故事已经收好了' : '最近 30 天的脚印已经收好了' }}
        </h3>
        <p class="review-letter__state-desc">要不要让小芽把这些天的心情整理成一封回信？</p>
        <BaseButton variant="primary" :loading="submitting" @click="emit('generate')">
          {{ mode === 'week' ? '写一封本周回信' : '写一封这阵子的信' }}
        </BaseButton>
      </template>

      <template v-else-if="viewState === 'generating'">
        <h3 class="review-letter__state-title">小芽正在把这几天的心情整理成一封信</h3>
        <p class="review-letter__state-desc">正在慢慢读你写的字，通常很快就好。</p>
      </template>

      <template v-else-if="viewState === 'polling_timeout'">
        <h3 class="review-letter__state-title">还在写，过一会儿再来看看</h3>
        <p class="review-letter__state-desc">小芽还在认真落笔，你可以点刷新再看一眼。</p>
        <BaseButton variant="soft" @click="emit('refresh')">刷新</BaseButton>
      </template>

      <template v-else-if="viewState === 'stale_deleted'">
        <h3 class="review-letter__state-title">记录有变动或已删除</h3>
        <p class="review-letter__state-desc">原先那封信对应的记录已不在了，重新写一封会更贴近现在。</p>
        <BaseButton variant="primary" :loading="submitting" @click="emit('regenerate')">重新生成</BaseButton>
      </template>

      <template v-else-if="viewState === 'failed_empty'">
        <h3 class="review-letter__state-title">这次没有顺利写出来</h3>
        <p class="review-letter__state-desc">可能是刚刚连线走神了，过一会儿再试试吧。</p>
        <BaseButton variant="primary" :loading="submitting" @click="emit('retry')">再试一次</BaseButton>
      </template>
    </div>

    <!-- 已有信件正文（含周报过期与 failed_with_report 顶部温和提醒条） -->
    <div v-else class="review-letter__paper-wrap">
      <div v-if="viewState === 'stale_edited'" class="review-letter__banner review-letter__banner--warn">
        <span>记录有修改或新增，这封信写在那之前</span>
        <BaseButton variant="soft" size="sm" :loading="submitting" @click="emit('regenerate')">重新生成</BaseButton>
      </div>

      <div v-else-if="viewState === 'failed_with_report'" class="review-letter__banner review-letter__banner--danger">
        <span>刚才重新写没有成功，先把上一封留在这里</span>
        <BaseButton variant="soft" size="sm" :loading="submitting" @click="emit('retry')">再试一次</BaseButton>
      </div>

      <article ref="paperRef" class="review-letter__paper">
        <header class="review-letter__header">
          <div class="review-letter__sender">
            <Xiaoya expression="reply" :size="36" />
            <div>
              <h3 class="review-letter__title">{{ mode === 'week' ? '小芽的本周来信' : '小芽的近况来信' }}</h3>
              <p v-if="headerMeta" class="review-letter__meta">{{ headerMeta }}</p>
            </div>
          </div>

          <BaseButton
            v-if="mode === 'recent30'"
            variant="ghost"
            size="sm"
            :loading="submitting"
            @click="emit('generate')"
          >
            重新写一封
          </BaseButton>
        </header>

        <p v-if="showAiEnabledHint" class="review-letter__d1-note">
          这次回信读了你开启小芽回应的 {{ report.aiEnabledCount }} 条记录
        </p>

        <div class="review-letter__body">
          <p v-for="(segments, pIdx) in bodyParagraphs" :key="pIdx" class="review-letter__p">
            <template v-for="(seg, sIdx) in segments" :key="sIdx"><template v-if="seg.type === 'text'">{{ seg.text }}</template><span v-else class="nw">{{ seg.prefix }}<button
                v-for="refItem in seg.refs"
                :key="refItem.ref"
                type="button"
                class="review-letter__src"
                :aria-label="citationAriaLabel(refItem)"
                :aria-expanded="String(activeCitation?.citeKey === `body-${pIdx}-${sIdx}-${refItem.ref}`)"
                @click.stop="toggleCitation($event, `body-${pIdx}-${sIdx}-${refItem.ref}`, refItem.ref)"
              >{{ refItem.label }}</button>{{ seg.punctuation }}</span></template>
          </p>
        </div>

        <p v-if="psSegments.length" class="review-letter__ps">
          <span class="review-letter__ps-label">P.S. </span><template v-for="(seg, sIdx) in psSegments" :key="sIdx"><template v-if="seg.type === 'text'">{{ seg.text }}</template><span v-else class="nw">{{ seg.prefix }}<button
              v-for="refItem in seg.refs"
              :key="refItem.ref"
              type="button"
              class="review-letter__src"
              :aria-label="citationAriaLabel(refItem)"
              :aria-expanded="String(activeCitation?.citeKey === `ps-${sIdx}-${refItem.ref}`)"
              @click.stop="toggleCitation($event, `ps-${sIdx}-${refItem.ref}`, refItem.ref)"
            >{{ refItem.label }}</button>{{ seg.punctuation }}</span></template>
        </p>

        <footer class="review-letter__sig">—— 小芽</footer>

        <ReviewCitationPopover
          :source="activeCitation?.source || null"
          :anchor-el="activeCitation?.anchorEl || null"
          :paper-el="paperRef"
          @close="closeCitation"
          @open-record="handleOpenRecord"
        />
      </article>
    </div>
  </section>
</template>

<style scoped>
.review-letter__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 10px;
  padding: 28px 20px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  box-shadow: var(--shadow-sm);
}

.review-letter__state-title {
  margin: 4px 0 0;
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  font-weight: 400;
  color: var(--ink-1);
}

.review-letter__state-desc {
  margin: 0 0 6px;
  max-width: 36ch;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--ink-2);
}

.review-letter__support {
  padding: 24px 26px;
  background: var(--bg-soft);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-3);
}

.review-letter__support-title {
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  color: var(--ink-1);
  margin-bottom: 12px;
}

.review-letter__support-body p {
  margin: 0 0 12px;
  font-size: var(--fs-base);
  line-height: 1.8;
  color: var(--ink-1);
  overflow-wrap: anywhere;
}

.review-letter__support-body p:last-child {
  margin-bottom: 0;
}

.review-letter__paper-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-letter__banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-radius: var(--radius-2);
  font-size: var(--fs-sm);
}

.review-letter__banner--warn {
  background: var(--warn-soft);
  color: var(--warn-ink);
}

.review-letter__banner--danger {
  background: var(--danger-soft);
  color: var(--danger-ink);
}

.review-letter__paper {
  position: relative;
  padding: 24px 26px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  box-shadow: var(--shadow-sm);
}

.review-letter__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--line);
}

.review-letter__sender {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-letter__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  font-weight: 400;
  color: var(--ink-1);
}

.review-letter__meta {
  margin: 2px 0 0;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.review-letter__d1-note {
  margin: 0 0 12px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* 横线只画在正文段落 <p> 上，line-height 与 34px 线距严格一致 */
.review-letter__p {
  margin: 0 0 18px;
  font-family: var(--font-display);
  font-size: 16px;
  line-height: 34px;
  color: var(--ink-1);
  overflow-wrap: anywhere;
  background-image: repeating-linear-gradient(transparent 0 33px, var(--paper-line) 33px 34px);
  background-position: 0 2px;
}

.review-letter__p:last-child {
  margin-bottom: 0;
}

.nw {
  white-space: nowrap;
}

.review-letter__src {
  font: inherit;
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1;
  color: var(--brand-deep);
  background: none;
  border: 0;
  border-bottom: 1px dashed var(--brand);
  padding: 0 1px 1px;
  margin: 0 0 0 3px;
  border-radius: 3px 3px 0 0;
  vertical-align: baseline;
  cursor: pointer;
}

.review-letter__src:hover {
  border-bottom-style: solid;
}

.review-letter__src:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.review-letter__src[aria-expanded='true'] {
  border-bottom: 1px solid var(--brand-deep);
  background: var(--brand-soft);
}

.review-letter__ps {
  margin: 18px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  font-size: var(--fs-sm);
  line-height: 1.75;
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.review-letter__ps-label {
  font-weight: 600;
  color: var(--brand-deep);
}

.review-letter__sig {
  margin-top: 16px;
  text-align: right;
  font-family: var(--font-display);
  font-size: var(--fs-md);
  color: var(--ink-2);
}

@media (max-width: 720px) {
  .review-letter__paper {
    padding: 18px 16px;
  }
}
</style>
