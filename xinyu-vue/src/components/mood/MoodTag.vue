<script setup>
// 心情标签：BaseTag 柔和配色 + 小芽表情，供首页时间线与详情弹窗复用。
// 危机流程回复（resultKind 为 SUPPORT）由调用方传 hideMascot 隐藏表情；本组件只做展示。
import { computed } from 'vue'
import BaseTag from '../ui/BaseTag.vue'
import Xiaoya from '../ui/Xiaoya.vue'
import { moodTone, moodExpression } from '../../utils/mood'

const props = defineProps({
  moodType: { type: String, default: '' },
  hideMascot: Boolean,
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v)
  }
})

// 小芽尺寸与 BaseTag 的三档尺寸对应，保持标签高度稳定。
const MASCOT_SIZES = { sm: 20, md: 28, lg: 36 }

const mascotSize = computed(() => MASCOT_SIZES[props.size] || MASCOT_SIZES.md)
</script>

<template>
  <BaseTag :tone="moodTone(moodType)" variant="soft" :size="size">
    <Xiaoya v-if="!hideMascot" :expression="moodExpression(moodType)" :size="mascotSize" />
    {{ moodType }}
  </BaseTag>
</template>
