<script setup>
// 三段式日期输入：用户只填数字，分隔符由组件固定显示；也可点击日历选择日期。
// 对外始终输出 YYYY-MM-DD，方便接口筛选和表单提交。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: String,
  hint: String,
  error: String,
  disabled: Boolean,
  placeholder: String,
  size: { type: String, default: 'md' },
  clearable: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'enter'])

const rootRef = ref(null)
const yearInputRef = ref(null)
const monthInputRef = ref(null)
const dayInputRef = ref(null)
const yearPart = ref('')
const monthPart = ref('')
const dayPart = ref('')
const pickerOpen = ref(false)
const viewDate = ref(getInitialViewDate(props.modelValue))

watch(
  () => props.modelValue,
  (value) => {
    setPartsFromValue(value)
    const parsed = parseDate(value)
    if (parsed) viewDate.value = new Date(parsed.year, parsed.month - 1, 1)
  },
  { immediate: true }
)

const hasValue = computed(() => yearPart.value || monthPart.value || dayPart.value)

const monthTitle = computed(() => {
  const year = viewDate.value.getFullYear()
  const month = viewDate.value.getMonth() + 1
  return `${year}年${pad(month)}月`
})

const calendarDays = computed(() => {
  const year = viewDate.value.getFullYear()
  const month = viewDate.value.getMonth()
  const first = new Date(year, month, 1)
  const startOffset = (first.getDay() + 6) % 7
  const start = new Date(year, month, 1 - startOffset)

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const value = formatDateObject(date)
    return {
      value,
      day: date.getDate(),
      inMonth: date.getMonth() === month,
      isToday: value === formatDateObject(new Date()),
      isSelected: value === props.modelValue
    }
  })
})

onMounted(() => {
  document.addEventListener('pointerdown', handleOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutside)
})

function pad(value) {
  return String(value).padStart(2, '0')
}

function digitsOnly(value) {
  return String(value || '').replace(/\D/g, '')
}

function formatDateObject(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function getInitialViewDate(value) {
  const parsed = parseDate(value)
  if (parsed) return new Date(parsed.year, parsed.month - 1, 1)
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), 1)
}

function parseDate(value) {
  const raw = String(value || '').trim()
  if (!raw) return null

  const normalized = raw
    .replace(/[年月.\\/]/g, '-')
    .replace(/日/g, '')
    .replace(/\s+/g, '')

  if (/^\d{8}$/.test(normalized)) {
    return toValidDate(
      Number(normalized.slice(0, 4)),
      Number(normalized.slice(4, 6)),
      Number(normalized.slice(6, 8))
    )
  }

  if (/^\d{4}$/.test(normalized)) {
    const now = new Date()
    return toValidDate(
      now.getFullYear(),
      Number(normalized.slice(0, 2)),
      Number(normalized.slice(2, 4))
    )
  }

  const parts = normalized.split('-').filter(Boolean)
  if (parts.length === 3) {
    return toValidDate(Number(parts[0]), Number(parts[1]), Number(parts[2]))
  }
  if (parts.length === 2) {
    const now = new Date()
    return toValidDate(now.getFullYear(), Number(parts[0]), Number(parts[1]))
  }

  return null
}

function toValidDate(year, month, day) {
  if (!year || !month || !day) return null
  if (year < 1900 || year > 2100) return null

  const date = new Date(year, month - 1, day)
  if (
    date.getFullYear() !== year ||
    date.getMonth() !== month - 1 ||
    date.getDate() !== day
  ) {
    return null
  }

  return { year, month, day }
}

function setPartsFromValue(value) {
  const parsed = parseDate(value)
  if (!parsed) {
    if (!value) clearParts()
    return
  }

  yearPart.value = String(parsed.year)
  monthPart.value = pad(parsed.month)
  dayPart.value = pad(parsed.day)
}

function clearParts() {
  yearPart.value = ''
  monthPart.value = ''
  dayPart.value = ''
}

function emitIfValid() {
  if (!hasValue.value) {
    emit('update:modelValue', '')
    return
  }
  if (yearPart.value.length !== 4 || !monthPart.value || !dayPart.value) return

  const parsed = toValidDate(
    Number(yearPart.value),
    Number(monthPart.value),
    Number(dayPart.value)
  )
  if (parsed) {
    emit('update:modelValue', `${parsed.year}-${pad(parsed.month)}-${pad(parsed.day)}`)
  }
}

function applyRawDate(value) {
  const parsed = parseDate(value)
  if (!parsed) return false
  const next = `${parsed.year}-${pad(parsed.month)}-${pad(parsed.day)}`
  setPartsFromValue(next)
  emit('update:modelValue', next)
  return true
}

