<script setup>
// 个人资料页：基本信息（昵称 / 性别 / 生日 / 签名 / 城市） + 修改密码。
// hero 展示性别、年龄、城市、签名；下方一行 4 个数据小条。
// 用户名不可改，仅展示。所有逻辑走 userStore + userApi。
import { ref, computed, onMounted } from 'vue'
import AppShell from '../components/layout/AppShell.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseDateInput from '../components/ui/BaseDateInput.vue'
import BaseTextarea from '../components/ui/BaseTextarea.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import Doodle from '../components/ui/Doodle.vue'
import { useUserStore } from '../stores/user'
import { userApi } from '../api/user'
import { useToast } from '../composables/useToast'
import { useAsyncAction } from '../composables/useAsyncAction'

const userStore = useUserStore()
const toast = useToast()

const username = computed(() => userStore.loginUser?.username || '')
const avatarText = computed(
  () => (userStore.loginUser?.nickname || username.value || '心')[0].toUpperCase()
)

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
  <AppShell width="narrow">
    <div class="container profile">
      <!-- Hero -->
      <header class="profile__hero">
        <div class="profile__id">
          <span class="profile__avatar">{{ avatarText }}</span>
          <div class="profile__id-text">
            <p class="profile__kicker">个人资料</p>
            <h1 class="profile__name">{{ userStore.loginUser?.nickname || '心屿用户' }}</h1>
            <p class="profile__username">@{{ username }}</p>
            <p v-if="heroMeta" class="profile__meta">{{ heroMeta }}</p>
            <p v-if="heroBio" class="profile__bio">"{{ heroBio }}"</p>
          </div>
        </div>
        <Doodle name="heart" :size="44" color="rgba(217,140,122,0.55)" class="profile__doodle" />
      </header>

      <!-- 数据小条 -->
      <div class="profile__stats" :class="{ 'is-loading': statsLoading }">
        <div class="profile__stat">
          <span class="profile__stat-icon">
            <Doodle name="heart" :size="22" color="currentColor" :stroke-width="2.4" />
          </span>
          <div class="profile__stat-body">
            <strong>{{ stats.moodCount }}</strong>
            <span>心情记录</span>
          </div>
        </div>
        <div class="profile__stat">
          <span class="profile__stat-icon">
            <Doodle name="wave" :size="22" color="currentColor" :stroke-width="2.4" />
          </span>
          <div class="profile__stat-body">
            <strong>{{ stats.postCount }}</strong>
            <span>我的帖子</span>
          </div>
        </div>
        <div class="profile__stat">
          <span class="profile__stat-icon">
            <Doodle name="star" :size="22" color="currentColor" :stroke-width="2.4" />
          </span>
          <div class="profile__stat-body">
            <strong>{{ stats.favoriteCount }}</strong>
            <span>收藏</span>
          </div>
        </div>
        <div class="profile__stat">
          <span class="profile__stat-icon">
            <Doodle name="sun" :size="22" color="currentColor" :stroke-width="2.4" />
          </span>
          <div class="profile__stat-body">
            <strong>{{ stats.joinDays }}<small>天</small></strong>
            <span>陪伴天数</span>
          </div>
        </div>
      </div>

      <div class="profile__grid">
        <!-- 基本信息 -->
        <BaseCard padding="lg" class="profile__panel">
          <h2 class="profile__card-title">基本信息</h2>
          <p class="profile__card-desc">昵称会展示在社区里，用户名不可修改。其它字段都可选填。</p>

          <div class="profile__form">
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
            />
            <BaseButton
              variant="primary"
              :loading="savingProfile"
              @click="handleSaveProfile"
            >保存资料</BaseButton>
          </div>
        </BaseCard>

        <!-- 修改密码 -->
        <BaseCard padding="lg" class="profile__panel profile__panel--password">
          <h2 class="profile__card-title">修改密码</h2>
          <p class="profile__card-desc">为了安全，修改密码需要先验证原密码。</p>

          <div class="profile__form">
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
            <BaseButton
              variant="primary"
              :loading="savingPassword"
              @click="handleSavePassword"
            >保存密码</BaseButton>
          </div>
        </BaseCard>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.profile__hero {
  position: relative;
  width: min(760px, 100%);
  align-self: center;
  padding: 28px 32px;
  background: linear-gradient(135deg, var(--brand-soft), var(--accent-soft));
  border-radius: var(--radius-4);
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.profile__id {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.profile__id-text {
  min-width: 0;
}

.profile__avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), var(--accent));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-2xl);
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 10px 22px rgba(111, 169, 164, 0.3);
}

