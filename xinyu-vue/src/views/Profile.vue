<script setup>
// 个人资料页。两块独立表单：修改昵称、修改密码。
// 用户名不可改，仅展示。所有逻辑走 userStore，请求状态用 useAsyncAction。
import { ref, computed } from 'vue'
import AppShell from '../components/layout/AppShell.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import Doodle from '../components/ui/Doodle.vue'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import { useAsyncAction } from '../composables/useAsyncAction'

const userStore = useUserStore()
const toast = useToast()

const username = computed(() => userStore.loginUser?.username || '')
const avatarText = computed(
  () => (userStore.loginUser?.nickname || username.value || '心')[0].toUpperCase()
)

/* ───── 修改昵称 ───── */
const nickname = ref(userStore.loginUser?.nickname || '')

const { run: submitNickname, loading: savingNickname } = useAsyncAction(
  () => userStore.updateProfile({ nickname: nickname.value.trim() }),
  { successMsg: '昵称已更新' }
)

function handleSaveNickname() {
  const value = nickname.value.trim()
  if (!value) return toast.warn('昵称不能为空')
  if (value === userStore.loginUser?.nickname) return toast.info('昵称没有变化')
  submitNickname()
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
      <header class="profile__hero">
        <div class="profile__id">
          <span class="profile__avatar">{{ avatarText }}</span>
          <div>
            <p class="profile__kicker">个人资料</p>
            <h1 class="profile__name">{{ userStore.loginUser?.nickname || '心屿用户' }}</h1>
            <p class="profile__username">@{{ username }}</p>
          </div>
        </div>
        <Doodle name="heart" :size="44" color="rgba(217,140,122,0.55)" class="profile__doodle" />
      </header>

      <div class="profile__grid">
        <BaseCard padding="lg">
          <h2 class="profile__card-title">修改昵称</h2>
          <p class="profile__card-desc">昵称会展示在社区里，用户名不可修改。</p>

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
              @enter="handleSaveNickname"
            />
            <BaseButton
              variant="primary"
              :loading="savingNickname"
              @click="handleSaveNickname"
            >保存昵称</BaseButton>
          </div>
        </BaseCard>

        <BaseCard padding="lg">
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
  padding: 28px 32px;
  background: linear-gradient(135deg, var(--brand-soft), var(--accent-soft));
  border-radius: var(--radius-4);
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profile__id {
  display: flex;
  align-items: center;
  gap: 16px;
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

.profile__doodle {
  flex-shrink: 0;
}

.profile__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.profile__card-title {
  margin: 0;
  font-size: var(--fs-xl);
}

.profile__card-desc {
  margin: 6px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.profile__form {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile__form > :deep(.btn) {
  align-self: flex-start;
}

@media (max-width: 720px) {
  .profile__grid {
    grid-template-columns: 1fr;
  }
  .profile__hero {
    padding: 24px;
  }
}
</style>