function handlePartInput(part, event) {
  const raw = event.target.value
  const digits = digitsOnly(raw)

  if (digits.length >= 8 && applyRawDate(digits)) return

  if (part === 'year') {
    yearPart.value = digits.slice(0, 4)
    if (yearPart.value.length === 4) monthInputRef.value?.focus()
  }
  if (part === 'month') {
    monthPart.value = digits.slice(0, 2)
    if (monthPart.value.length === 2) dayInputRef.value?.focus()
  }
  if (part === 'day') {
    dayPart.value = digits.slice(0, 2)
  }

  emitIfValid()
}

function handlePartBlur(part) {
  if (part === 'month' && monthPart.value.length === 1) monthPart.value = pad(monthPart.value)
  if (part === 'day' && dayPart.value.length === 1) dayPart.value = pad(dayPart.value)
  emitIfValid()
}

function handlePaste(event) {
  const text = event.clipboardData?.getData('text')
  if (text && applyRawDate(text)) event.preventDefault()
}

function handleSegmentKeydown(part, event) {
  if (event.key === 'Enter') {
    handlePartBlur(part)
    emit('enter')
    return
  }

  if (event.key !== 'Backspace') return
  if (part === 'month' && !monthPart.value) yearInputRef.value?.focus()
  if (part === 'day' && !dayPart.value) monthInputRef.value?.focus()
}

function clear() {
  clearParts()
  emit('update:modelValue', '')
}

function togglePicker() {
  if (props.disabled) return
  const parsed = parseDate(props.modelValue)
  if (parsed) viewDate.value = new Date(parsed.year, parsed.month - 1, 1)
  pickerOpen.value = !pickerOpen.value
}

function shiftMonth(offset) {
  const next = new Date(viewDate.value)
  next.setMonth(next.getMonth() + offset)
  viewDate.value = next
}

function pickDate(value) {
  setPartsFromValue(value)
  emit('update:modelValue', value)
  pickerOpen.value = false
}

function pickToday() {
  pickDate(formatDateObject(new Date()))
}

function handleOutside(event) {
  if (!rootRef.value?.contains(event.target)) pickerOpen.value = false
}
</script>

<template>
  <label ref="rootRef" class="date-field" :class="{ 'is-error': !!error, 'is-disabled': disabled }">
    <span v-if="label" class="date-field__label">{{ label }}</span>
    <span class="date-field__control" :class="`date-field__control--${size}`">
      <span class="date-field__segments" @paste="handlePaste">
        <input
          ref="yearInputRef"
          :value="yearPart"
          class="date-field__segment date-field__segment--year"
          type="text"
          inputmode="numeric"
          placeholder="YYYY"
          maxlength="4"
          :disabled="disabled"
          aria-label="年"
          @input="handlePartInput('year', $event)"
          @blur="handlePartBlur('year')"
          @keydown="handleSegmentKeydown('year', $event)"
        />
        <span class="date-field__dash" aria-hidden="true">-</span>
        <input
          ref="monthInputRef"
          :value="monthPart"
          class="date-field__segment"
          type="text"
          inputmode="numeric"
          placeholder="MM"
          maxlength="2"
          :disabled="disabled"
          aria-label="月"
          @input="handlePartInput('month', $event)"
          @blur="handlePartBlur('month')"
          @keydown="handleSegmentKeydown('month', $event)"
        />
        <span class="date-field__dash" aria-hidden="true">-</span>
        <input
          ref="dayInputRef"
          :value="dayPart"
          class="date-field__segment"
          type="text"
          inputmode="numeric"
          placeholder="DD"
          maxlength="2"
          :disabled="disabled"
          aria-label="日"
          @input="handlePartInput('day', $event)"
          @blur="handlePartBlur('day')"
          @keydown="handleSegmentKeydown('day', $event)"
        />
      </span>
      <button
        v-if="clearable && hasValue && !disabled"
        type="button"
        class="date-field__clear"
        aria-label="清空日期"
        @click="clear"
      >
        ×
      </button>
      <button
        type="button"
        class="date-field__calendar"
        aria-label="打开日期选择"
        :aria-expanded="pickerOpen"
        :disabled="disabled"
        @click="togglePicker"
      >
        <span class="date-field__calendar-icon" aria-hidden="true"></span>
      </button>
    </span>
    <div v-if="pickerOpen" class="date-field__picker">
      <div class="date-picker__head">
        <button type="button" aria-label="上个月" @click="shiftMonth(-1)">‹</button>
        <strong>{{ monthTitle }}</strong>
        <button type="button" aria-label="下个月" @click="shiftMonth(1)">›</button>
      </div>
      <div class="date-picker__week" aria-hidden="true">
        <span>一</span>
        <span>二</span>
        <span>三</span>
        <span>四</span>
        <span>五</span>
        <span>六</span>
        <span>日</span>
      </div>
      <div class="date-picker__days">
        <button
          v-for="day in calendarDays"
          :key="day.value"
          type="button"
          :class="{
            'is-muted': !day.inMonth,
            'is-today': day.isToday,
            'is-selected': day.isSelected
          }"
          @click="pickDate(day.value)"
        >
          {{ day.day }}
        </button>
      </div>
      <div class="date-picker__foot">
        <button type="button" @click="clear(); pickerOpen = false">清空</button>
        <button type="button" @click="pickToday">今天</button>
      </div>
    </div>
    <span v-if="error" class="date-field__msg date-field__msg--error">{{ error }}</span>
    <span v-else-if="hint" class="date-field__msg">{{ hint }}</span>
  </label>
