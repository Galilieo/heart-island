<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import AdminPageTitle from '../components/admin/AdminPageTitle.vue'
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

// 限流观测：后端状态 + 今日各范围拒绝总数。
// 拒绝计数来自后端进程内统计，重启归零；降级中提示注意 Redis 状态。
const RATE_LIMIT_BACKEND_LABELS = {
  redis: 'Redis',
  local_only: '本机（未配置 Redis）',
  local_degraded: '本机（Redis 降级中）',
  disabled: '已关闭'
}

const rateLimitBackendLabel = computed(() =>
  RATE_LIMIT_BACKEND_LABELS[overview.value.rateLimitBackend] || '未知'
)

const rateLimitRejections = computed(() =>
  // 后端 Long 全局序列化为字符串（JacksonConfig），求和前先转数字，避免拼接。
  Object.values(overview.value.rateLimitRejections || {}).reduce((sum, count) => sum + Number(count), 0)
)

// 按业务域分组展示：用户与账号 / 社区内容 / AI 服务。
// 只重组展示结构，字段与数据来源完全不变；指标卡一排 4 张，手机一排 2 张。
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
      {
        label: 'AI失败次数',
        value: overview.value.aiReplyFailed,
        hint: `当前失败率 ${aiFailureRate.value}`,
        // 只有确实有失败记录时才用危险色；0 次失败按普通提示文字显示。
        tone: (overview.value.aiReplyFailed || 0) > 0 ? 'danger' : 'default'
      },
      {
        label: '限流后端',
        value: rateLimitBackendLabel.value,
        hint: '付费 AI 调用的共享计数来源',
        // Redis 不可用退回本机时提醒管理员关注。
        tone: overview.value.rateLimitBackend === 'local_degraded' ? 'danger' : 'default'
      },
      {
        label: '今日限流拒绝',
        value: rateLimitRejections.value,
        hint: '各范围拒绝合计（进程内计数，重启归零）'
      }
    ]
  }
])

onMounted(() => {
  adminStore.fetchOverview()
})
</script>

<template>
  <div class="admin-dashboard">
    <AdminPageTitle
      title="概览"
      desc="用户、社区与 AI 服务的整体情况。"
    />

    <div v-if="overviewLoading" class="admin-dashboard__kpis">
      <SkeletonCard v-for="i in 9" :key="i" rows="2" />
    </div>

    <div v-else class="admin-dashboard__groups">
      <section v-for="group in statGroups" :key="group.name">
        <h2 class="admin-dashboard__group-title">{{ group.name }}</h2>
        <div class="admin-dashboard__kpis">
          <article
            v-for="item in group.items"
            :key="item.label"
            class="admin-dashboard__kpi"
          >
            <p class="admin-dashboard__kpi-label">{{ item.label }}</p>
            <strong class="admin-dashboard__kpi-value">
              {{ item.value || 0 }}
            </strong>
            <p
              class="admin-dashboard__kpi-hint"
              :class="{
                'is-danger': item.tone === 'danger',
                'admin-dashboard__kpi-hint--failure': item.label === 'AI失败次数'
              }"
            >
              {{ item.hint }}
            </p>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-dashboard {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-dashboard__groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-dashboard__group-title {
  margin: 6px 0 10px;
  color: var(--ink-2);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

.admin-dashboard__kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.admin-dashboard__kpi {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-card);
}

.admin-dashboard__kpi-label {
  color: var(--ink-2);
  font-size: 12px;
  font-weight: 600;
}

.admin-dashboard__kpi-value {
  color: var(--ink-1);
  font-family: var(--font-sans);
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.admin-dashboard__kpi-hint {
  overflow: hidden;
  color: var(--ink-3);
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-dashboard__kpi-hint.is-danger {
  color: var(--danger-ink);
  font-weight: 600;
}

@media (max-width: 1024px) {
  .admin-dashboard__kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .admin-dashboard__groups {
    gap: 16px;
  }

  .admin-dashboard__kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .admin-dashboard__kpi {
    padding: 12px;
    gap: 4px;
  }

  .admin-dashboard__kpi-value {
    font-size: 22px;
  }

  .admin-dashboard__kpi-hint {
    display: -webkit-box;
    font-size: 11px;
    white-space: normal;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}
</style>
