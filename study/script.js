function createParagraph() {
  const para = document.createElement('p');
  para.textContent = '你点击了按钮，已新增一个段落。';
  document.body.appendChild(para);
}

const buttons = document.querySelectorAll('button');
for (const button of buttons) {
  button.addEventListener('click', createParagraph);
}
