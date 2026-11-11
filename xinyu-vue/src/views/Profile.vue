<script setup>
// 个人资料页：基本信息（昵称 / 性别 / 生日 / 签名 / 城市） + 修改密码。
// 头部为头像 + 昵称概况 + 四项数据条；下方用局部 tabs 分层「基本资料 / 账号安全」。
// 用户名不可改，仅展示。所有逻辑走 userStore + userApi，tabs 只是展示层状态。
import { ref, computed, onMounted } from 'vue'
import AppShell from '../components/layout/AppShell.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseDateInput from '../components/ui/BaseDateInput.vue'
import BaseTextarea from '../components/ui/BaseTextarea.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import avatarUrl from '../assets/xiaoya/xinyu-avatar.svg'
import { useUserStore } from '../stores/user'
import { userApi } from '../api/user'
import { useToast } from '../composables/useToast'
import { useAsyncAction } from '../composables/useAsyncAction'

const userStore = useUserStore()
const toast = useToast()

const username = computed(() => userStore.loginUser?.username || '')

const GENDER_OPTIONS = [
  { label: '不愿透露', value: 'UNDISCLOSED' },
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '其他', value: 'OTHER' }
]

const GENDER_LABEL = {
  MALE: '♂ 男',
  FEMALE: '♀ 女',
  OTHER: '· 其他',
  UNDISCLOSED: ''
}

/* ───── hero 展示数据 ───── */
const heroGender = computed(() => GENDER_LABEL[userStore.loginUser?.gender] || '')
const heroAge = computed(() => {
  const b = userStore.loginUser?.birthday
  if (!b) return ''
  const birth = new Date(b)
  if (Number.isNaN(birth.getTime())) return ''
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const m = today.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--
  return age >= 0 ? `${age} 岁` : ''
})
const heroCity = computed(() => userStore.loginUser?.city || '')
const heroBio = computed(() => userStore.loginUser?.bio || '')

const heroMeta = computed(() => {
  return [heroGender.value, heroAge.value, heroCity.value].filter(Boolean).join(' · ')
})

/* ───── 数据小条 ───── */
const stats = ref({ moodCount: 0, postCount: 0, favoriteCount: 0, joinDays: 0 })

const { run: doFetchStats, loading: statsLoading } = useAsyncAction(
  async () => {
    const res = await userApi.profileStats()
    if (res.ok) stats.value = res.data || stats.value
    return res
  },
  { silent: true }
)

onMounted(() => {
  doFetchStats()
})

/* ───── 局部 tabs：基本资料 / 账号安全（纯展示层状态） ───── */
const activeTab = ref('profile')
const TABS = [
  { key: 'profile', label: '基本资料' },
  { key: 'security', label: '账号安全' }
]

/* ───── 基本信息表单 ───── */
const nickname = ref(userStore.loginUser?.nickname || '')
const gender = ref(userStore.loginUser?.gender || 'UNDISCLOSED')
const birthday = ref(userStore.loginUser?.birthday || '')
const bio = ref(userStore.loginUser?.bio || '')
const city = ref(userStore.loginUser?.city || '')

const { run: submitProfile, loading: savingProfile } = useAsyncAction(
  () =>
    userStore.updateProfile({
      nickname: nickname.value.trim(),
      gender: gender.value,
      birthday: birthday.value || null,
      bio: bio.value.trim(),
      city: city.value.trim()
    }),
  { successMsg: '资料已更新' }
)

function handleSaveProfile() {
  if (!nickname.value.trim()) return toast.warn('昵称不能为空')
  if (bio.value.length > 300) return toast.warn('签名不能超过 300 字')
  if (city.value.length > 50) return toast.warn('城市不能超过 50 字')
  submitProfile()
}

/* ───── 修改密码 ───── */
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

const { run: submitPassword, loading: savingPassword } = useAsyncAction(
  () =>
    userStore.updatePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value
    }),
  { successMsg: '密码修改成功' }
)

async function handleSavePassword() {
  if (!oldPassword.value) return toast.warn('请输入原密码')
  if (!newPassword.value) return toast.warn('请输入新密码')
  if (newPassword.value.length < 6) return toast.warn('新密码不能少于 6 位')
  if (newPassword.value !== confirmPassword.value) {
    return toast.warn('两次输入的新密码不一致')
  }

  const res = await submitPassword()
  if (res.ok) {
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  }
}
</script>

