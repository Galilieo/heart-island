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
  if (!password.value.trim()) return toast.warn('请输入密码')
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
    kicker="新的开始"
    title="创建账号"
    subtitle="给自己的心情留一个安静的位置，从今天开始慢慢记录。"
    slogan="慢一点，没有关系。"
  >
    <BaseInput
      v-model="username"
      placeholder="用户名"
      size="lg"
      @enter="handleRegister"
    />
    <BaseInput
      v-model="password"
      type="password"
      placeholder="密码"
      size="lg"
      @enter="handleRegister"
    />
    <BaseInput
      v-model="nickname"
      placeholder="昵称（其他人会看到）"
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

    <template #footer>
      <BaseButton variant="link" @click="goLogin">
        已经有账号？回到登录 →
      </BaseButton>
    </template>
  </AuthLayout>
</template>
