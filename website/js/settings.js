/**
 * settings.js
 * Настройки профиля и внешнего вида: тема, акцентный цвет, размер шрифта,
 * обращение.
 *
 * Скрипт подключается в <head> ДО отрисовки страницы, чтобы применить
 * сохранённые настройки мгновенно и избежать «мигания» темы.
 *
 * Принцип работы:
 *  - конфиг хранится в localStorage под ключом 'pf_portfolio_settings';
 *  - при загрузке читаем конфиг и вешаем на <html> атрибуты data-theme,
 *    data-font-size и CSS-переменную --accent;
 *  - изменения применяются через window.PF.set(), который сохраняет
 *    данные и перечитывает атрибуты на всех страницах сайта.
 */
(function () {
  'use strict';

  var STORAGE_KEY = 'pf_portfolio_settings';

  /* Значения по умолчанию */
  var DEFAULTS = {
    theme: 'dark',          // 'light' | 'dark' | 'system'
    accent: '#0000ff',      // акцентный цвет (hex)
    fontSize: 'm',          // 's' | 'm' | 'l'
    addressName: 'Иван'     // как к вам обращаться
  };

  var config = load();

  /* Читаем конфиг из localStorage и подмешиваем значения по умолчанию */
  function load() {
    var merged = Object.assign({}, DEFAULTS);
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        Object.assign(merged, JSON.parse(raw));
      }
    } catch (err) {
      /* если данные битые — остаются значения по умолчанию */
    }
    return merged;
  }

  /* Сохраняем конфиг в localStorage */
  function save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
    } catch (err) {
      /* localStorage может быть недоступен, молча пропускаем */
    }
  }

  /* Резолвим фактическую тему с учётом настройки 'system' */
  function resolveTheme() {
    if (config.theme === 'system') {
      var darkQuery = window.matchMedia('(prefers-color-scheme: dark)');
      return darkQuery.matches ? 'dark' : 'light';
    }
    return config.theme;
  }

  /* Применяем настройки к корневому элементу <html> */
  function apply() {
    var root = document.documentElement;
    root.setAttribute('data-theme', resolveTheme());
    root.setAttribute('data-font-size', config.fontSize);
    root.style.setProperty('--accent', config.accent);
  }

  /* Следим за системной темой, если выбран режим 'system' */
  var systemMedia = window.matchMedia('(prefers-color-scheme: dark)');
  if (systemMedia.addEventListener) {
    systemMedia.addEventListener('change', function () {
      if (config.theme === 'system') {
        apply();
      }
    });
  } else if (systemMedia.addListener) {
    /* поддержка старых браузеров */
    systemMedia.addListener(function () {
      if (config.theme === 'system') {
        apply();
      }
    });
  }

  /* Публичный API для остальных скриптов (main.js и страницы настроек) */
  window.PF = {
    get: function () {
      return config;
    },
    set: function (patch) {
      config = Object.assign({}, config, patch);
      save();
      apply();
      /* событие для переподстановки «обращения» и UI настроек */
      document.dispatchEvent(new CustomEvent('pf:settings', { detail: config }));
    },
    reset: function () {
      config = Object.assign({}, DEFAULTS);
      save();
      apply();
      document.dispatchEvent(new CustomEvent('pf:settings', { detail: config }));
    },
    resolveTheme: resolveTheme
  };

  /* Применяем настройки сразу, ещё до первой отрисовки */
  apply();
})();
