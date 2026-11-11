<script setup>
import AppShell from '../components/layout/AppShell.vue'
import BaseCard from '../components/ui/BaseCard.vue'
</script>

<template>
  <AppShell no-header width="narrow">
    <article class="container privacy">
      <RouterLink to="/home">返回心屿</RouterLink>
      <BaseCard padding="lg">
        <h1>心屿隐私政策</h1>
        <p class="privacy__date">更新日期：2026年11月11日</p>
        <p>心屿由个人开发者运营（以下简称“心屿运营者”）。如需咨询个人信息处理、申请访问、更正、删除或注销账号，
          请联系 <a href="mailto:jianggalilieo@gmail.com">jianggalilieo@gmail.com</a>。</p>

        <h2>我们处理哪些信息</h2>
        <p>注册和登录时处理用户名、密码和昵称，用于创建账号、验证身份和提供服务。
          密码以哈希形式保存，不保存登录时输入的明文密码。您主动填写的性别、生日、简介和城市用于展示或管理个人资料，未填写不影响记心情。</p>
        <p>保存您主动提交的心情类型、日记正文、时间和回复开关，以提供记录、历史回看和情绪统计。
          日记不向其他普通用户公开。社区帖子、话题、心情标签、评论和互动用于社区展示；
          匿名展示仍保留账号与内容的后台关联，不等于无法识别发布者。</p>

        <h2>AI 回复如何使用内容</h2>
        <p>开启 AI 回复时，系统会处理您提交的心情类型和正文；社区回复还使用话题信息。
          需要模型生成的内容会发送至当前接入的 DeepSeek 模型服务，生成结果会保存用于回看。
          发送正文中若包含您主动输入的个人信息，也可能随正文一并发送。关闭 AI 不影响保存记录，
          但关闭或删除不能撤回已经发送给模型服务的请求。</p>
        <p>AI 生成可能失败，或出现不准确、不适合您的内容。您可以关闭后续生成，或修改、删除相关记录。</p>

        <h2>管理员排障访问</h2>
        <p>为排查 AI 生成、保存或状态异常，以及处理您反馈的问题，经过授权的全站管理员可能按排障所需
          查询相关 AI 调用记录，包括关联账号和记录标识、AI 回复、错误详情，以及历史记录中已保存的输入或提示词。
          此类访问用于维护和排障，私人记录不会因此向其他普通用户公开。</p>

        <h2>保存与删除</h2>
        <p>账号资料、记录和社区内容在提供账号、历史回看及社区服务期间保存；任务状态和调用记录用于生成状态查询及排障。
          页面删除会使内容不可查询或不再公开展示，但不等于立即清除数据库中的所有关联记录、调用日志或备份。
          当前没有对上述数据统一执行固定天数的自动清理。</p>
        <p>如果您需要彻底删除关联数据、限制处理或注销账号，请通过上述邮箱联系。
          我们会核验必要的账号归属信息并告知处理范围；依法必须保留的信息另行说明。
          不再需要用于上述目的的信息，应删除或匿名化处理。</p>

        <h2>本机存储与账号安全</h2>
        <p>浏览器会保存登录令牌和基础账号信息，以保持登录状态；退出登录会清除这些登录信息。
          请勿在共用设备上长期保持登录。后台访问需要管理员权限。</p>

        <h2>您的选择与本政策更新</h2>
        <p>您可以在个人资料页修改资料，在记录或社区中编辑、删除自己的内容，按需选择是否生成 AI 回复。
          其他个人信息请求及对处理结果的疑问，可通过联系邮箱提出。处理目的、信息范围或服务提供方发生变化时，
          我们会更新本页面并按实际变化另行告知；需要您另行选择的，不以浏览本页代替您的选择。</p>
      </BaseCard>
    </article>
  </AppShell>
</template>

<style scoped>
.privacy { max-width: 800px; display: grid; gap: 18px; }
.privacy h1 { font-size: var(--fs-2xl); }
.privacy h2 { margin-top: 26px; font-size: var(--fs-lg); }
.privacy p { line-height: 1.9; color: var(--ink-2); overflow-wrap: anywhere; }
.privacy__date { font-size: var(--fs-sm); }
.privacy a { color: var(--brand-deep); }
</style>