<template>
  <AppShell>
    <div class="container profile">
      <!-- 页头：头像 + 昵称 / 用户名 / 概况 / 签名，同其他页面的手账页头 -->
      <header class="profile__head">
        <img :src="avatarUrl" alt="" aria-hidden="true" width="64" height="64" class="profile__avatar" />
        <div class="profile__head-text">
          <h1 class="profile__name">{{ userStore.loginUser?.nickname || '心屿用户' }}</h1>
          <p class="profile__username">@{{ username }}</p>
          <p v-if="heroMeta" class="profile__meta">{{ heroMeta }}</p>
          <p v-if="heroBio" class="profile__bio">「{{ heroBio }}」</p>
        </div>
      </header>

      <!-- 数据条：一行四项，项间虚线竖线；手机 2×2 -->
      <div class="profile__stats" :class="{ 'is-loading': statsLoading }">
        <div class="profile__stat">
          <strong>{{ stats.moodCount }}</strong>
          <span>心情记录</span>
        </div>
        <div class="profile__stat">
          <strong>{{ stats.postCount }}</strong>
          <span>我的帖子</span>
        </div>
        <div class="profile__stat">
          <strong>{{ stats.favoriteCount }}</strong>
          <span>收藏</span>
        </div>
        <div class="profile__stat">
          <strong>{{ stats.joinDays }}<small>天</small></strong>
          <span>加入心屿</span>
        </div>
      </div>

      <!-- 设置区：局部 tabs 分层 -->
      <BaseCard padding="lg" class="profile__panel">
        <div class="profile__tabs" role="tablist" aria-label="资料设置">
          <button
            v-for="tab in TABS"
            :key="tab.key"
            type="button"
            role="tab"
            class="profile__tab"
            :class="{ 'is-active': activeTab === tab.key }"
            :aria-selected="activeTab === tab.key"
            @click="activeTab = tab.key"
          >{{ tab.label }}</button>
        </div>

        <!-- 基本资料 -->
        <section
          v-show="activeTab === 'profile'"
          role="tabpanel"
          aria-label="基本资料"
          class="profile__pane"
        >
          <p class="profile__card-desc">昵称只在顶栏和个人页显示，社区里统一显示为「匿名岛民」。用户名不可修改，其它都可选填。</p>

          <div class="profile__form profile__form--grid">
            <BaseInput
              label="用户名"
              :model-value="username"
              disabled
            />
            <BaseInput
              v-model="nickname"
              label="昵称"
              placeholder="给自己起个昵称"
            />
            <BaseSelect
              v-model="gender"
              label="性别"
              :options="GENDER_OPTIONS"
            />
            <BaseDateInput
              v-model="birthday"
              label="生日"
              hint="可直接输入数字，也可以点右侧日历选择"
            />
            <BaseInput
              v-model="city"
              label="城市"
              placeholder="例如：上海"
            />
            <BaseTextarea
              v-model="bio"
              label="个性签名"
              placeholder="一句话介绍自己"
              :maxlength="300"
              :rows="3"
              class="profile__span"
            />
            <div class="profile__actions profile__span">
              <BaseButton
                variant="primary"
                :loading="savingProfile"
                @click="handleSaveProfile"
              >保存资料</BaseButton>
            </div>
          </div>
        </section>

        <!-- 账号安全 -->
        <section
          v-show="activeTab === 'security'"
          role="tabpanel"
          aria-label="账号安全"
          class="profile__pane"
        >
          <p class="profile__card-desc">为了安全，修改密码需要先验证原密码。</p>

          <div class="profile__form profile__form--narrow">
            <BaseInput
              v-model="oldPassword"
              type="password"
              label="原密码"
              placeholder="请输入原密码"
            />
            <BaseInput
              v-model="newPassword"
              type="password"
              label="新密码"
              placeholder="至少 6 位"
            />
            <BaseInput
              v-model="confirmPassword"
              type="password"
              label="确认新密码"
              placeholder="再输入一次新密码"
              @enter="handleSavePassword"
            />
            <div class="profile__actions">
              <BaseButton
                variant="primary"
                :loading="savingPassword"
                @click="handleSavePassword"
              >保存密码</BaseButton>
            </div>
          </div>
        </section>
      </BaseCard>
    </div>
  </AppShell>
