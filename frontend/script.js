const STORAGE_KEY = "heart_island_bottles";

const emotionGrid = document.getElementById("emotionGrid");
const messageInput = document.getElementById("message");
const submitButton = document.getElementById("submitBottle");
const submitHint = document.getElementById("submitHint");
const aiReply = document.getElementById("aiReply");
const bottleList = document.getElementById("bottleList");
const stats = document.getElementById("stats");
const timeline = document.getElementById("timeline");

let selectedEmotion = "";
let bottles = loadBottles();

function loadBottles() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveBottles() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(bottles));
}

function pickEmotion(button) {
  document.querySelectorAll(".emotion-chip").forEach((chip) => {
    chip.classList.remove("active");
  });
  button.classList.add("active");
  selectedEmotion = button.dataset.emotion || "";
}

function generateReply(emotion, text) {
  const map = {
    "焦虑": "你已经很努力了，先让自己慢一点，哪怕只是深呼吸三次，也是在照顾自己。",
    "迷茫": "迷茫并不说明你不行，它常常是成长前的过渡地带。",
    "疲惫": "辛苦了，允许自己休息不是退步，而是给心重新充电。",
    "平静": "这种平静很珍贵，愿你把它变成接下来几天的小锚点。",
    "开心": "谢谢你分享这份开心，它也会悄悄照亮看到这条消息的人。",
    "失落": "失落的时候先别急着给自己下结论，你的感受本身就值得被看见。"
  };

  const base = map[emotion] || "谢谢你愿意表达自己，情绪被说出来，就是自我照顾的一步。";
  const ending = text.length > 55 ? "你写得很真诚，这本身就很有力量。" : "你已经迈出了很重要的一步。";
  return `${base}${ending}`;
}

function formatTime(ts) {
  return new Date(ts).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function renderBottles() {
  if (bottles.length === 0) {
    bottleList.innerHTML = "<li class='bottle-item'>还没有漂流瓶，投递第一条吧。</li>";
    return;
  }

  bottleList.innerHTML = "";
  bottles.slice(0, 6).forEach((item) => {
    const li = document.createElement("li");
    li.className = "bottle-item";
    li.innerHTML = `
      <div class="meta">
        <span class="tag">${item.emotion}</span>
        <span>${formatTime(item.createdAt)}</span>
        <span>同感 ${item.likes}</span>
      </div>
      <div>${item.text}</div>
      <div class="actions">
        <button type="button" class="action-btn" data-like-id="${item.id}">我也有同感</button>
      </div>
    `;
    bottleList.appendChild(li);
  });
}

function renderStats() {
  const count = bottles.length;
  const latest = bottles[0] ? bottles[0].emotion : "-";
  const thisWeek = bottles.filter((item) => Date.now() - item.createdAt < 7 * 24 * 60 * 60 * 1000).length;

  stats.innerHTML = `
    <div class="stat-box"><span>总投递数</span><strong>${count}</strong></div>
    <div class="stat-box"><span>最近情绪</span><strong>${latest}</strong></div>
    <div class="stat-box"><span>近 7 天</span><strong>${thisWeek}</strong></div>
  `;

  if (count === 0) {
    timeline.innerHTML = "<li>还没有记录，先写下一条情绪吧。</li>";
    return;
  }

  timeline.innerHTML = "";
  bottles.slice(0, 5).forEach((item) => {
    const li = document.createElement("li");
    li.textContent = `${formatTime(item.createdAt)} · ${item.emotion} · ${item.text.slice(0, 30)}`;
    timeline.appendChild(li);
  });
}

emotionGrid.addEventListener("click", (event) => {
  const target = event.target;
  if (!(target instanceof HTMLButtonElement)) {
    return;
  }
  pickEmotion(target);
  submitHint.textContent = `已选择情绪：${selectedEmotion}`;
});

submitButton.addEventListener("click", () => {
  const text = messageInput.value.trim();

  if (!selectedEmotion) {
    submitHint.textContent = "请先选择一个情绪标签。";
    return;
  }
  if (!text) {
    submitHint.textContent = "请输入一些内容再投递。";
    return;
  }

  const newBottle = {
    id: crypto.randomUUID(),
    emotion: selectedEmotion,
    text,
    likes: 0,
    createdAt: Date.now(),
    reply: generateReply(selectedEmotion, text)
  };

  bottles.unshift(newBottle);
  saveBottles();
  aiReply.textContent = newBottle.reply;
  submitHint.textContent = "投递成功，已收到温和回应。";
  messageInput.value = "";

  renderBottles();
  renderStats();
});

bottleList.addEventListener("click", (event) => {
  const target = event.target;
  if (!(target instanceof HTMLButtonElement)) {
    return;
  }

  const id = target.dataset.likeId;
  if (!id) {
    return;
  }

  const item = bottles.find((bottle) => bottle.id === id);
  if (!item) {
    return;
  }

  item.likes += 1;
  saveBottles();
  renderBottles();
  renderStats();
});

renderBottles();
renderStats();
