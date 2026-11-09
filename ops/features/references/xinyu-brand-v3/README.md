# 心屿品牌素材包（小芽 v3）

吉祥物“小芽”：海上的一座小岛，头顶长出两片子叶，合起来是一颗心。
`overview.png` 是全部内容的总览。

## 目录

| 文件 | 用途 |
| --- | --- |
| `logo/xinyu-mascot.svg` | 主 logo（完整版小芽）。页头 32px、登录页 88px 都用它 |
| `logo/xinyu-mascot-dark.svg` | 深夜模式用，海浪颜色调亮 |
| `logo/xinyu-avatar.svg` | 无水版，圆形头像（AI 回复、个人页）用 |
| `logo/xinyu-lockup.svg` / `-dark.svg` | 小芽 + “心屿”字标，横版，用在页脚、分享图、对外介绍 |
| `logo/xinyu-app-icon.svg` | 带米白底的 App 图标源文件 |
| `logo/*.png` | 不支持 SVG 的场合（文档、社交平台头像） |
| `favicon/` | 浏览器标签页和手机桌面图标；favicon 用完整版小芽裁紧，手机桌面图标带米白底（iOS 不支持透明底，透明处会变黑） |
| `mascot/svg/`、`mascot/png/` | 23 个表情，PNG 为 512×512 透明底 |
| `mascot/animated/` | 23 个表情的动态版（动图 v2，海浪会流动），`preview.html` 可直接预览 |
| `spec/` | 比例构造图和四视图（正面、¾ 侧面、侧面、背面），重画时照这个 |

## 放进 xinyu-vue

1. `favicon/` 里的文件全部放到 `public/`，替换原来的 `favicon.ico`、`favicon-32.png`。
2. `logo/xinyu-mascot.svg` 放到 `src/assets/`，替换 `logo-xinyu.png`（986KB）和 `logo-xinyu-small.webp`。
3. `index.html` 的 `<head>` 里换成：

```html
<link rel="icon" href="/favicon.ico" sizes="48x48">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
```

4. `AppHeader.vue` 的 `.brand__logo` 去掉 `border-radius`、`box-shadow` 和 `object-fit: cover`，那是给旧的方块 logo 用的；小芽是透明底。
5. 以后做 PWA 时，`icon-192.png` 和 `icon-512.png` 写进 `manifest.webmanifest`。

## 表情

### 心情（和网站的 13 个心情选项一一对应）

| 文件 | 心情 | 画法 |
| --- | --- | --- |
| `xiaoya-happy` | 开心 | 弯弯眼、张嘴笑，旁边闪星星 |
| `xiaoya-content` | 满足 | 眯眼、猫猫嘴，旁边飘两个音符 |
| `xiaoya-hopeful` | 期待 | 星星眼，子叶往上翘，旁边闪星光 |
| `xiaoya-touched` | 感动 | 眼里含泪、微笑，两边泪珠 |
| `xiaoya-calm` | 平静 | 闭眼微笑 |
| `xiaoya-anxious` | 焦虑 | 小眼睛、皱眉、波浪嘴，冒汗 |
| `xiaoya-sad` | 难过 | 皱眉、撇嘴，眼泪掉下来，子叶歪向一边 |
| `xiaoya-angry` | 生气 | 竖眉，怒气符号 |
| `xiaoya-wronged` | 委屈 | 泪汪汪往上看，嘴往下撇，眼角挂着一颗还没掉的泪 |
| `xiaoya-lonely` | 孤独 | 外眼角下垂，子叶耷拉，望着远处飞走的小鸟 |
| `xiaoya-lost` | 迷茫 | 眼睛放空，子叶一高一低，周围飘着问号 |
| `xiaoya-tired` | 疲惫 | 打哈欠，子叶歪倒，Z 字 |
| `xiaoya-other` | 其他 | 默认表情，不带情绪 |

前端可以直接用心情名映射文件名，例如 `{ 开心: 'happy', 满足: 'content', 期待: 'hopeful', 委屈: 'wronged', 孤独: 'lonely', 迷茫: 'lost', 其他: 'other' }`。

### AI 状态和其他表情

| 文件 | 名称 | 建议用在 |
| --- | --- | --- |
| `xiaoya-thinking` | AI 思考中 | 等待 AI 回复时 |
| `xiaoya-reply` | AI 回应 | AI 回复的头像 |
| `xiaoya-default` | 默认 | logo、空状态 |
| `xiaoya-surprised` | 惊讶 | 通知、提示 |
| `xiaoya-shy` | 害羞 | 收到回应、贴图 |
| `xiaoya-speechless` | 无语 | 贴图 |
| `xiaoya-love` | 喜欢 | 收藏、点赞 |
| `xiaoya-cheer` | 加油 | 鼓励、连续记录 |
| `xiaoya-goodnight` | 晚安 | 夜间提醒、深夜模式 |
| `xiaoya-confused` | 困惑 | 贴图（原来的“其他”，歪头加问号） |

注意：风险分级判定为高风险、走危机流程时，不要显示卡通表情。

## 动态表情（`mascot/animated/`）

全部 23 个表情的动态版，外加 `xiaoya-avatar.svg`（无水版头像）和 `xiaoya-default-dark.svg`（深夜模式）。双击 `preview.html` 可以一次看全部，里面还有心情选择和 AI 回复的交互示例（单文件，可以直接发给别人看）。