.profile__kicker {
  margin: 0;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.profile__name {
  margin: 4px 0 0;
  font-size: var(--fs-2xl);
}

.profile__username {
  margin: 2px 0 0;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.profile__meta {
  margin: 6px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  letter-spacing: 1px;
}

.profile__bio {
  margin: 6px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-style: italic;
  line-height: 1.6;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.profile__doodle {
  flex-shrink: 0;
}

/* 数据小条 */
.profile__stats {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  width: min(760px, 100%);
  align-self: center;
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  box-shadow: 0 10px 26px rgba(87, 68, 43, 0.04);
  overflow: hidden;
  transition: opacity var(--t-fast) var(--ease-soft);
}

.profile__stats::before {
  content: '';
  position: absolute;
  inset: 22px 0;
  pointer-events: none;
  background-image:
    linear-gradient(
      90deg,
      transparent calc(25% - 0.5px),
      var(--line) calc(25% - 0.5px),
      var(--line) calc(25% + 0.5px),
      transparent calc(25% + 0.5px)
    ),
    linear-gradient(
      90deg,
      transparent calc(50% - 0.5px),
      var(--line) calc(50% - 0.5px),
      var(--line) calc(50% + 0.5px),
      transparent calc(50% + 0.5px)
    ),
    linear-gradient(
      90deg,
      transparent calc(75% - 0.5px),
      var(--line) calc(75% - 0.5px),
      var(--line) calc(75% + 0.5px),
      transparent calc(75% + 0.5px)
    );
}

.profile__stats.is-loading {
  opacity: 0.6;
}

.profile__stat {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  gap: 7px;
  min-height: 88px;
  padding: 8px 10px;
}

.profile__stat-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, var(--brand-soft), rgba(248, 226, 218, 0.75));
  color: var(--brand-deep);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.profile__stat-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  text-align: center;
  justify-content: center;
}

.profile__stat strong {
  font-family: var(--font-display);
  font-size: 32px;
  color: var(--brand-deep);
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  display: inline-flex;
  align-items: baseline;
  justify-content: center;
}

.profile__stat strong small {
  margin-left: 3px;
  color: var(--ink-3);
  font-family: var(--font-sans);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.profile__stat-body span {
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 700;
  line-height: 1.2;
  white-space: nowrap;
}

.profile__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 28px;
  width: min(1040px, 100%);
  align-self: center;
  align-items: start;
}

.profile__panel {
  min-width: 0;
}

.profile__panel :deep(.card__body) {
  padding: 34px 40px;
}

.profile__panel--password {
  max-width: 460px;
  width: 100%;
}

.profile__card-title {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
}

.profile__card-desc {
  margin: 6px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.profile__form {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
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

.profile__form > :deep(.btn) {
  align-self: flex-start;
}

@media (max-width: 980px) {
  .profile__hero {
    padding: 24px;
  }
  .profile__grid {
    grid-template-columns: 1fr;
    width: min(680px, 100%);
    gap: 18px;
  }
  .profile__panel--password {
    max-width: none;
  }
}

@media (max-width: 640px) {
  .profile__panel :deep(.card__body) {
    padding: 28px 24px;
  }
  .profile__card-title {
    font-size: var(--fs-xl);
  }
  .profile__form {
    gap: 16px;
  }
  .profile__form > :deep(.btn) {
    width: 100%;
    min-height: 48px;
  }
}

@media (max-width: 520px) {
  .profile__stats {
    grid-template-columns: repeat(2, 1fr);
    padding: 12px;
  }
  .profile__stats::before {
    inset: 18px 22px;
    background-image:
      linear-gradient(
        90deg,
        transparent calc(50% - 0.5px),
        var(--line) calc(50% - 0.5px),
        var(--line) calc(50% + 0.5px),
        transparent calc(50% + 0.5px)
      ),
      linear-gradient(
        180deg,
        transparent calc(50% - 0.5px),
        var(--line) calc(50% - 0.5px),
        var(--line) calc(50% + 0.5px),
        transparent calc(50% + 0.5px)
      );
  }
  .profile__stat {
    min-height: 94px;
    padding: 12px 10px;
  }
  .profile__stat strong {
    font-size: 32px;
  }
  .profile__stat-body span {
    font-size: var(--fs-xs);
  }
}

@media (max-width: 380px) {
  .profile__stats {
    grid-template-columns: 1fr;
  }
  .profile__stats::before {
    inset: 0 20px;
    background-image:
      linear-gradient(
        180deg,
        transparent calc(25% - 0.5px),
        var(--line) calc(25% - 0.5px),
        var(--line) calc(25% + 0.5px),
        transparent calc(25% + 0.5px)
      ),
      linear-gradient(
        180deg,
        transparent calc(50% - 0.5px),
        var(--line) calc(50% - 0.5px),
        var(--line) calc(50% + 0.5px),
        transparent calc(50% + 0.5px)
      ),
      linear-gradient(
        180deg,
        transparent calc(75% - 0.5px),
        var(--line) calc(75% - 0.5px),
        var(--line) calc(75% + 0.5px),
        transparent calc(75% + 0.5px)
      );
  }
  .profile__stat {
    min-height: 68px;
    flex-direction: row;
    justify-content: flex-start;
    padding: 10px 14px;
    display: flex;
  }
  .profile__stat-body {
    align-items: flex-start;
    text-align: left;
  }
}
</style>
