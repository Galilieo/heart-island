import { defineStore } from 'pinia'
import { createCatalogModule } from './admin/catalogModule'
import { createModerationModule } from './admin/moderationModule'
import { createReportsModule } from './admin/reportsModule'

/**
 * 后台管理统一 Store（内部按举报、内容审核、概览/用户/话题/AI记录拆模块维护，对外导出契约保持不变）。
 */
export const useAdminStore = defineStore('admin', () => ({
  ...createCatalogModule(),
  ...createModerationModule(),
  ...createReportsModule()
}))