</template>

<style scoped>
/* 与其他页面同宽的单列（680px），页面容器自己控制宽度 */
.profile {
  width: 100%;
  max-width: 680px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ── 页头：头像 + 身份信息 ── */
.profile__head {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.profile__head-text {
  min-width: 0;
}

.profile__avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--bg-soft);
  object-fit: contain;
  flex-shrink: 0;
}

.profile__name {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
  line-height: 1.3;
}

.profile__username {
  margin: 2px 0 0;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.profile__meta {
  margin: 4px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.profile__bio {
  margin: 4px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
  max-width: 460px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* ── 数据条：一行四项，项间虚线竖线；加载中整体变淡 ── */
.profile__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding: 4px 0;
  transition: opacity var(--t-fast) var(--ease-soft);
}

.profile__stats.is-loading {
  opacity: 0.6;
}

.profile__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 0;
  padding: 10px 8px;
}

.profile__stat + .profile__stat {
  border-left: 1px dashed var(--line-strong);
}

.profile__stat strong {
  display: inline-flex;
  align-items: baseline;
  font-family: var(--font-display);
  font-size: var(--fs-2xl);
  font-weight: 400;
  color: var(--ink-1);
  line-height: 1.2;
  white-space: nowrap;
}

.profile__stat strong small {
  margin-left: 2px;
  color: var(--ink-3);
  font-family: var(--font-sans);
  font-size: var(--fs-xs);
  font-weight: 400;
}

.profile__stat span {
  color: var(--ink-3);
  font-size: var(--fs-xs);
  line-height: 1.2;
  white-space: nowrap;
}

/* ── 设置面板 + tabs ── */
.profile__panel {
  width: 100%;
  min-width: 0;
}

/* tabs 与首页 / 社区页的范围按钮同一套样式 */
.profile__tabs {
  display: inline-flex;
  gap: 4px;
}

.profile__tab {
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.profile__tab:not(.is-active):hover {
  background: var(--bg-soft);
  color: var(--ink-1);
}

.profile__tab.is-active {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

.profile__tab:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.profile__pane {
  margin-top: 18px;
}

.profile__card-desc {
  margin: 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.profile__form {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 桌面：基本资料两列成对，签名/操作通栏 */
.profile__form--grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 22px;
  align-items: start;
}

/* >=1024px：面板与名片同宽后，拉开列距让两列读宽更平衡 */
@media (min-width: 1024px) {
  .profile__form--grid {
    gap: 20px 28px;
  }
}

.profile__span {
  grid-column: 1 / -1;
}

/* 密码表单：单列窄读宽，安全感来自留白 */
.profile__form--narrow {
  max-width: 420px;
}

.profile__form :deep(.field__control),
.profile__form :deep(.date-field__control),
.profile__form :deep(.select__trigger) {
  min-height: 48px;
}

.profile__form :deep(.field__label),
.profile__form :deep(.date-field__label),
.profile__form :deep(.select__label) {
  font-size: var(--fs-sm);
}

.profile__actions {
  display: flex;
  justify-content: flex-start;
}

/* 手机：卡片收紧、tabs 补足触控高度、表单转单列 */
@media (max-width: 768px) {
  .profile__panel :deep(.card__body) {
    padding: 18px 16px;
  }

  .profile__tab {
    min-height: 44px;
    padding: 0 16px;
  }

  .profile__form--grid {
    grid-template-columns: 1fr;
  }

  .profile__actions :deep(.btn) {
    width: 100%;
    min-height: 48px;
  }
}

/* ≤640px：数据条转 2×2，第一列去掉左线、第二行补上边线 */
@media (max-width: 640px) {
  .profile__name {
    font-size: var(--fs-2xl);
  }

  .profile__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .profile__stat {
    padding: 12px;
  }

  .profile__stat:nth-child(3) {
    border-left: none;
    border-top: 1px dashed var(--line-strong);
  }

  .profile__stat:nth-child(4) {
    border-top: 1px dashed var(--line-strong);
  }
}
</style>
