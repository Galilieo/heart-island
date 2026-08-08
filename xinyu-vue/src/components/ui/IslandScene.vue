<template>
  <!--
    心屿岛景：深夜青暮色天空 + 地平线雾金 + 海面波层 + 一座有树与灯的小岛。
    纯装饰 SVG，slice 铺满父容器；动画只在未要求减少动态时播放。
  -->
  <svg
    class="island-scene"
    viewBox="0 0 800 360"
    preserveAspectRatio="xMidYMid slice"
    fill="none"
    aria-hidden="true"
  >
    <defs>
      <linearGradient id="island-sky" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#16323a" />
        <stop offset="0.52" stop-color="#2a5655" />
        <stop offset="0.82" stop-color="#7fa89b" />
        <stop offset="1" stop-color="#eec27f" />
      </linearGradient>
      <linearGradient id="island-sea" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#3d6f6e" />
        <stop offset="1" stop-color="#16323a" />
      </linearGradient>
      <radialGradient id="island-sun-glow" cx="0.5" cy="0.5" r="0.5">
        <stop offset="0" stop-color="#f6d9a0" stop-opacity="0.75" />
        <stop offset="1" stop-color="#f6d9a0" stop-opacity="0" />
      </radialGradient>
    </defs>

    <!-- 天空 -->
    <rect x="0" y="0" width="800" height="240" fill="url(#island-sky)" />

    <!-- 星 -->
    <g fill="#f6f1e4" opacity="0.5">
      <circle cx="96" cy="46" r="1.6" />
      <circle cx="212" cy="30" r="1.2" />
      <circle cx="352" cy="58" r="1.4" />
      <circle cx="520" cy="36" r="1.2" />
      <circle cx="700" cy="52" r="1.5" />
    </g>

    <!-- 太阳与光晕 -->
    <circle cx="616" cy="120" r="86" fill="url(#island-sun-glow)" />
    <circle cx="616" cy="120" r="30" fill="#f6d9a0" />

    <!-- 云（缓慢漂移） -->
    <g class="island-scene__cloud island-scene__cloud--1" fill="#f6f1e4" opacity="0.82">
      <ellipse cx="170" cy="86" rx="46" ry="16" />
      <ellipse cx="204" cy="74" rx="34" ry="14" />
      <ellipse cx="140" cy="76" rx="26" ry="11" />
    </g>
    <g class="island-scene__cloud island-scene__cloud--2" fill="#f6f1e4" opacity="0.6">
      <ellipse cx="430" cy="56" rx="38" ry="13" />
      <ellipse cx="458" cy="46" rx="26" ry="11" />
    </g>

    <!-- 海面 -->
    <rect x="0" y="236" width="800" height="124" fill="url(#island-sea)" />

    <!-- 小岛：丘 + 树 + 灯 -->
    <g>
      <path d="M470 240 Q556 160 650 240 Z" fill="#24514f" />
      <path d="M470 240 Q556 160 650 240" stroke="#eec27f" stroke-opacity="0.5" stroke-width="2" />
      <rect x="552" y="176" width="6" height="34" rx="3" fill="#16323a" />
      <circle cx="555" cy="168" r="16" fill="#3f7a6d" />
      <circle cx="538" cy="178" r="10" fill="#356b64" />
      <circle cx="572" cy="180" r="9" fill="#356b64" />
      <!-- 灯 -->
      <rect x="606" y="196" width="10" height="42" rx="4" fill="#16323a" />
      <circle cx="611" cy="192" r="5" fill="#f6d9a0" />
      <circle cx="611" cy="192" r="12" fill="#f6d9a0" opacity="0.25" />
    </g>

    <!-- 太阳的倒影 -->
    <ellipse cx="616" cy="262" rx="34" ry="5" fill="#f6d9a0" opacity="0.35" />
    <ellipse cx="616" cy="280" rx="22" ry="3.5" fill="#f6d9a0" opacity="0.22" />

    <!-- 波纹（缓慢流动） -->
    <g
      class="island-scene__waves"
      stroke="#f6f1e4"
      stroke-width="2"
      stroke-linecap="round"
      fill="none"
    >
      <path d="M-40 258 q20 -7 40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0" opacity="0.32" />
      <path d="M-40 292 q20 -6 40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0" opacity="0.22" />
      <path d="M-40 326 q20 -6 40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0" opacity="0.14" />
    </g>
  </svg>
</template>

<style scoped>
.island-scene {
  display: block;
  width: 100%;
  height: 100%;
}

@media (prefers-reduced-motion: no-preference) {
  .island-scene__cloud--1 {
    animation: island-cloud-1 26s ease-in-out infinite alternate;
  }
  .island-scene__cloud--2 {
    animation: island-cloud-2 32s ease-in-out infinite alternate;
  }
  .island-scene__waves {
    animation: island-waves 14s linear infinite;
  }
}

@keyframes island-cloud-1 {
  from { transform: translateX(0); }
  to { transform: translateX(26px); }
}

@keyframes island-cloud-2 {
  from { transform: translateX(0); }
  to { transform: translateX(-20px); }
}

@keyframes island-waves {
  from { transform: translateX(0); }
  to { transform: translateX(80px); }
}
</style>