</template>

<style scoped>
.date-field {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.date-field__label {
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  letter-spacing: 0.5px;
}

.date-field__control {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px 0 12px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  transition: border-color var(--t-fast) var(--ease-soft),
    box-shadow var(--t-fast) var(--ease-soft);
}

.date-field__control--sm { height: 34px; font-size: var(--fs-sm); }
.date-field__control--md { height: 42px; }
.date-field__control--lg { height: 48px; font-size: var(--fs-lg); }

.date-field__control:focus-within {
  border-color: var(--brand);
  box-shadow: var(--shadow-focus);
}

.date-field.is-error .date-field__control {
  border-color: var(--danger);
  box-shadow: 0 0 0 3px rgba(198, 106, 106, 0.16);
}

.date-field.is-disabled .date-field__control {
  background: var(--bg-soft);
  color: var(--ink-3);
}

.date-field__segments {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  overflow: hidden;
}

.date-field__segment {
  width: 2.4em;
  min-width: 0;
  padding: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--ink-1);
  font: inherit;
  text-align: center;
}

.date-field__segment--year {
  width: 4.2em;
  text-align: left;
}

.date-field__segment::placeholder {
  color: var(--ink-3);
}

.date-field__dash {
  color: var(--line-strong);
  font-weight: 700;
  line-height: 1;
}

.date-field__clear {
  width: 20px;
  height: 20px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--line);
  color: var(--ink-3);
  cursor: pointer;
  font-size: var(--fs-md);
  line-height: 1;
}

.date-field__clear:hover {
  color: var(--ink-1);
}

.date-field__calendar {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--brand-soft);
  color: var(--brand-deep);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
}

.date-field__control--sm .date-field__calendar {
  width: 26px;
  height: 26px;
}

.date-field__control--sm .date-field__calendar-icon {
  width: 13px;
  height: 13px;
}

.date-field__control--sm .date-field__clear {
  width: 18px;
  height: 18px;
}

.date-field__calendar:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.date-field__calendar-icon {
  position: relative;
  width: 15px;
  height: 15px;
  border: 2px solid currentColor;
  border-radius: 4px;
}

.date-field__calendar-icon::before {
  content: '';
  position: absolute;
  left: -2px;
  right: -2px;
  top: 3px;
  border-top: 2px solid currentColor;
}

.date-field__calendar-icon::after {
  content: '';
  position: absolute;
  left: 3px;
  top: -5px;
  width: 5px;
  height: 4px;
  border-left: 2px solid currentColor;
  border-right: 2px solid currentColor;
}

.date-field__picker {
  position: absolute;
  z-index: calc(var(--z-dropdown) + 10);
  top: calc(100% + 8px);
  left: 0;
  width: min(320px, 100%);
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-card);
  box-shadow: var(--shadow-md);
}

.date-picker__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.date-picker__head strong {
  color: var(--ink-1);
  font-size: var(--fs-sm);
}

.date-picker__head button,
.date-picker__foot button,
.date-picker__days button {
  border: none;
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font: inherit;
}

.date-picker__head button {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-pill);
  font-size: var(--fs-xl);
  line-height: 1;
}

.date-picker__head button:hover,
.date-picker__days button:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.date-picker__week,
.date-picker__days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.date-picker__week {
  margin-bottom: 6px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  font-weight: 700;
  text-align: center;
}

.date-picker__days button {
  height: 34px;
  border-radius: var(--radius-2);
  font-size: var(--fs-sm);
}

.date-picker__days button.is-muted {
  color: var(--ink-3);
  opacity: 0.56;
}

.date-picker__days button.is-today {
  color: var(--brand-deep);
  font-weight: 700;
}

.date-picker__days button.is-selected {
  background: var(--brand);
  color: var(--ink-inverse);
  font-weight: 700;
}

.date-picker__foot {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.date-picker__foot button {
  min-height: 32px;
  padding: 0 10px;
  border-radius: var(--radius-pill);
  color: var(--brand-deep);
  font-size: var(--fs-sm);
  font-weight: 700;
}

.date-picker__foot button:hover {
  background: var(--brand-soft);
}

.date-field__msg {
  padding-left: 4px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.date-field__msg--error {
  color: var(--danger);
}
</style>