每个文件约 8KB（gzip 后约 4KB），动画写在 SVG 内部（身体和表情用 CSS，海浪用 SVG 自带的 SMIL 动画），不需要 JS 或 Lottie。

海浪从左往右流动，两端淡出；身体沿水面的切线跟着一起移动，任何时刻都不会露缝。流速跟情绪走：生气、焦虑约 3 秒一个波，默认 6 秒，晚安 10 秒。用法和静态图一样：

```html
<img src="/mascot/xiaoya-thinking.svg" alt="心屿正在思考" width="48" height="48">
```

- 必须用 `<img>`、CSS `background-image` 或直接内联 `<svg>`。微信等平台头像、贴纸不支持 SVG 动画，那种场合用静态 PNG 或之后导出的 GIF。
- 系统开启“减少动态效果”（prefers-reduced-motion）时会自动停在静止姿势，海浪也会换回静态的圆头版本。
- 每个文件的样式都带了自己的类名前缀（如 `.xy-happy`），多个内联在同一页面也不会互相影响。
- 文件名和静态版一一对应，替换时只换目录就行。例如心情按钮平时用静态版，选中后换成动态版。
- 同一屏不要同时放太多动态的小芽，选中项、AI 等待状态这类需要吸引注意的地方再用。

| 文件 | 名称 | 动作 |
| --- | --- | --- |
| `xiaoya-happy` | 开心 | 小跳起来，子叶向上一翘，旁边闪星星 |
| `xiaoya-content` | 满足 | 左右轻轻摇，像在哼歌，音符往上飘 |
| `xiaoya-hopeful` | 期待 | 时不时踮脚往上张望，星光一闪一闪 |
| `xiaoya-touched` | 感动 | 眼里含泪，两边泪珠滑落 |
| `xiaoya-calm` | 平静 | 很慢的浮动，子叶几乎不动 |
| `xiaoya-anxious` | 焦虑 | 身体细微发抖，汗滴往下滑 |
| `xiaoya-sad` | 难过 | 子叶下垂慢慢晃，眼泪掉落 |
| `xiaoya-angry` | 生气 | 左右抖动，怒气符号一胀一缩 |
| `xiaoya-wronged` | 委屈 | 时不时抽一下鼻子，眼角的泪珠跟着一颤，泪光一闪一闪 |
| `xiaoya-lonely` | 孤独 | 慢慢呼吸，小鸟扇着翅膀越飞越远 |
| `xiaoya-lost` | 迷茫 | 左右张望，问号一个个冒出来 |
| `xiaoya-tired` | 疲惫 | 缓慢呼吸起伏，Z 字往上飘 |
| `xiaoya-other` | 其他 | 和默认一样，轻轻浮动，偶尔眨眼 |
| `xiaoya-thinking` | AI 思考中 | 气泡里三个点依次跳动 |
| `xiaoya-reply` | AI 回应 | 轻快浮动，像在说话 |
| `xiaoya-default` | 默认 | 在水里轻轻浮动，子叶随风摆，偶尔眨眼 |
| `xiaoya-surprised` | 惊讶 | 猛地一跳，感叹号弹出 |
| `xiaoya-shy` | 害羞 | 身体往一边扭，腮红一阵阵变深 |
| `xiaoya-speechless` | 无语 | 面无表情，省略号一个个出现 |
| `xiaoya-love` | 喜欢 | 轻快浮动，小爱心往上飘 |
| `xiaoya-cheer` | 加油 | 快速连跳，子叶用力翘起 |
| `xiaoya-goodnight` | 晚安 | 睡着呼吸，月亮微微发光；夜深了的招呼语也用它 |
| `xiaoya-confused` | 困惑 | 歪头来回，问号上下浮 |

## 比例规范（W = 身体在水面处的宽度 45）

- 身体露出水面高 26（约 0.58W），圆肩、左右对称，边缘一圈深色
- 两片圆头子叶，各向外张开 38°，根部合拢；整体宽约 0.57W
- 眼睛中心距 0.25W，半径 2.8；右上大高光、左下小高光
- 水面一条波浪线，线宽 3；身体沿波浪线切齐，不留缝
- 锁定：身体轮廓、眼距和眼睛大小、子叶形状、配色；表情只改眉、眼、嘴；子叶可以动

## 颜色

| 用途 | 色值 |
| --- | --- |
| 身体 | `#a6cbb6` |
| 身体边缘 | `#79ad97` |
| 暗部 | `#86b29e` |
| 高光 | `#d3e7da` |
| 子叶 | `#8dc35a`（背面 `#74a94a`） |
| 茎 | `#72a556` |
| 海浪 | `#5a978d`（深夜模式 `#8bb2ac`） |
| 眼睛、嘴 | `#22403b` |
| 腮红 | `#f29a86` |
| 字标 | `#2f5d58`，“心”上的点 `#f29a86` |

## 使用规范

- 所有尺寸都用完整版，favicon 只是裁掉四周留白
- 四周至少留出一个眼睛直径的空白
- 不加描边、不拉伸、不旋转身体、不换配色
- 深色背景只把海浪换成 `#8bb2ac`

## 字体说明

字标“心屿”由思源黑体 Medium（Noto Sans CJK SC，SIL OFL 1.1 授权）转曲线后，把“心”的上点改色而成。OFL 允许这种用法。如果以后要注册商标，建议请设计师微调笔画，做出自己的特征。
