import assert from 'node:assert/strict'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createSSRApp, h } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

globalThis.localStorage ??= { getItem: () => null, setItem: () => {}, removeItem: () => {} }

const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false,
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('../src', import.meta.url)) }
  },
  optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false }
})

const { default: PostCard } = await server.ssrLoadModule('/src/components/community/PostCard.vue')
const { default: PostDetail } = await server.ssrLoadModule('/src/views/PostDetail.vue')
const { useCommunityStore } = await server.ssrLoadModule('/src/stores/community.js')
await server.close()

for (const resultKind of ['SUPPORT', 'MODEL', null]) {
  test(`社区卡片和详情按 resultKind 展示回复（${resultKind}）`, async () => {
    const post = {
      id: '1',
      topicId: '1',
      content: '合成帖子',
      moodType: '平静',
      aiReply: '合成回复',
      resultKind
    }

    const card = await renderToString(createSSRApp({ render: () => h(PostCard, { post }) }))
    const pinia = createPinia()
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/community/post/:id', component: PostDetail },
        { path: '/:pathMatch(.*)*', component: { render: () => null } }
      ]
    })
    const detailApp = createSSRApp({ render: () => h(PostDetail) })
      .use(pinia)
      .use(router)
    await router.push('/community/post/1')
    useCommunityStore(pinia).currentPost = post
    const detail = await renderToString(detailApp)

    assert.equal(detail.includes('这条回复由 AI 自动生成'), resultKind !== 'SUPPORT')
    for (const html of [card, detail]) {
      const note = html.match(/<aside class="note[^"]*"[^>]*>[\s\S]*?<\/aside>/)?.[0]
      assert.ok(note, '已有回复必须显示批注')
      assert.match(note, /合成回复/)
      if (resultKind === 'SUPPORT') {
        assert.ok(note.includes('note--support'), 'SUPPORT 需要使用危机样式')
        assert.match(note, /心屿/)
        assert.doesNotMatch(note, /<picture|小芽写道/)
      } else {
        assert.doesNotMatch(note, /note--support/)
        assert.match(note, /<picture/)
        assert.match(note, /小芽写道/)
      }
    }
  })
}

test('作者打开后来被隐藏的 SUPPORT 帖子时，保留状态提示并隐藏小芽', async () => {
  const post = {
    id: '1',
    content: '合成隐藏帖',
    moodType: '平静',
    aiReply: '合成固定回应',
    resultKind: 'SUPPORT',
    hidden: true,
    mine: true
  }
  const html = await renderToString(
    createSSRApp({ render: () => h(PostCard, { post, variant: 'detail' }) })
  )
  assert.match(html, /仅你自己可见/)
  assert.doesNotMatch(html, /<picture/)
})
