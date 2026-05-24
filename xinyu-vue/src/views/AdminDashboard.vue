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

const statCards = computed(() => [
  {
    label: '用户总数',
    value: overview.value.userTotal,
    hint: '平台注册用户数量'
  },
  {
    label: '禁用用户',
    value: overview.value.userDisabled,
    hint: '当前不可登录的账号'
  },
  {
    label: '帖子总数',
    value: overview.value.postTotal,
    hint: '社区发布内容数量'
  },
  {
    label: '隐藏帖子',
    value: overview.value.postHidden,
    hint: '后台已隐藏的帖子'
  },
  {
    label: '回复总数',
    value: overview.value.replyTotal,
    hint: '帖子下的互动回复'
  },
  {
    label: '隐藏回复',
    value: overview.value.replyHidden,
    hint: '后台已隐藏的回复'
  },
  {
    label: 'AI调用总数',
    value: overview.value.aiReplyTotal,
    hint: '心情与社区触发的 AI 回复次数'
  },
  {
    label: 'AI成功次数',
    value: overview.value.aiReplySuccess,
    hint: '模型成功返回回复的次数'
  },
  {
    label: 'AI失败次数',
    value: overview.value.aiReplyFailed,
    hint: `当前失败率 ${aiFailureRate.value}`
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

      <div v-else class="admin-dashboard__grid">
        <BaseCard
          v-for="item in statCards"
          :key="item.label"
          padding="lg"
          class="admin-dashboard__stat"
        >
          <p class="admin-dashboard__stat-label">{{ item.label }}</p>
          <strong class="admin-dashboard__stat-value">
            {{ item.value || 0 }}
          </strong>
          <p class="admin-dashboard__stat-hint">{{ item.hint }}</p>
        </BaseCard>
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

.admin-dashboard__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.admin-dashboard__stat {
  min-height: 150px;
}

.admin-dashboard__stat-label {
  margin: 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 700;
}

.admin-dashboard__stat-value {
  display: block;
  margin-top: 12px;
  color: var(--brand-deep);
  font-size: var(--fs-3xl);
  line-height: 1;
}

.admin-dashboard__stat-hint {
  margin: 12px 0 0;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

@media (max-width: 1024px) {
  .admin-dashboard__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .admin-dashboard__grid {
    grid-template-columns: 1fr;
  }
}
</style>
