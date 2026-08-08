<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import AdminTabs from '../components/admin/AdminTabs.vue'
import AdminPageHeader from '../components/admin/AdminPageHeader.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import { useAdminStore } from '../stores/admin'

const adminStore = useAdminStore()

const { overview, overviewLoading } = storeToRefs(adminStore)

const aiFailureRate = computed(() => {
  const total = overview.value.aiReplyTotal || 0
  if (!total) return '0%'

  const failed = overview.value.aiReplyFailed || 0
  return `${Math.round((failed / total) * 100)}%`
})

// 按业务域分组展示：用户与账号 / 社区内容 / AI 服务。
// 只重组展示结构，字段与数据来源完全不变。
const statGroups = computed(() => [
  {
    name: '用户与账号',
    items: [
      { label: '用户总数', value: overview.value.userTotal, hint: '平台注册用户数量' },
      { label: '禁用用户', value: overview.value.userDisabled, hint: '当前不可登录的账号' }
    ]
  },
  {
    name: '社区内容',
    items: [
      { label: '帖子总数', value: overview.value.postTotal, hint: '社区发布内容数量' },
      { label: '隐藏帖子', value: overview.value.postHidden, hint: '后台已隐藏的帖子' },
      { label: '回复总数', value: overview.value.replyTotal, hint: '帖子下的互动回复' },
      { label: '隐藏回复', value: overview.value.replyHidden, hint: '后台已隐藏的回复' }
    ]
  },
  {
    name: 'AI 服务',
    items: [
      { label: 'AI调用总数', value: overview.value.aiReplyTotal, hint: '心情与社区触发的 AI 回复次数' },
      { label: 'AI成功次数', value: overview.value.aiReplySuccess, hint: '模型成功返回回复的次数' },
      { label: 'AI失败次数', value: overview.value.aiReplyFailed, hint: `当前失败率 ${aiFailureRate.value}` }
    ]
  }
])

onMounted(() => {
  adminStore.fetchOverview()
})
</script>

<template>
  <AppShell width="wide">
    <div class="container admin-dashboard">
      <AdminPageHeader
        title="概览"
        desc="查看平台用户、社区内容和 AI 服务调用的整体情况。"
      />

      <AdminTabs />

      <div v-if="overviewLoading" class="admin-dashboard__grid">
        <SkeletonCard v-for="i in 9" :key="i" rows="2" />
      </div>

      <div v-else class="admin-dashboard__groups">
        <section
          v-for="group in statGroups"
          :key="group.name"
          class="admin-dashboard__group"
        >
          <h2 class="admin-dashboard__group-title">{{ group.name }}</h2>
          <div class="admin-dashboard__grid">
            <BaseCard
              v-for="item in group.items"
              :key="item.label"
              padding="md"
              class="admin-dashboard__stat"
            >
              <p class="admin-dashboard__stat-label">{{ item.label }}</p>
              <strong class="admin-dashboard__stat-value">
                {{ item.value || 0 }}
              </strong>
              <p class="admin-dashboard__stat-hint">{{ item.hint }}</p>
            </BaseCard>
          </div>
        </section>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.admin-dashboard {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-dashboard__groups {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.admin-dashboard__group-title {
  margin: 0 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--sea);
  color: var(--ink-2);
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
  font-weight: 700;
  letter-spacing: 2px;
}

.admin-dashboard__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.admin-dashboard__stat {
  min-height: 0;
}

.admin-dashboard__stat :deep(.card__body) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.admin-dashboard__stat-label {
  margin: 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 700;
}

.admin-dashboard__stat-value {
  display: block;
  color: var(--brand-deep);
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.admin-dashboard__stat-hint {
  margin: 0;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

@media (max-width: 1024px) {
  .admin-dashboard__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 手机：保持 2 列紧凑网格，不做 9 张通栏长栈 */
@media (max-width: 480px) {
  .admin-dashboard__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .admin-dashboard__stat :deep(.card__body) {
    padding: 14px;
    gap: 6px;
  }

  .admin-dashboard__stat-label {
    font-size: var(--fs-xs);
  }

  .admin-dashboard__stat-value {
    font-size: var(--fs-xl);
  }

  .admin-dashboard__stat-hint {
    font-size: 11px;
  }
}
</style>
