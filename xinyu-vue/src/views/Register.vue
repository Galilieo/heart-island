<script setup>
// 注册页。结构与 Login 几乎一样，多一个昵称字段。
// 注册成功后回到登录页（不自动登录，让用户主动确认密码）。
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthLayout from '../components/layout/AuthLayout.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import { useAsyncAction } from '../composables/useAsyncAction'

const router = useRouter()
const userStore = useUserStore()
const toast = useToast()

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const nickname = ref('')

const { run: submit, loading } = useAsyncAction(
  () =>
    userStore.register({
      username: username.value.trim(),
      password: password.value,
      nickname: nickname.value.trim()
    }),
  { errorMsg: '注册失败' }
)

async function handleRegister() {
  if (!username.value.trim()) return toast.warn('请输入用户名')
  if (!password.value) return toast.warn('请输入密码')
  if (password.value.length < 6) return toast.warn('密码不能少于 6 位')
  if (password.value !== confirmPassword.value) return toast.warn('两次输入的密码不一致')
  if (!nickname.value.trim()) return toast.warn('请输入昵称')

  const res = await submit()
  if (res.ok) {
    toast.success(res.message || '注册成功，去登录吧')
    router.push('/')
  }
}

function goLogin() {
  router.push('/')
}
</script>

<template>
  <AuthLayout
    kicker="欢迎来到心屿"
    title="创建账号"
    subtitle="注册后就能记心情、发帖子，和大家聊聊。"
    slogan="开心的、烦心的，都可以写。"
  >
    <BaseInput
      v-model="username"
      label="用户名"
      placeholder="登录用的账号名"
      size="lg"
      @enter="handleRegister"
    />
    <BaseInput
      v-model="password"
      type="password"
      label="密码"
      placeholder="至少 6 位"
      size="lg"
      @enter="handleRegister"
    />
    <BaseInput
      v-model="confirmPassword"
      type="password"
      label="确认密码"
      placeholder="再输入一次密码"
      size="lg"
      @enter="handleRegister"
    />
    <BaseInput
      v-model="nickname"
      label="昵称"
      placeholder="其他人会看到的名字"
      size="lg"
      @enter="handleRegister"
    />
    <BaseButton
      variant="primary"
      size="lg"
      :loading="loading"
      block
      @click="handleRegister"
    >注册</BaseButton>

    <p>注册前请阅读 <RouterLink to="/privacy">隐私政策</RouterLink>，了解记录、AI回复和管理员排障访问的范围。</p>

    <template #footer>
      <BaseButton variant="link" @click="goLogin">
        已经有账号？回到登录 →
      </BaseButton>
    </template>
  </AuthLayout>
</template>
