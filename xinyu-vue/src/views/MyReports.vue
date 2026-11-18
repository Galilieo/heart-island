<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseTag from '../components/ui/BaseTag.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import { useCommunityStore } from '../stores/community'
import { reasonLabel, myReportResultMeta, formatReportTime } from '../utils/communityReport'

const router = useRouter()
const communityStore = useCommunityStore()
const { myReports, myReportsTotal, myReportsPage, myReportsPages, myReportsLoading } =
  storeToRefs(communityStore)
const { myReportsPageSize } = communityStore

onMounted(() => {
  communityStore.fetchMyReports(1)
})

function handlePageChange(pageNum) {
  communityStore.fetchMyReports(pageNum)
}

function goBack() {
  router.push('/profile')
}
</script>

<template>
  <AppShell>
    <div class="container my-reports">
      <button type="button" class="my-reports__back" @click="goBack">
        <span aria-hidden="true">←</span> 返回我的
      </button>

      <header class="my-reports__head">
        <h1>我的举报</h1>
        <p>只展示最近 90 天内提交的举报记录。</p>
      </header>

      <div v-if="myReportsLoading && !myReports.length" class="my-reports__list">
        <SkeletonCard v-for="i in 3" :key="i" rows="2" />
      </div>

      <div v-else-if="myReports.length" class="my-reports__list">
        <BaseCard v-for="item in myReports" :key="item.id" padding="md" class="my-reports__card">
          <div class="my-reports__row-top">
            <div class="my-reports__badges">
              <BaseTag tone="neutral" size="sm">
                {{ item.targetType === 'REPLY' ? '回复' : '帖子' }}
              </BaseTag>
              <span class="my-reports__reason">{{ reasonLabel(item.reason) }}</span>
            </div>
            <BaseTag :tone="myReportResultMeta(item.status).tone" size="sm">
              {{ myReportResultMeta(item.status).text }}
            </BaseTag>
          </div>

          <p class="my-reports__excerpt">{{ item.excerpt || '（无内容摘录）' }}</p>

          <footer class="my-reports__meta">
            <span>举报于 {{ formatReportTime(item.createTime) }}</span>
            <span v-if="item.status === 'HIDDEN' && item.hideReason">
              隐藏原因：{{ reasonLabel(item.hideReason) }}
            </span>
          </footer>
        </BaseCard>

        <BasePagination
          v-if="myReportsTotal > myReportsPageSize"
          :page-num="Number(myReportsPage)"
          :pages="Number(myReportsPages)"
          :total="Number(myReportsTotal)"
          :page-size="myReportsPageSize"
          @change="handlePageChange"
        />
      </div>

      <EmptyState
        v-else
        doodle="cloud"
        title="暂无举报记录"
        hint="遇到违规内容可以在帖子详情页右上角「⋯」发起举报"
      />
    </div>
  </AppShell>
</template>

<style scoped>
.my-reports {
  width: 100%;
  max-width: 680px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.my-reports__back {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 8px;
  border: none;
  background: transparent;
  color: var(--brand-deep);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
}

.my-reports__head h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
}

.my-reports__head p {
  margin: 4px 0 0;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.my-reports__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.my-reports__card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.my-reports__row-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.my-reports__badges {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.my-reports__reason {
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.my-reports__excerpt {
  margin: 0;
  color: var(--ink-1);
  font-size: var(--fs-md);
  line-height: 1.7;
  word-break: break-word;
}

.my-reports__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}
</style>
