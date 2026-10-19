(() => {
  const toLocalDate = (date) => {
    const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return localDate.toISOString().slice(0, 10);
  };
  const today = new Date();
  const minimumDate = toLocalDate(today);
  const lastForecastDate = new Date(today);
  lastForecastDate.setDate(lastForecastDate.getDate() + 15);
  const maximumDate = toLocalDate(lastForecastDate);

  document.querySelectorAll('input[type="date"]').forEach((input) => {
    input.min = minimumDate;
    input.max = maximumDate;
    if (!input.value) input.value = minimumDate;
  });

  const labels = {0:'晴朗',1:'大致晴朗',2:'局部多云',3:'阴天',45:'有雾',48:'雾凇',51:'毛毛雨',53:'中等毛毛雨',55:'较强毛毛雨',56:'冻毛毛雨',57:'较强冻毛毛雨',61:'小雨',63:'中雨',65:'大雨',66:'冻雨',67:'较强冻雨',71:'小雪',73:'中雪',75:'大雪',77:'雪粒',80:'阵雨',81:'中等阵雨',82:'强阵雨',85:'阵雪',86:'强阵雪',95:'雷雨',96:'雷雨伴小冰雹',99:'雷雨伴较强冰雹'};
  document.querySelectorAll('[data-weather-code]').forEach((node) => { const code = node.dataset.code; if (code !== '' && code != null) node.textContent = labels[Number(code)] || ('天气代码 ' + code); });
})();
