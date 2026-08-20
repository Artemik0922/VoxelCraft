/**
 * api.js
 * Работа с бэкендом FastAPI: чтение и сохранение профиля «о себе».
 *
 * Скрипт подключается в <body> перед main.js. Если бэкенд недоступен
 * (например, сайт открыт напрямую через file://), используется фолбэк —
 * DEFAULT_PROFILE с исходным содержимым портфолио.
 */

/* Дефолтные данные профиля — используются, когда API не отвечает */
var DEFAULT_PROFILE = {
  name: 'Иван Петров',
  role: 'фронтенд-разработчик',
  photo: 'https://picsum.photos/seed/ivan-portrait/800/1000',
  bio: [
    'Я — фронтенд-разработчик с семилетним опытом. Начинал с простых лендингов, а сегодня проектирую и собираю сложные интерфейсы для продуктов: от интернет-магазинов и галерей до аналитических дашбордов и мобильных приложений.',
    'Мне интересен весь путь продукта: от первой зарисовки макета и прототипа до финальной анимации и оптимизации под метрики. Умею находить общий язык и с дизайнерами, и с бэкенд-разработчиками, поэтому люблю работать в командах с плотной коммуникацией.',
    'В свободное время экспериментирую с генеративной графикой, веду небольшой блог о типографике в интерфейсах и помогаю начинающим разработчикам с код-ревью.'
  ],
  hero_text: 'Делаю быстрые, доступные и выразительные интерфейсы. Люблю доводить детали до совершенства — от сетки и типографики до анимации и производительности.',
  email: 'hello@ivanpetrov.dev',
  location: 'Москва, Россия',
  address: 'Москва, ул. Тверская, 12, офис 304',
  timezone: 'UTC+3',
  telegram: 'https://t.me/',
  github: 'https://github.com/',
  behance: 'https://www.behance.net/',
  skills: [
    { name: 'HTML / CSS', level: 95 },
    { name: 'JavaScript / TypeScript', level: 90 },
    { name: 'React / Vue', level: 85 },
    { name: 'UI / UX дизайн', level: 75 },
    { name: 'Анимация интерфейсов', level: 70 },
    { name: 'Node.js / инструменты сборки', level: 60 }
  ],
  testimonials: [
    {
      name: 'Анна Соколова',
      role: 'Руководитель отдела маркетинга, арт-галерея «Полотно»',
      text: 'Иван полностью закрыл сайт галереи: от прототипа до релиза. Сдал всё раньше срока, а сайт отлично выдержал наплыв посетителей на открытии выставки.'
    },
    {
      name: 'Дмитрий Ковалёв',
      role: 'Продуктовый директор, издательство «Восход»',
      text: 'Сервис аналитики, который сделал Иван, изменил нашу работу: отчёты теперь готовятся за минуты, а не за часы. Приятно работать с человеком, который слышит задачу.'
    },
    {
      name: 'Мария Лебедева',
      role: 'Основательница кофейни «Зерно»',
      text: 'Иван сделал фирменный стиль, который сразу узнаётся. Клиенты хвалят и упаковку, и сайт. Это лучшие вложения в бренд за всё время существования кофейни.'
    }
  ],
  projects: [
    {
      title: 'Сайт арт-галереи «Полотно»',
      category: 'web',
      year: '2025',
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
      title: 'Брендинг кофейни «Зерно»',
      category: 'design',
      year: '2024',
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
      title: 'Сервис аналитики чтения «Ось»',
      category: 'web',
      year: '2025',
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
      title: 'Интерфейс мобильного банка',
      category: 'design',
      year: '2024',
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
      title: 'Каталог винтажной электроники',
      category: 'web',
      year: '2023',
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
      title: 'Генератор постеров',
      category: 'other',
      year: '2025',
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
  ]
};

var API = (function () {
  'use strict';

  /* Базовый путь API: сайт и API раздаются с одного сервера */
  var BASE = '/api/profile';

  /* Ключ localStorage для пароля администратора */
  var TOKEN_KEY = 'pf_admin_token';

  /* Читаем пароль администратора из localStorage */
  function getToken() {
    try {
      return localStorage.getItem(TOKEN_KEY) || '';
    } catch (err) {
      return '';
    }
  }

  /* Сохраняем пароль администратора в localStorage */
  function setToken(token) {
    try {
      if (token) {
        localStorage.setItem(TOKEN_KEY, token);
      } else {
        localStorage.removeItem(TOKEN_KEY);
      }
    } catch (err) {
      /* localStorage может быть недоступен, молча пропускаем */
    }
  }

  /* Заголовок с паролем, если он задан */
  function adminHeader() {
    var token = getToken();
    return token ? { 'X-Admin-Token': token } : {};
  }

  /* Превращает ответ fetch в JSON, в ошибку кладёт HTTP-статус */
  function handle(res) {
    if (!res.ok) {
      var err = new Error('HTTP ' + res.status);
      err.status = res.status;
      throw err;
    }
    return res.json();
  }

  function getProfile() {
    return fetch(BASE, { headers: { Accept: 'application/json' } })
      .then(handle)
      .then(function (data) {
        /* объединяем с дефолтами, чтобы не потерять поля */
        return Object.assign({}, DEFAULT_PROFILE, data);
      });
  }

  function saveProfile(payload) {
    var headers = Object.assign({ 'Content-Type': 'application/json' }, adminHeader());
    return fetch(BASE, {
      method: 'PUT',
      headers: headers,
      body: JSON.stringify(payload)
    }).then(handle);
  }

  /* Отправка сообщения из формы контактов */
  function sendContact(payload) {
    return fetch('/api/contact', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(handle);
  }

  return {
    BASE: BASE,
    getProfile: getProfile,
    saveProfile: saveProfile,
    sendContact: sendContact,
    getToken: getToken,
    setToken: setToken
  };
})();
