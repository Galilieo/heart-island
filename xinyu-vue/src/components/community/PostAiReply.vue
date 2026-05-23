<script setup>
// 帖子的「心屿」AI 陪伴回复卡片。
// 仅在 reply 非空时使用；老帖 aiReply 为 null 时父级条件渲染不挂载本组件。
defineProps({
  reply: { type: String, required: true }
})
</script>

<template>
  <section class="ai-card">
    <header class="ai-card__head">
      <span class="ai-card__avatar" aria-hidden="true">心</span>
      <div class="ai-card__title">
        <p class="ai-card__name">心屿 · AI 陪伴</p>
        <p class="ai-card__hint">这条回复由 AI 自动生成，不代表平台立场</p>
      </div>
    </header>
    <div class="ai-card__body">
      <p class="ai-card__text">{{ reply }}</p>
    </div>
  </section>
</template>

<style scoped>
.ai-card {
  position: relative;
  padding: 18px 20px 18px 26px;
  background: linear-gradient(135deg, var(--accent-soft), var(--bg-soft));
  border: 1px solid #f1cdc2;
  border-radius: var(--radius-3);
  overflow: hidden;
}

.ai-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 14%;
  bottom: 14%;
  width: 3px;
  border-radius: var(--radius-pill);
  background: var(--accent);
}

.ai-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.ai-card__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--brand));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: var(--fs-md);
  flex-shrink: 0;
}

.ai-card__title {
  min-width: 0;
}

.ai-card__name {
  margin: 0;
  font-size: var(--fs-md);
  font-weight: 700;
  color: #a45a4a;
  letter-spacing: 1px;
}

.ai-card__hint {
  margin: 2px 0 0;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.ai-card__body {
  padding: 12px 14px;
  background: var(--bg-card);
  border-radius: var(--radius-2);
  border: 1px solid var(--line);
}

.ai-card__text {
  margin: 0;
  color: var(--ink-1);
  line-height: 1.85;
  font-size: var(--fs-md);
  white-space: pre-wrap;
}
</style>
