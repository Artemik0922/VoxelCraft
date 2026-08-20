/**
 * main.js
 * Общие взаимодействия сайта-портфолио:
 *  - единые шапка и подвал на всех страницах;
 *  - бургер-меню на мобильных и подсветка активного пункта;
 *  - данные проектов, фильтр портфолио и рендер страницы кейса;
 *  - появление секций при скролле (IntersectionObserver);
 *  - валидация формы контактов и плавный скролл по якорям;
 *  - подстановка профиля «о себе» из API (api.js) в шапку, герой,
 *    биографию и контакты, а также форма редактирования профиля.
 *
 * Скрипт подключается в конце <body>. Внешний вид настраивает settings.js
 * (в <head>), контент подгружает api.js.
 */
(function () {
  'use strict';

  /* Уважаем настройку операционной системы об ограничении анимаций */
  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* Названия категорий для фильтра портфолио */
  var CATEGORY_LABELS = {
    all: 'Все',
    web: 'Веб',
    design: 'Дизайн',
    other: 'Другое'
  };

  /* SVG-иконки соцсетей и почты (stroke=currentColor для подцветки акцентом) */
  var SOCIAL_ICONS = {
    telegram: {
      label: 'Telegram',
      svg: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true"><path d="M21.9 4.6c.3-1.1-.8-2-1.9-1.6L2.9 9.6c-1.2.4-1.1 2 .1 2.3l4.6 1.2 1.7 5.4c.3 1 1.6 1.3 2.4.5l2.5-2.5 4.6 3.4c.9.7 2.2.2 2.4-1l2.7-14.3zM8.5 12.5l8.7-5.6c.4-.2.8.3.4.6l-6.6 6.2c-.3.3-.5.7-.6 1.1l-.4 2-1.2-3.9c-.1-.2-.2-.4-.3-.4z"/></svg>'
    },
    github: {
      label: 'GitHub',
      svg: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true"><path d="M12 2a10 10 0 0 0-3.2 19.5c.5.1.7-.2.7-.5v-1.8c-2.7.6-3.3-1.2-3.3-1.2-.4-1.1-1.1-1.4-1.1-1.4-.9-.6.1-.6.1-.6 1 .1 1.5 1 1.5 1 .9 1.6 2.4 1.1 3 .9.1-.7.4-1.1.6-1.4-2.2-.3-4.6-1.1-4.6-5 0-1.1.4-2 1-2.7-.1-.3-.4-1.3.1-2.6 0 0 .8-.3 2.7 1a9.3 9.3 0 0 1 5 0c1.9-1.3 2.7-1 2.7-1 .5 1.3.2 2.3.1 2.6.6.7 1 1.6 1 2.7 0 3.9-2.4 4.7-4.6 5 .4.3.7.9.7 1.9v2.8c0 .3.2.6.7.5A10 10 0 0 0 12 2z"/></svg>'
    },
    behance: {
      label: 'Behance',
      svg: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true"><path d="M9.4 8.7c.5-.5 1.2-.7 2.1-.7.9 0 1.6.2 2.2.5l.4 1.4c-.8-.5-1.6-.7-2.5-.7-.6 0-1.1.1-1.4.3-.3.2-.4.5-.4.8 0 .4.1.6.4.8.3.2.9.4 1.6.6.8.3 1.5.6 1.9.9.5.4.8.9.8 1.6 0 .8-.3 1.4-.9 1.9-.6.5-1.4.7-2.4.7-.9 0-1.8-.2-2.6-.6l-.4-1.5c.8.6 1.8.9 2.9.9.6 0 1-.1 1.3-.3.3-.2.4-.5.4-.9 0-.4-.2-.7-.5-.9-.3-.2-.9-.4-1.6-.7-.7-.3-1.3-.6-1.7-.9-.4-.3-.7-.8-.7-1.4 0-.6.2-1.1.7-1.5zM7.2 12H2.8v-1.7h4.4V12zm10.3 2.3c.4.4.8.5 1.5.5.8 0 1.4-.2 1.7-.5l.7 1.4c-.8.7-1.7 1-2.6 1-1.2 0-2.2-.4-2.8-1.1-.6-.7-.9-1.6-.9-2.6 0-1 .3-1.9.9-2.6.6-.7 1.5-1 2.7-1 1.1 0 2 .4 2.7 1.1.6.7.9 1.6.9 2.6 0 .4 0 .7-.1.9h-6.3c0 .7.2 1.2.6 1.3zm2.3-3.2c-.3-.3-.8-.5-1.3-.5-.6 0-1 .2-1.3.5-.3.3-.5.7-.6 1.2h3.9c0-.5-.2-1-.7-1.2zM2.8 10.3h.9v5.1H2.4V10.9c-.2-.4-.5-.5-.8-.5v-.2c.6-.1 1.1-.1 1.2.1z"/></svg>'
    },
    email: {
      label: 'Email',
      svg: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m4 7 8 6 8-6"/></svg>'
    }
  };

  /* Текущий профиль: по умолчанию — фолбэк из api.js */
  var profile = Object.assign({}, window.DEFAULT_PROFILE || {});

  /* Экранирование HTML для безопасной вставки текста из БД */
  function esc(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /* ---------- Данные проектов (реалистичные заглушки) ---------- */

  var PROJECTS = [
    {
      id: 'proj-01',
      category: 'web',
      year: '2025',
      title: 'Сайт арт-галереи «Полотно»',
      short: 'Виртуальные туры по залам, каталог выставок и медиатека.',
      cover: 'https://picsum.photos/seed/gallery-cover/1200/900',
      gallery: [
        'https://picsum.photos/seed/gallery-1/1200/900',
        'https://picsum.photos/seed/gallery-2/900/1200',
        'https://picsum.photos/seed/gallery-3/1200/800',
        'https://picsum.photos/seed/gallery-4/1200/900'
      ],
      task: 'Спроектировать и разработать сайт галереи современного искусства: каталог выставок, виртуальные туры по залам, медиатека и запись на экскурсии.',
      role: 'Фронтенд-разработчик',
      stack: ['HTML', 'CSS', 'JavaScript', 'Vite'],
      result: 'Сайт выдержал пиковые нагрузки в дни открытий выставок, загрузка страниц — менее 1,2 с, посещаемость выросла на 40%.'
    },
    {
      id: 'proj-02',
      category: 'design',
      year: '2024',
      title: 'Брендинг кофейни «Зерно»',
      short: 'Логотип, фирменный стиль, упаковка и лендинг для доставки.',
      cover: 'https://picsum.photos/seed/coffee-brand/1200/900',
      gallery: [
        'https://picsum.photos/seed/coffee-1/1200/900',
        'https://picsum.photos/seed/coffee-2/900/1200',
        'https://picsum.photos/seed/coffee-3/1200/800',
        'https://picsum.photos/seed/coffee-4/1200/900'
      ],
      task: 'Создать айдентику кофейни с нуля: логотип, фирменный стиль, упаковка, меню и посадочная страница для доставки.',
      role: 'Дизайнер, арт-директор',
      stack: ['Figma', 'Illustrator', 'HTML', 'CSS'],
      result: 'Стиль внедрён во всех точках касания: упаковка, меню, соцсети. Продажи через лендинг выросли на 25%.'
    },
    {
      id: 'proj-03',
      category: 'web',
      year: '2025',
      title: 'Сервис аналитики чтения «Ось»',
      short: 'Дашборды читательских привычек для издательств.',
      cover: 'https://picsum.photos/seed/analytics-app/1200/900',
      gallery: [
        'https://picsum.photos/seed/analytics-1/1200/900',
        'https://picsum.photos/seed/analytics-2/900/1200',
        'https://picsum.photos/seed/analytics-3/1200/800',
        'https://picsum.photos/seed/analytics-4/1200/900'
      ],
      task: 'Разработать интерфейс дашбордов: графики вовлечённости, отчёты для издательств, настройка и экспорт данных.',
      role: 'Фронтенд-разработчик',
      stack: ['HTML', 'CSS', 'JavaScript', 'Chart.js'],
      result: 'Время на подготовку отчёта сократилось с 2 часов до 4 минут, сервис используют 12 издательств.'
    },
    {
      id: 'proj-04',
      category: 'design',
      year: '2024',
      title: 'Интерфейс мобильного банка',
      short: 'Дизайн-система, ключевые экраны, тёмная тема и доступность.',
      cover: 'https://picsum.photos/seed/bank-app/1200/900',
      gallery: [
        'https://picsum.photos/seed/bank-1/1200/900',
        'https://picsum.photos/seed/bank-2/900/1200',
        'https://picsum.photos/seed/bank-3/1200/800',
        'https://picsum.photos/seed/bank-4/1200/900'
      ],
      task: 'Переработать дизайн мобильного приложения банка: дизайн-система, ключевые экраны, тёмная тема и доступность по WCAG AA.',
      role: 'UI/UX-дизайнер',
      stack: ['Figma', 'Design tokens', 'Прототипирование'],
      result: 'Оценка в сторах выросла с 3,8 до 4,6, число жалоб на интерфейс снизилось на треть.'
    },
    {
      id: 'proj-05',
      category: 'web',
      year: '2023',
      title: 'Каталог винтажной электроники',
      short: 'Магазин-витрина с фильтрами, поиском и корзиной.',
      cover: 'https://picsum.photos/seed/retro-store/1200/900',
      gallery: [
        'https://picsum.photos/seed/retro-1/1200/900',
        'https://picsum.photos/seed/retro-2/900/1200',
        'https://picsum.photos/seed/retro-3/1200/800',
        'https://picsum.photos/seed/retro-4/1200/900'
      ],
      task: 'Сверстать магазин винтажной электроники: каталог с фильтрами по годам и типу, поиск, корзина и быстрая выдача.',
      role: 'Фронтенд-разработчик',
      stack: ['HTML', 'CSS', 'JavaScript'],
      result: 'Конверсия в заказ выросла на 18%, среднее время на сайте — 4 минуты.'
    },
    {
      id: 'proj-06',
      category: 'other',
      year: '2025',
      title: 'Генератор постеров',
      short: 'Эксперимент с генеративной типографикой и canvas.',
      cover: 'https://picsum.photos/seed/poster-gen/1200/900',
      gallery: [
        'https://picsum.photos/seed/poster-1/1200/900',
        'https://picsum.photos/seed/poster-2/900/1200',
        'https://picsum.photos/seed/poster-3/1200/800',
        'https://picsum.photos/seed/poster-4/1200/900'
      ],
      task: 'Сделать экспериментальный генератор постеров: шум, сетки и шрифтовые композиции, рендер на canvas.',
      role: 'Креативный разработчик',
      stack: ['HTML', 'CSS', 'JavaScript', 'Canvas API'],
      result: 'Проект стал участником онлайн-фестиваля генеративного искусства.'
    }
  ];

  /* ---------- Шаблоны шапки и подвала (имя/роль подставляются из профиля) ---------- */

  var HEADER_HTML = `
    <header class="header" role="banner">
      <div class="container header__inner">
        <a class="logo" href="index.html" data-profile="name">Иван Петров</a>
        <nav class="nav" id="nav" aria-label="Основная навигация">
          <a class="nav__link" href="index.html" data-nav="index">Главная</a>
          <a class="nav__link" href="about.html" data-nav="about">О себе</a>
          <a class="nav__link" href="portfolio.html" data-nav="portfolio">Портфолио</a>
          <a class="nav__link" href="contacts.html" data-nav="contacts">Контакты</a>
          <a class="nav__link nav__link--settings" href="settings.html" data-nav="settings">Настройки</a>
        </nav>
        <button class="burger" id="burger" type="button" aria-expanded="false" aria-controls="nav" aria-label="Открыть меню">
          <span class="burger__bar"></span>
          <span class="burger__bar"></span>
          <span class="burger__bar"></span>
        </button>
      </div>
    </header>`;

  var FOOTER_HTML = `
    <footer class="footer" role="contentinfo">
      <div class="container footer__inner">
        <div class="footer__col">
          <p class="footer__name" data-profile="name">Иван Петров</p>
          <p class="footer__note" data-profile="role">фронтенд-разработчик</p>
        </div>
        <nav class="footer__nav" aria-label="Навигация в подвале">
          <a href="index.html">Главная</a>
          <a href="about.html">О себе</a>
          <a href="portfolio.html">Портфолио</a>
          <a href="contacts.html">Контакты</a>
          <a href="settings.html">Настройки</a>
        </nav>
        <div class="footer__socials">
          <a href="https://t.me/" target="_blank" rel="noopener">Telegram</a>
          <a href="https://github.com/" target="_blank" rel="noopener">GitHub</a>
          <a href="https://www.behance.net/" target="_blank" rel="noopener">Behance</a>
        </div>
        <p class="footer__copy">&copy; ${new Date().getFullYear()} <span data-profile="name">Иван Петров</span></p>
      </div>
    </footer>`;

  /* Вставляем шапку и подвал в контейнеры на странице */
  function injectShell() {
    var headerBox = document.getElementById('site-header');
    var footerBox = document.getElementById('site-footer');
    if (headerBox) headerBox.innerHTML = HEADER_HTML;
    if (footerBox) footerBox.innerHTML = FOOTER_HTML;
  }

  /* Подсветка активного пункта навигации по текущей странице */
  function setActiveNav() {
    var file = location.pathname.split('/').pop() || '';
    var page = file.replace(/\.html$/, '') || 'index';
    document.querySelectorAll('.nav__link[data-nav]').forEach(function (link) {
      if (link.getAttribute('data-nav') === page) {
        link.classList.add('is-active');
        link.setAttribute('aria-current', 'page');
      }
    });
  }

  /* ---------- Подстановка профиля «о себе» в элементы страницы ---------- */

  function renderIdentity() {
    /* Простые текстовые поля: [data-profile="ключ"] */
    document.querySelectorAll('[data-profile]').forEach(function (el) {
      var key = el.getAttribute('data-profile');
      if (profile[key] != null) el.textContent = profile[key];
    });

    /* E-mail-ссылка: ставим текст и href="mailto:..." */
    document.querySelectorAll('[data-email]').forEach(function (el) {
      el.textContent = profile.email || '';
      el.setAttribute('href', 'mailto:' + (profile.email || ''));
    });

    /* Соцсети: ставим адрес из профиля */
    document.querySelectorAll('[data-social]').forEach(function (el) {
      var key = el.getAttribute('data-social');
      if (profile[key]) el.setAttribute('href', profile[key]);
    });

    /* Соцсети с иконками: [data-socials] — контейнер со ссылками */
    document.querySelectorAll('[data-socials]').forEach(function (el) {
      var keys = ['telegram', 'github', 'behance'];
      el.innerHTML = keys
        .filter(function (key) { return profile[key]; })
        .map(function (key) {
          var item = SOCIAL_ICONS[key];
          return '<a class="social-icon" href="' + esc(profile[key]) +
            '" target="_blank" rel="noopener" aria-label="' + item.label + '">' +
            item.svg + '<span>' + item.label + '</span></a>';
        }).join('');
    });

    /* Адрес: [data-address] — текст + подпись */
    document.querySelectorAll('[data-address]').forEach(function (el) {
      el.textContent = profile.address || '';
    });

    /* Фотография профиля: [data-photo] — ставим src изображения */
    document.querySelectorAll('[data-photo]').forEach(function (img) {
      if (profile.photo) {
        img.setAttribute('src', profile.photo);
        img.setAttribute('alt', 'Портрет ' + (profile.name || ''));
      }
    });

    /* Биография: каждый абзац отдельным <p> */
    document.querySelectorAll('[data-about-bio]').forEach(function (el) {
      var paragraphs = (profile.bio || [])
        .map(function (p) { return '<p>' + esc(p) + '</p>'; })
        .join('');
      el.innerHTML = paragraphs;
    });

    /* Список профессиональных навыков */
    document.querySelectorAll('[data-about-skills]').forEach(function (el) {
      var items = (profile.skills || []).map(function (s) {
        var level = Math.max(0, Math.min(100, Number(s.level) || 0));
        return '<div class="skill-item">' +
          '<div class="skill-item__head"><span>' + esc(s.name) + '</span>' +
          '<span class="skill-item__value">' + level + '%</span></div>' +
          '<div class="skill-item__bar"><div class="skill-item__fill" style="width:' + level + '%"></div></div>' +
          '</div>';
      }).join('');
      el.innerHTML = items;
    });

    /* Отзывы клиентов: [data-testimonials] */
    document.querySelectorAll('[data-testimonials]').forEach(function (el) {
      var items = (profile.testimonials || []).map(function (t) {
        return '<article class="testimonial-card reveal">' +
          '<div class="testimonial-card__quote">&ldquo;</div>' +
          '<p class="testimonial-card__text">' + esc(t.text) + '</p>' +
          '<footer class="testimonial-card__meta">' +
          '<span class="testimonial-card__name">' + esc(t.name) + '</span>' +
          '<span class="testimonial-card__role">' + esc(t.role) + '</span>' +
          '</footer></article>';
      }).join('');
      el.innerHTML = items;
    });
  }

  /* Подставляем «как к вам обращаться» в элементы на странице */
  function fillAddress() {
    var name = window.PF ? window.PF.get().addressName : 'Иван';
    document.querySelectorAll('[data-address-name]').forEach(function (el) {
      el.textContent = name || 'Иван';
    });
  }

  /* ---------- Бургер-меню ---------- */

  function initBurger() {
    var burger = document.getElementById('burger');
    var nav = document.getElementById('nav');
    if (!burger || !nav) return;

    burger.addEventListener('click', function () {
      var open = document.body.classList.toggle('menu-open');
      burger.setAttribute('aria-expanded', open ? 'true' : 'false');
      burger.setAttribute('aria-label', open ? 'Закрыть меню' : 'Открыть меню');
    });

    /* закрываем меню после клика по пункту */
    nav.addEventListener('click', function (e) {
      if (e.target.closest('.nav__link')) {
        document.body.classList.remove('menu-open');
        burger.setAttribute('aria-expanded', 'false');
        burger.setAttribute('aria-label', 'Открыть меню');
      }
    });
  }

  /* ---------- Появление секций при скролле ---------- */

  var revealObserver = null;

  /* Наблюдатель за появлением: добавляет is-visible элементам в зоне видимости */
  function initReveal() {
    if (reduceMotion || !('IntersectionObserver' in window)) {
      document.querySelectorAll('.reveal').forEach(function (el) { el.classList.add('is-visible'); });
      return;
    }

    revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          revealObserver.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });

    observeReveals();
  }

  /* Подписывает новые .reveal-элементы (нужно после перерисовки из API) */
  function observeReveals() {
    var items = document.querySelectorAll('.reveal:not(.is-visible)');
    if (reduceMotion || !('IntersectionObserver' in window) || !revealObserver) {
      items.forEach(function (el) { el.classList.add('is-visible'); });
      return;
    }
    items.forEach(function (el) { revealObserver.observe(el); });
  }

  /* ---------- Плавный скролл по внутренним якорям ---------- */

  function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(function (link) {
      link.addEventListener('click', function (e) {
        var hash = link.getAttribute('href');
        if (!hash || hash.length < 2) return;
        var target = document.querySelector(hash);
        if (!target) return;
        e.preventDefault();
        target.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
      });
    });
  }

  /* ---------- Карточка проекта (общая для портфолио и «избранного») ---------- */

  function workCard(project, extraClass) {
    var category = CATEGORY_LABELS[project.category] || 'Другое';
    var num = project.id.replace('proj-', '');
    return `
      <a class="work-card ${extraClass || ''} reveal" href="case.html#${project.id}" data-category="${esc(project.category)}">
        <div class="work-card__media">
          <img src="${esc(project.cover)}" alt="${esc(project.title)} — обложка проекта" loading="lazy" width="1200" height="900">
          <span class="work-card__num">${num}</span>
        </div>
        <div class="work-card__body">
          <p class="work-card__meta">${category} &middot; ${esc(project.year)}</p>
          <h3 class="work-card__title">${esc(project.title)}</h3>
          <p class="work-card__short">${esc(project.short)}</p>
          <span class="work-card__more" aria-hidden="true">Смотреть кейс &rarr;</span>
        </div>
      </a>`;
  }

  /* ---------- Портфолио: фильтр и сетка ---------- */

  var portfolioFilter = 'all';

  function initPortfolio() {
    var grid = document.getElementById('works-grid');
    var filterBox = document.getElementById('works-filter');
    if (!grid || !filterBox) return;

    function renderFilter() {
      filterBox.innerHTML = Object.keys(CATEGORY_LABELS).map(function (key) {
        var active = key === portfolioFilter;
        return '<button type="button" class="filter__btn' + (active ? ' is-active' : '') +
          '" data-filter="' + key + '" aria-pressed="' + active + '">' + CATEGORY_LABELS[key] + '</button>';
      }).join('');

      filterBox.querySelectorAll('.filter__btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
          portfolioFilter = btn.getAttribute('data-filter');
          renderFilter();
          filterWorks();
        });
      });
    }

    function filterWorks() {
      grid.querySelectorAll('.work-card').forEach(function (card) {
        var show = portfolioFilter === 'all' || card.getAttribute('data-category') === portfolioFilter;
        card.classList.toggle('is-hidden', !show);
      });
    }

    function renderWorks() {
      grid.innerHTML = getProjects().map(function (p) { return workCard(p); }).join('');
      filterWorks();
    }

    renderFilter();
    renderWorks();
  }

  /* ---------- Избранные работы на главной ---------- */

  /* Список проектов: из профиля (БД) или статический фолбэк */
  function getProjects() {
    var list = profile.projects && profile.projects.length ? profile.projects : PROJECTS;
    return list.map(function (p, i) {
      var id = p.id || 'proj-' + String(i + 1).padStart(2, '0');
      return Object.assign({}, p, { id: id });
    });
  }

  function renderFeatured() {
    var box = document.getElementById('featured-grid');
    if (!box) return;
    var list = getProjects();
    /* берём проекты с чётными индексами: 1-й, 3-й, 5-й */
    var picks = [0, 2, 4].map(function (i) { return list[i]; }).filter(Boolean);
    box.innerHTML = picks.map(function (project) {
      return workCard(project, 'featured-card');
    }).join('');
  }

  function initFeatured() {
    renderFeatured();
  }

  /* ---------- Страница кейса (данные берутся из хэша URL) ---------- */

  function renderCase() {
    var caseBox = document.getElementById('case');
    if (!caseBox) return;
    var hash = (location.hash || '').replace('#', '');
    var projects = getProjects();
    var index = projects.findIndex(function (p) { return p.id === hash; });
    var project = projects[index >= 0 ? index : 0];
    var category = CATEGORY_LABELS[project.category] || 'Другое';
    var num = project.id.replace('proj-', '');
    var nextIndex = (index >= 0 ? index + 1 : 1) % projects.length;
    var next = projects[nextIndex];

    var gallery = project.gallery.map(function (src, i) {
      return '<figure class="case__gallery-item">' +
        '<img src="' + esc(src) + '" alt="' + esc(project.title) + ' — кадр ' + (i + 1) + '" loading="lazy">' +
        '</figure>';
    }).join('');

    caseBox.innerHTML = `
      <div class="case">
        <a class="case__back" href="portfolio.html">&larr; Назад к портфолио</a>
        <header class="case__head reveal is-visible">
          <p class="case__num">Кейс &numero; ${num}</p>
          <h1 class="case__title">${esc(project.title)}</h1>
          <p class="case__tags">${category} &middot; ${esc(project.year)}</p>
        </header>
        <figure class="case__cover reveal is-visible">
          <img src="${esc(project.cover)}" alt="${esc(project.title)} — главный экран">
        </figure>
        <div class="case__meta">
          <div class="case__meta-item reveal is-visible">
            <h3>Задача</h3>
            <p>${esc(project.task)}</p>
          </div>
          <div class="case__meta-item reveal is-visible">
            <h3>Роль</h3>
            <p>${esc(project.role)}</p>
          </div>
          <div class="case__meta-item reveal is-visible">
            <h3>Стек</h3>
            <ul class="case__stack">${project.stack.map(function (s) { return '<li>' + esc(s) + '</li>'; }).join('')}</ul>
          </div>
          <div class="case__meta-item reveal is-visible">
            <h3>Результат</h3>
            <p>${esc(project.result)}</p>
          </div>
        </div>
        <section class="case__gallery">
          ${gallery}
        </section>
        <div class="case__next">
          <a class="btn btn--solid" href="#${next.id}">Следующий проект &rarr;</a>
        </div>
      </div>`;

    window.scrollTo(0, 0);
  }

  function initCase() {
    var caseBox = document.getElementById('case');
    if (!caseBox) return;
    renderCase();
    window.addEventListener('hashchange', renderCase);
  }

  /* Перерисовка всех проектных блоков после загрузки профиля из БД */
  function refreshProjectViews() {
    renderFeatured();
    var grid = document.getElementById('works-grid');
    if (grid) {
      grid.innerHTML = getProjects().map(function (p) { return workCard(p); }).join('');
      /* повторно применяем активный фильтр портфолио */
      grid.querySelectorAll('.work-card').forEach(function (card) {
        var show = portfolioFilter === 'all' || card.getAttribute('data-category') === portfolioFilter;
        card.classList.toggle('is-hidden', !show);
      });
    }
    renderCase();
  }

  /* ---------- Страница настроек: внешний вид + профиль «о себе» ---------- */

  function initSettingsPage() {
    var form = document.getElementById('settings-form');
    if (!form || !window.PF) return;

    var resetBtn = document.getElementById('settings-reset');

    /* Синхронизируем состояние кнопок внешнего вида с текущим конфигом */
    function sync() {
      var config = window.PF.get();
      form.querySelectorAll('[data-group="theme"] [data-value]').forEach(function (btn) {
        btn.classList.toggle('is-active', btn.getAttribute('data-value') === config.theme);
        btn.setAttribute('aria-pressed', btn.getAttribute('data-value') === config.theme ? 'true' : 'false');
      });
      form.querySelectorAll('[data-group="accent"] [data-value]').forEach(function (btn) {
        btn.classList.toggle('is-active', btn.getAttribute('data-value') === config.accent);
      });
      form.querySelectorAll('[data-group="fontSize"] [data-value]').forEach(function (btn) {
        btn.classList.toggle('is-active', btn.getAttribute('data-value') === config.fontSize);
        btn.setAttribute('aria-pressed', btn.getAttribute('data-value') === config.fontSize ? 'true' : 'false');
      });
      var nameInput = document.getElementById('address-name');
      if (nameInput) nameInput.value = config.addressName;
    }

    /* Клик по кнопкам групп внешнего вида: применяем настройку мгновенно */
    form.addEventListener('click', function (e) {
      var btn = e.target.closest('[data-group] [data-value]');
      if (!btn) return;
      var group = btn.closest('[data-group]').getAttribute('data-group');
      var patch = {};
      patch[group] = btn.getAttribute('data-value');
      window.PF.set(patch);
      form.querySelectorAll('[data-group="' + group + '"] [data-value]').forEach(function (b) {
        var active = b === btn;
        b.classList.toggle('is-active', active);
        if (b.tagName === 'BUTTON') {
          b.setAttribute('aria-pressed', active ? 'true' : 'false');
        }
      });
    });

    /* Ввод обращения: сохраняем по мере ввода */
    var nameInput = document.getElementById('address-name');
    if (nameInput) {
      nameInput.addEventListener('input', function () {
        window.PF.set({ addressName: nameInput.value });
      });
    }

    /* Пароль администратора: хранится в браузере, уходит в заголовке X-Admin-Token */
    var tokenInput = document.getElementById('admin-token');
    if (tokenInput && window.API) {
      tokenInput.value = window.API.getToken() || '';
      tokenInput.addEventListener('input', function () {
        window.API.setToken(tokenInput.value.trim());
      });
    }

    /* Сброс внешнего вида к значениям по умолчанию */
    if (resetBtn) {
      resetBtn.addEventListener('click', function () {
        window.PF.reset();
        sync();
      });
    }

    /* Пересинхронизация при изменении настроек извне */
    document.addEventListener('pf:settings', sync);

    sync();
    initProfileForm();
  }

  /* ---------- Форма редактирования профиля «о себе» ---------- */

  var profileStatusTimer = null;

  function initProfileForm() {
    var form = document.getElementById('settings-form');
    var saveBtn = document.getElementById('profile-save');
    if (!form || !saveBtn) return;

    saveBtn.addEventListener('click', function () {
      var payload = {
        name: inputValue('pf-name'),
        role: inputValue('pf-role'),
        photo: profile.photo || '',
        bio: bioToArray(inputValue('pf-bio')),
        hero_text: inputValue('pf-hero'),
        email: inputValue('pf-email'),
        location: inputValue('pf-location'),
        address: inputValue('pf-address'),
        timezone: inputValue('pf-timezone'),
        telegram: inputValue('pf-telegram'),
        github: inputValue('pf-github'),
        behance: inputValue('pf-behance'),
        skills: collectSkills(),
        testimonials: collectTestimonials(),
        projects: collectProjects()
      };

      API.saveProfile(payload)
        .then(function (data) {
          profile = Object.assign({}, profile, data);
          renderIdentity();
          refreshProjectViews();
          showProfileStatus('Сохранено', true);
        })
        .catch(function (err) {
          if (err && (err.status === 401 || err.status === 403)) {
            showProfileStatus('Неверный пароль администратора', false);
          } else {
            showProfileStatus('Ошибка сохранения — сервер недоступен', false);
          }
        });
    });

    /* Кнопка «добавить навык» */
    var addBtn = document.getElementById('skills-add');
    if (addBtn) {
      addBtn.addEventListener('click', function () {
        var box = document.getElementById('skills-editor');
        if (box) box.appendChild(skillRow('', 60));
      });
    }

    /* Кнопка «добавить отзыв» */
    var addTestimonialBtn = document.getElementById('testimonials-add');
    if (addTestimonialBtn) {
      addTestimonialBtn.addEventListener('click', function () {
        var box = document.getElementById('testimonials-editor');
        if (box) box.appendChild(testimonialRow('', '', ''));
      });
    }

    /* Кнопка «добавить проект» */
    var addProjectBtn = document.getElementById('projects-add');
    if (addProjectBtn) {
      addProjectBtn.addEventListener('click', function () {
        var box = document.getElementById('projects-editor');
        if (box) box.appendChild(projectRow({}));
      });
    }

    /* Загрузка фотографии профиля */
    var photoBtn = document.getElementById('photo-upload-btn');
    var photoInput = document.getElementById('pf-photo');
    if (photoBtn && photoInput && window.API) {
      photoBtn.addEventListener('click', function () {
        var file = photoInput.files && photoInput.files[0];
        if (!file) {
          showProfileStatus('Сначала выберите файл', false);
          return;
        }
        if (!/^image\//.test(file.type)) {
          showProfileStatus('Файл должен быть изображением', false);
          return;
        }
        var formData = new FormData();
        formData.append('file', file);

        var headers = {};
        var token = window.API.getToken();
        if (token) headers['X-Admin-Token'] = token;

        fetch(window.API.BASE + '/photo', { method: 'POST', body: formData, headers: headers })
          .then(function (res) {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
          })
          .then(function (data) {
            profile.photo = data.photo;
            renderIdentity();
            refreshPhotoPreview();
            showProfileStatus('Фотография сохранена', true);
          })
          .catch(function () {
            showProfileStatus('Ошибка загрузки фото', false);
          });
      });
    }
  }

  /* Создаёт строку редактора навыка */
  function skillRow(name, level) {
    var row = document.createElement('div');
    row.className = 'skill-edit-row';

    var nameInput = document.createElement('input');
    nameInput.type = 'text';
    nameInput.className = 'sk-name';
    nameInput.placeholder = 'Название навыка';
    nameInput.value = name || '';
    nameInput.setAttribute('aria-label', 'Название навыка');

    var levelInput = document.createElement('input');
    levelInput.type = 'range';
    levelInput.className = 'sk-level';
    levelInput.min = 0;
    levelInput.max = 100;
    levelInput.value = level == null ? 0 : level;
    levelInput.setAttribute('aria-label', 'Уровень навыка');

    var value = document.createElement('output');
    value.className = 'sk-value';
    value.textContent = levelInput.value + '%';

    levelInput.addEventListener('input', function () {
      value.textContent = levelInput.value + '%';
    });

    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'sk-remove';
    removeBtn.textContent = '\u00d7';
    removeBtn.setAttribute('aria-label', 'Удалить навык');
    removeBtn.addEventListener('click', function () {
      row.remove();
    });

    row.appendChild(nameInput);
    row.appendChild(levelInput);
    row.appendChild(value);
    row.appendChild(removeBtn);
    return row;
  }

  /* Собирает список навыков из строк редактора */
  function collectSkills() {
    var box = document.getElementById('skills-editor');
    if (!box) return [];
    var skills = [];
    box.querySelectorAll('.skill-edit-row').forEach(function (row) {
      var name = (row.querySelector('.sk-name').value || '').trim();
      var level = parseInt(row.querySelector('.sk-level').value, 10);
      if (name) skills.push({ name: name, level: isNaN(level) ? 0 : level });
    });
    return skills;
  }

  /* Предзаполняем редактор навыков */
  function prefillSkills() {
    var box = document.getElementById('skills-editor');
    if (!box) return;
    var skills = profile.skills || [];
    box.innerHTML = '';
    skills.forEach(function (s) { box.appendChild(skillRow(s.name, s.level)); });
    if (skills.length === 0) box.appendChild(skillRow('', 0));
  }

  /* Создаёт строку редактора отзыва клиента */
  function testimonialRow(name, role, text) {
    var row = document.createElement('div');
    row.className = 'testimonial-edit-row';

    var nameInput = document.createElement('input');
    nameInput.type = 'text';
    nameInput.className = 'tm-name';
    nameInput.placeholder = 'Имя клиента';
    nameInput.value = name || '';
    nameInput.setAttribute('aria-label', 'Имя клиента');

    var roleInput = document.createElement('input');
    roleInput.type = 'text';
    roleInput.className = 'tm-role';
    roleInput.placeholder = 'Должность / компания';
    roleInput.value = role || '';
    roleInput.setAttribute('aria-label', 'Должность или компания');

    var textInput = document.createElement('textarea');
    textInput.className = 'tm-text';
    textInput.placeholder = 'Текст отзыва';
    textInput.value = text || '';
    textInput.setAttribute('aria-label', 'Текст отзыва');

    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'sk-remove';
    removeBtn.textContent = '\u00d7';
    removeBtn.setAttribute('aria-label', 'Удалить отзыв');
    removeBtn.addEventListener('click', function () {
      row.remove();
    });

    row.appendChild(nameInput);
    row.appendChild(roleInput);
    row.appendChild(textInput);
    row.appendChild(removeBtn);
    return row;
  }

  /* Собирает список отзывов из строк редактора */
  function collectTestimonials() {
    var box = document.getElementById('testimonials-editor');
    if (!box) return [];
    var testimonials = [];
    box.querySelectorAll('.testimonial-edit-row').forEach(function (row) {
      var name = (row.querySelector('.tm-name').value || '').trim();
      var role = (row.querySelector('.tm-role').value || '').trim();
      var text = (row.querySelector('.tm-text').value || '').trim();
      if (name && text) testimonials.push({ name: name, role: role, text: text });
    });
    return testimonials;
  }

  /* Предзаполняем редактор отзывов */
  function prefillTestimonials() {
    var box = document.getElementById('testimonials-editor');
    if (!box) return;
    var testimonials = profile.testimonials || [];
    box.innerHTML = '';
    testimonials.forEach(function (t) {
      box.appendChild(testimonialRow(t.name, t.role, t.text));
    });
    if (testimonials.length === 0) box.appendChild(testimonialRow('', '', ''));
  }

  /* Создаёт строку редактора проекта */
  function projectRow(project) {
    project = project || {};
    var row = document.createElement('div');
    row.className = 'project-edit-row';

    var title = document.createElement('input');
    title.type = 'text';
    title.className = 'pe-title';
    title.placeholder = 'Название проекта';
    title.value = project.title || '';
    title.setAttribute('aria-label', 'Название проекта');

    var category = document.createElement('select');
    category.className = 'pe-category';
    category.setAttribute('aria-label', 'Категория');
    [['web', 'Веб'], ['design', 'Дизайн'], ['other', 'Другое']].forEach(function (c) {
      var opt = document.createElement('option');
      opt.value = c[0];
      opt.textContent = c[1];
      opt.selected = project.category === c[0];
      category.appendChild(opt);
    });

    var year = document.createElement('input');
    year.type = 'text';
    year.className = 'pe-year';
    year.placeholder = 'Год';
    year.value = project.year || '';
    year.setAttribute('aria-label', 'Год');

    var short = document.createElement('input');
    short.type = 'text';
    short.className = 'pe-short';
    short.placeholder = 'Краткое описание';
    short.value = project.short || '';
    short.setAttribute('aria-label', 'Краткое описание');

    var cover = document.createElement('input');
    cover.type = 'text';
    cover.className = 'pe-cover';
    cover.placeholder = 'URL обложки (ссылка на изображение)';
    cover.value = project.cover || '';
    cover.setAttribute('aria-label', 'URL обложки');

    var gallery = document.createElement('textarea');
    gallery.className = 'pe-gallery';
    gallery.placeholder = 'Галерея: по одной ссылке на строку';
    gallery.value = (project.gallery || []).join('\n');
    gallery.setAttribute('aria-label', 'Галерея');

    var task = document.createElement('textarea');
    task.className = 'pe-task';
    task.placeholder = 'Задача';
    task.value = project.task || '';
    task.setAttribute('aria-label', 'Задача');

    var role = document.createElement('input');
    role.type = 'text';
    role.className = 'pe-role';
    role.placeholder = 'Роль';
    role.value = project.role || '';
    role.setAttribute('aria-label', 'Роль');

    var stack = document.createElement('textarea');
    stack.className = 'pe-stack';
    stack.placeholder = 'Стек: по одному на строку';
    stack.value = (project.stack || []).join('\n');
    stack.setAttribute('aria-label', 'Стек');

    var result = document.createElement('textarea');
    result.className = 'pe-result';
    result.placeholder = 'Результат';
    result.value = project.result || '';
    result.setAttribute('aria-label', 'Результат');

    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'sk-remove pe-remove';
    removeBtn.textContent = '\u00d7';
    removeBtn.setAttribute('aria-label', 'Удалить проект');
    removeBtn.addEventListener('click', function () {
      row.remove();
    });

    row.appendChild(title);
    row.appendChild(category);
    row.appendChild(year);
    row.appendChild(short);
    row.appendChild(cover);
    row.appendChild(gallery);
    row.appendChild(task);
    row.appendChild(role);
    row.appendChild(stack);
    row.appendChild(result);
    row.appendChild(removeBtn);
    return row;
  }

  /* Собирает список проектов из строк редактора */
  function collectProjects() {
    var box = document.getElementById('projects-editor');
    if (!box) return [];
    var projects = [];
    box.querySelectorAll('.project-edit-row').forEach(function (row) {
      var title = (row.querySelector('.pe-title').value || '').trim();
      if (!title) return;
      function lines(el) {
        return (el.value || '').split(/\n+/).map(function (s) { return s.trim(); }).filter(Boolean);
      }
      projects.push({
        title: title,
        category: row.querySelector('.pe-category').value || 'web',
        year: (row.querySelector('.pe-year').value || '').trim(),
        short: (row.querySelector('.pe-short').value || '').trim(),
        cover: (row.querySelector('.pe-cover').value || '').trim(),
        gallery: lines(row.querySelector('.pe-gallery')),
        task: (row.querySelector('.pe-task').value || '').trim(),
        role: (row.querySelector('.pe-role').value || '').trim(),
        stack: lines(row.querySelector('.pe-stack')),
        result: (row.querySelector('.pe-result').value || '').trim()
      });
    });
    return projects;
  }

  /* Предзаполняем редактор проектов: из профиля или дефолтными */
  function prefillProjects() {
    var box = document.getElementById('projects-editor');
    if (!box) return;
    var projects = profile.projects && profile.projects.length ? profile.projects : PROJECTS;
    box.innerHTML = '';
    projects.forEach(function (p) { box.appendChild(projectRow(p)); });
  }

  /* Превью фотографии в форме настроек */
  function refreshPhotoPreview() {
    var preview = document.getElementById('photo-preview');
    if (!preview) return;
    if (profile.photo) {
      preview.src = profile.photo;
      preview.hidden = false;
    } else {
      preview.hidden = true;
    }
  }

  /* Предзаполняем форму профиля текущими данными */
  function prefillProfileForm() {
    document.getElementById('pf-name').value = profile.name || '';
    document.getElementById('pf-role').value = profile.role || '';
    document.getElementById('pf-bio').value = (profile.bio || []).join('\n\n');
    document.getElementById('pf-hero').value = profile.hero_text || '';
    document.getElementById('pf-email').value = profile.email || '';
    document.getElementById('pf-location').value = profile.location || '';
    document.getElementById('pf-address').value = profile.address || '';
    document.getElementById('pf-timezone').value = profile.timezone || '';
    document.getElementById('pf-telegram').value = profile.telegram || '';
    document.getElementById('pf-github').value = profile.github || '';
    document.getElementById('pf-behance').value = profile.behance || '';
    refreshPhotoPreview();
    prefillSkills();
    prefillTestimonials();
    prefillProjects();
  }

  function inputValue(id) {
    var el = document.getElementById(id);
    return el ? el.value.trim() : '';
  }

  /* Абзацы: в поле — пустая строка между абзацами */
  function bioToArray(text) {
    return String(text || '')
      .split(/\n\s*\n/)
      .map(function (p) { return p.trim(); })
      .filter(function (p) { return p.length > 0; });
  }

  function showProfileStatus(message, ok) {
    var el = document.getElementById('profile-status');
    if (!el) return;
    el.textContent = message;
    el.className = 'profile-status' + (ok ? ' is-ok' : ' is-error');
    clearTimeout(profileStatusTimer);
    profileStatusTimer = setTimeout(function () { el.textContent = ''; }, 4000);
  }

  /* ---------- Контакты: валидация формы ---------- */

  function initContactForm() {
    var form = document.getElementById('contact-form');
    if (!form) return;

    var success = form.querySelector('.form__success');
    var errorBox = form.querySelector('.form__error');
    var submitBtn = form.querySelector('button[type="submit"]');

    function setError(field, hasError) {
      field.classList.toggle('field--error', hasError);
      var errorEl = field.querySelector('.field__error');
      var input = field.querySelector('input, textarea');
      if (errorEl) errorEl.hidden = !hasError;
      if (input) input.setAttribute('aria-invalid', hasError ? 'true' : 'false');
    }

    function validate(field) {
      var input = field.querySelector('input, textarea');
      var value = (input.value || '').trim();
      var valid = true;

      if (input.required && value === '') valid = false;
      if (valid && input.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) valid = false;
      if (valid && input.minLength && value.length < parseInt(input.minLength, 10)) valid = false;

      return valid;
    }

    function showStatus(box, on) {
      if (!box) return;
      box.hidden = !on;
    }

    form.querySelectorAll('.field').forEach(function (field) {
      var input = field.querySelector('input, textarea');
      input.addEventListener('input', function () { setError(field, false); });
      input.addEventListener('blur', function () { setError(field, !validate(field)); });
    });

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var allValid = true;
      form.querySelectorAll('.field').forEach(function (field) {
        var ok = validate(field);
        setError(field, !ok);
        if (!ok) allValid = false;
      });

      if (!allValid) return;
      showStatus(success, false);
      showStatus(errorBox, false);

      var payload = {
        name: inputValue('cf-name'),
        email: inputValue('cf-email'),
        message: inputValue('cf-message')
      };

      if (submitBtn) submitBtn.disabled = true;

      API.sendContact(payload)
        .then(function () {
          form.reset();
          showStatus(success, true);
          setTimeout(function () { showStatus(success, false); }, 6000);
        })
        .catch(function () {
          showStatus(errorBox, true);
          setTimeout(function () { showStatus(errorBox, false); }, 6000);
        })
        .then(function () {
          if (submitBtn) submitBtn.disabled = false;
        });
    });
  }

  /* ---------- Загрузка профиля из API ---------- */

  function loadProfile() {
    if (!window.API) return;
    API.getProfile()
      .then(function (data) {
        profile = Object.assign({}, profile, data);
        renderIdentity();
        refreshProjectViews();
        observeReveals();
        /* предзаполняем форму на странице настроек */
        if (document.getElementById('pf-name')) {
          prefillProfileForm();
        }
      })
      .catch(function () {
        /* API недоступен — остаётся дефолтный профиль */
      });
  }

  /* ---------- Инициализация ---------- */

  function init() {
    injectShell();
    setActiveNav();
    renderIdentity();          /* применяем дефолтный профиль сразу */
    fillAddress();
    initBurger();
    initReveal();
    initSmoothScroll();
    initFeatured();
    initPortfolio();
    initCase();
    initSettingsPage();
    initContactForm();

    /* Обновляем «обращение» в реальном времени (страница настроек) */
    document.addEventListener('pf:settings', fillAddress);

    /* Подгружаем профиль из БД через API и обновляем страницу */
    loadProfile();
  }

  document.addEventListener('DOMContentLoaded', init);
})();
