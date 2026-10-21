(() => {
  const form = document.querySelector('#chat-form');
  const input = document.querySelector('#chat-input');
  const messages = document.querySelector('#chat-messages');
  const results = document.querySelector('#result-area');
  const status = document.querySelector('#agent-status');
  const send = form.querySelector('button');
  let sessionId = sessionStorage.getItem('travel-agent-session') || null;

  function addMessage(role, text, pending = false) {
    const article = document.createElement('article');
    article.className = `message ${role === 'user' ? 'user-message' : 'assistant-message'}`;
    const avatar = document.createElement('span');
    avatar.className = 'message-avatar';
    avatar.textContent = role === 'user' ? '我' : 'W';
    const body = document.createElement('div');
    body.className = 'message-body';
    const paragraph = document.createElement('p');
    paragraph.textContent = text;
    if (pending) paragraph.className = 'typing';
    body.append(paragraph);
    article.append(avatar, body);
    messages.append(article);
    messages.scrollTop = messages.scrollHeight;
    return paragraph;
  }

  function field(label, value, suffix = '') {
    const box = document.createElement('div');
    box.className = 'weather-fact';
    const title = document.createElement('small');
    title.textContent = label;
    const data = document.createElement('strong');
    data.textContent = value == null ? '暂无数据' : `${value}${suffix}`;
    box.append(title, data);
    return box;
  }

  function renderCards(payload) {
    results.replaceChildren();
    if (payload.weather) {
      const weather = payload.weather;
      const card = document.createElement('article');
      card.className = 'agent-card weather-agent-card';
      const heading = document.createElement('div');
      heading.className = 'agent-card-heading';
      const title = document.createElement('h2');
      title.textContent = `${weather.city || '天气'} · ${weather.date || ''}`;
      const source = document.createElement('span');
      source.textContent = weather.source || '天气查询';
      heading.append(title, source);
      const facts = document.createElement('div');
      facts.className = 'agent-weather-facts';
      facts.append(
        field('天气代码', weather.weatherCode),
        field('最高温', weather.maxTemp, ' °C'),
        field('最低温', weather.minTemp, ' °C'),
        field('降水概率', weather.precipitationProbability, '%'),
        field('降水量', weather.precipitation, ' mm'),
        field('最大风速', weather.windSpeed, ' km/h')
      );
      card.append(heading, facts);
      results.append(card);
    }
    if (payload.travelPlan) {
      const plan = payload.travelPlan;
      const card = document.createElement('article');
      card.className = 'agent-card plan-agent-card';
      const heading = document.createElement('div');
      heading.className = 'agent-card-heading';
      const title = document.createElement('h2');
      title.textContent = `${plan.city || '出行计划'} · ${plan.date || ''}`;
      heading.append(title);
      const summary = document.createElement('p');
      summary.className = 'agent-weather-summary';
      summary.textContent = plan.weatherSummary || '天气信息不足，请以最新预报为准。';
      const timeline = document.createElement('div');
      timeline.className = 'agent-timeline';
      [['上午', plan.morningActivity], ['下午', plan.afternoonActivity], ['晚间', plan.eveningActivity]].forEach(([period, activity]) => {
        const row = document.createElement('section');
        const label = document.createElement('b');
        label.textContent = period;
        const text = document.createElement('p');
        text.textContent = activity || '暂无安排';
        row.append(label, text);
        timeline.append(row);
      });
      const advice = document.createElement('p');
      advice.className = 'agent-advice';
      advice.textContent = `穿衣建议：${plan.clothingAdvice || '暂无数据'}`;
      card.append(heading, summary, timeline, advice);
      const lists = document.createElement('div');
      lists.className = 'agent-plan-lists';
      [['随身物品', plan.itemsToPack], ['出行提醒', plan.warnings]].forEach(([label, values]) => {
        const section = document.createElement('section');
        const titleNode = document.createElement('b');
        titleNode.textContent = label;
        const list = document.createElement('ul');
        (Array.isArray(values) ? values : []).forEach((item) => {
          const li = document.createElement('li'); li.textContent = item; list.append(li);
        });
        if (!list.children.length) { const li = document.createElement('li'); li.textContent = '暂无'; list.append(li); }
        section.append(titleNode, list); lists.append(section);
      });
      card.append(lists);
      results.append(card);
    }
  }

  async function submitMessage(text) {
    addMessage('user', text);
    const pending = addMessage('assistant', '正在理解你的需求并准备查询…', true);
    form.classList.add('is-loading');
    send.disabled = true;
    status.textContent = '正在处理';
    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId, message: text })
      });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || '请求失败，请稍后重试。');
      sessionId = payload.sessionId || sessionId;
      if (sessionId) sessionStorage.setItem('travel-agent-session', sessionId);
      pending.textContent = payload.reply || '我暂时没有生成回复，请再试一次。';
      pending.classList.remove('typing');
      if (Array.isArray(payload.toolCalls) && payload.toolCalls.length) {
        const log = document.createElement('div');
        log.className = 'tool-call-log';
        const labels = { search_city: '地点搜索', get_weather: '天气查询', generate_travel_plan: '行程生成', modify_travel_plan: '计划修改' };
        payload.toolCalls.forEach((call) => {
          const item = document.createElement('span');
          item.className = call.status === 'SUCCESS' ? 'tool-call-success' : 'tool-call-failed';
          item.textContent = `${call.status === 'SUCCESS' ? '✓' : '!'} ${labels[call.name] || call.name}${call.status === 'FAILED' && call.detail ? `：${call.detail}` : ''}`;
          log.append(item);
        });
        pending.parentElement.append(log);
      }
      renderCards(payload);
      const successfulCalls = (payload.toolCalls || []).filter((call) => call.status === 'SUCCESS');
      const failedCalls = (payload.toolCalls || []).filter((call) => call.status === 'FAILED');
      status.textContent = failedCalls.length ? '部分工具调用失败' : successfulCalls.length ? `已执行 ${successfulCalls.length} 次工具调用` : payload.status === 'NEEDS_INPUT' ? '等待你确认地点' : payload.status === 'FAILED' ? '本次请求未完成' : '已回复';
    } catch (error) {
      pending.textContent = error.message || '网络暂时不可用，请稍后重试。';
      pending.classList.remove('typing');
      status.textContent = '本次请求未完成';
    } finally {
      form.classList.remove('is-loading');
      send.disabled = false;
      input.focus();
    }
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const text = input.value.trim();
    if (!text || send.disabled) return;
    input.value = '';
    submitMessage(text);
  });
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); form.requestSubmit(); }
  });
  document.querySelectorAll('[data-prompt]').forEach((button) => {
    button.addEventListener('click', () => submitMessage(button.dataset.prompt));
  });
})();
