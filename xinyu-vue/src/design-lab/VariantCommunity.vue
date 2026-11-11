<script setup>
// 手账风格延伸到社区页：与首页共用顶栏、主栏 + 侧栏版式、心情标签和“小芽批注”式 AI 回复。
import { ref } from 'vue'
import Xiaoya from '../components/ui/Xiaoya.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import JournalNav from './JournalNav.vue'
import XiaoyaNote from './XiaoyaNote.vue'
import { moodExpression } from '../utils/mood'
import { MOCK_TOPICS, MOCK_POSTS, toneStyle } from './mock'

const activeTopic = ref('全部')
const sortBy = ref('latest')
</script>

<template>
  <div class="p">
    <JournalNav active="社区" wide />

    <div class="p-layout">
      <main class="p-main">
        <header class="p-head">
          <div>
            <h1>大家最近怎么样</h1>
            <p>帖子都以「匿名岛民」展示，聊聊近况，也请尊重不同的感受。</p>
          </div>
          <BaseButton>写一篇</BaseButton>
        </header>

        <div class="p-filter">
          <div class="p-topics" role="tablist" aria-label="话题">
            <button
              v-for="name in ['全部', ...MOCK_TOPICS.map(topic => topic.name)]"
              :key="name"
              type="button"
              role="tab"
              :aria-selected="activeTopic === name"
              :class="{ 'is-on': activeTopic === name }"
              @click="activeTopic = name"
            >{{ name }}</button>
          </div>
          <div class="p-sort">
            <button :class="{ 'is-on': sortBy === 'latest' }" type="button" @click="sortBy = 'latest'">最新</button>
            <button :class="{ 'is-on': sortBy === 'hot' }" type="button" @click="sortBy = 'hot'">最热</button>
          </div>
        </div>

        <article v-for="post in MOCK_POSTS" :key="post.id" class="p-card">
          <header class="p-card__meta">
            <span class="p-tag" :style="toneStyle(post.moodType)">
              <Xiaoya :expression="moodExpression(post.moodType)" :size="22" />{{ post.moodType }}
            </span>
            <span>匿名岛民</span>
            <span class="p-dot">·</span>
            <span>{{ post.topic }}</span>
            <span class="p-card__time">{{ post.timeLabel }}</span>
          </header>

          <p class="p-card__text">{{ post.content }}</p>

          <XiaoyaNote v-if="post.aiReply" :text="post.aiReply" />

          <footer class="p-card__actions">
            <button type="button">回复 {{ post.replyCount }}</button>
            <button type="button">抱抱 {{ post.likeCount }}</button>
            <button type="button" :class="{ 'is-on': post.favorited }">{{ post.favorited ? '已收藏' : '收藏' }}</button>
          </footer>
        </article>
      </main>

      <aside class="p-rail">
        <section class="p-rail__card">
          <h3>社区约定</h3>
          <ul class="p-rules">
            <li>所有人都以「匿名岛民」出现，不打听、不公开他人身份。</li>
            <li>可以不同意，但请好好说话。</li>
            <li>如果你或别人正处于危险中，请立即联系身边的人或当地急救。</li>
          </ul>
        </section>

        <section class="p-rail__card">
          <h3>话题</h3>
          <ul class="p-topic-list">
            <li v-for="topic in MOCK_TOPICS" :key="topic.name">
              <span>{{ topic.name }}</span>
              <span class="p-count">{{ topic.count }}</span>
            </li>
          </ul>
        </section>

        <section class="p-rail__card p-mine">
          <Xiaoya expression="content" :size="44" />
          <div>
            <h3>我的帖子</h3>
            <p>发过 2 篇，收到 9 个抱抱</p>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.p {
  min-height: 100%;
  background:
    radial-gradient(circle at 80% 0%, var(--accent-soft), transparent 40%),
    var(--bg-base);
  color: var(--ink-1);
}

.p-layout {
  display: grid;
  grid-template-columns: minmax(0, 680px) 300px;
  justify-content: center;
  gap: 40px;
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 20px 56px;
}

.p-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  padding-top: 8px;
}

.p-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}

.p-head h1 {
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
}

.p-head p {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.p-filter {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed var(--line-strong);
}

.p-topics {
  display: flex;
  gap: 4px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.p-topics button,
.p-sort button {
  flex-shrink: 0;
  padding: 4px 10px;
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.p-topics button.is-on,
.p-sort button.is-on {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

.p-sort {
  display: flex;
  flex-shrink: 0;
}

.p-card {
  padding: 20px 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.p-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.p-card__time { margin-left: auto; }

.p-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: 4px;
  padding: 2px 10px 2px 4px;
  border-radius: var(--radius-pill);
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-weight: 700;
}

.p-card__text {
  margin-top: 10px;
  font-size: var(--fs-base);
  line-height: 1.8;
}

.p-card__actions {
  display: flex;
  gap: 18px;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.p-card__actions button {
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.p-card__actions button.is-on { color: var(--accent-ink); font-weight: 700; }

.p-rail {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.p-rail__card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.p-rail__card h3 {
  margin-bottom: 10px;
  font-size: var(--fs-lg);
  font-weight: 400;
}

.p-rules {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 1.2em;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.p-topic-list {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--fs-sm);
}

.p-topic-list li {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px dashed var(--line);
}

.p-topic-list li:last-child { border-bottom: 0; }

.p-count { color: var(--ink-3); }

.p-mine {
  display: flex;
  align-items: center;
  gap: 12px;
}

.p-mine h3 { margin-bottom: 2px; }

.p-mine p {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

/* 中等及以下宽度：侧栏移到列表下方，只保留社区约定 */
@container (max-width: 980px) {
  .p-layout {
    grid-template-columns: minmax(0, 1fr);
    max-width: 720px;
    gap: 16px;
  }
  .p-rail { position: static; padding-top: 0; }
  .p-rail__card:not(:first-child) { display: none; }
}

@container (max-width: 640px) {
  .p-layout { padding: 0 16px 40px; }
  .p-head { align-items: flex-start; flex-direction: column; }
  .p-head h1 { font-size: var(--fs-2xl); }
  .p-head :deep(.btn) { width: 100%; }
  .p-card { padding: 16px; }
}
</style>
