"""
main.py
Точка входа FastAPI-приложения.

Приложение:
  - при старте создаёт таблицы и заполняет профиль дефолтными данными;
  - отдаёт API под префиксом /api;
  - раздаёт статику сайта (index.html и др.) с корня / — единый origin, CORS не нужен.
"""
import json
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from .database import Base, SessionLocal, engine
from .models import Profile, Project, Skill, Testimonial
from .routers import contact as contact_router
from .routers import profile as profile_router

# Папка сайта (родительская для backend/)
SITE_DIR = Path(__file__).resolve().parent.parent.parent

# Дефолтные данные профиля — совпадают с исходным содержимым сайта
DEFAULT_PROFILE = {
    "name": "Иван Петров",
    "role": "фронтенд-разработчик",
    "photo": "https://picsum.photos/seed/ivan-portrait/800/1000",
    "bio": [
        "Я — фронтенд-разработчик с семилетним опытом. Начинал с простых лендингов, а сегодня проектирую и собираю сложные интерфейсы для продуктов: от интернет-магазинов и галерей до аналитических дашбордов и мобильных приложений.",
        "Мне интересен весь путь продукта: от первой зарисовки макета и прототипа до финальной анимации и оптимизации под метрики. Умею находить общий язык и с дизайнерами, и с бэкенд-разработчиками, поэтому люблю работать в командах с плотной коммуникацией.",
        "В свободное время экспериментирую с генеративной графикой, веду небольшой блог о типографике в интерфейсах и помогаю начинающим разработчикам с код-ревью.",
    ],
    "hero_text": "Делаю быстрые, доступные и выразительные интерфейсы. Люблю доводить детали до совершенства — от сетки и типографики до анимации и производительности.",
    "email": "hello@ivanpetrov.dev",
    "location": "Москва, Россия",
    "address": "Москва, ул. Тверская, 12, офис 304",
    "timezone": "UTC+3",
    "telegram": "https://t.me/",
    "github": "https://github.com/",
    "behance": "https://www.behance.net/",
}

# Дефолтный список профессиональных навыков
DEFAULT_SKILLS = [
    {"name": "HTML / CSS", "level": 95},
    {"name": "JavaScript / TypeScript", "level": 90},
    {"name": "React / Vue", "level": 85},
    {"name": "UI / UX дизайн", "level": 75},
    {"name": "Анимация интерфейсов", "level": 70},
    {"name": "Node.js / инструменты сборки", "level": 60},
]

# Дефолтные отзывы клиентов
DEFAULT_TESTIMONIALS = [
    {
        "name": "Анна Соколова",
        "role": "Руководитель отдела маркетинга, арт-галерея «Полотно»",
        "text": "Иван полностью закрыл сайт галереи: от прототипа до релиза. Сдал всё раньше срока, а сайт отлично выдержал наплыв посетителей на открытии выставки.",
    },
    {
        "name": "Дмитрий Ковалёв",
        "role": "Продуктовый директор, издательство «Восход»",
        "text": "Сервис аналитики, который сделал Иван, изменил нашу работу: отчёты теперь готовятся за минуты, а не за часы. Приятно работать с человеком, который слышит задачу.",
    },
    {
        "name": "Мария Лебедева",
        "role": "Основательница кофейни «Зерно»",
        "text": "Иван сделал фирменный стиль, который сразу узнаётся. Клиенты хвалят и упаковку, и сайт. Это лучшие вложения в бренд за всё время существования кофейни.",
    },
]

# Дефолтные проекты портфолио — совпадают с исходными данными сайта
DEFAULT_PROJECTS = [
    {
        "title": "Сайт арт-галереи «Полотно»",
        "category": "web",
        "year": "2025",
        "short": "Виртуальные туры по залам, каталог выставок и медиатека.",
        "cover": "https://picsum.photos/seed/gallery-cover/1200/900",
        "gallery": [
            "https://picsum.photos/seed/gallery-1/1200/900",
            "https://picsum.photos/seed/gallery-2/900/1200",
            "https://picsum.photos/seed/gallery-3/1200/800",
            "https://picsum.photos/seed/gallery-4/1200/900",
        ],
        "task": "Спроектировать и разработать сайт галереи современного искусства: каталог выставок, виртуальные туры по залам, медиатека и запись на экскурсии.",
        "role": "Фронтенд-разработчик",
        "stack": ["HTML", "CSS", "JavaScript", "Vite"],
        "result": "Сайт выдержал пиковые нагрузки в дни открытий выставок, загрузка страниц — менее 1,2 с, посещаемость выросла на 40%.",
    },
    {
        "title": "Брендинг кофейни «Зерно»",
        "category": "design",
        "year": "2024",
        "short": "Логотип, фирменный стиль, упаковка и лендинг для доставки.",
        "cover": "https://picsum.photos/seed/coffee-brand/1200/900",
        "gallery": [
            "https://picsum.photos/seed/coffee-1/1200/900",
            "https://picsum.photos/seed/coffee-2/900/1200",
            "https://picsum.photos/seed/coffee-3/1200/800",
            "https://picsum.photos/seed/coffee-4/1200/900",
        ],
        "task": "Создать айдентику кофейни с нуля: логотип, фирменный стиль, упаковка, меню и посадочная страница для доставки.",
        "role": "Дизайнер, арт-директор",
        "stack": ["Figma", "Illustrator", "HTML", "CSS"],
        "result": "Стиль внедрён во всех точках касания: упаковка, меню, соцсети. Продажи через лендинг выросли на 25%.",
    },
    {
        "title": "Сервис аналитики чтения «Ось»",
        "category": "web",
        "year": "2025",
        "short": "Дашборды читательских привычек для издательств.",
        "cover": "https://picsum.photos/seed/analytics-app/1200/900",
        "gallery": [
            "https://picsum.photos/seed/analytics-1/1200/900",
            "https://picsum.photos/seed/analytics-2/900/1200",
            "https://picsum.photos/seed/analytics-3/1200/800",
            "https://picsum.photos/seed/analytics-4/1200/900",
        ],
        "task": "Разработать интерфейс дашбордов: графики вовлечённости, отчёты для издательств, настройка и экспорт данных.",
        "role": "Фронтенд-разработчик",
        "stack": ["HTML", "CSS", "JavaScript", "Chart.js"],
        "result": "Время на подготовку отчёта сократилось с 2 часов до 4 минут, сервис используют 12 издательств.",
    },
    {
        "title": "Интерфейс мобильного банка",
        "category": "design",
        "year": "2024",
        "short": "Дизайн-система, ключевые экраны, тёмная тема и доступность.",
        "cover": "https://picsum.photos/seed/bank-app/1200/900",
        "gallery": [
            "https://picsum.photos/seed/bank-1/1200/900",
            "https://picsum.photos/seed/bank-2/900/1200",
            "https://picsum.photos/seed/bank-3/1200/800",
            "https://picsum.photos/seed/bank-4/1200/900",
        ],
        "task": "Переработать дизайн мобильного приложения банка: дизайн-система, ключевые экраны, тёмная тема и доступность по WCAG AA.",
        "role": "UI/UX-дизайнер",
        "stack": ["Figma", "Design tokens", "Прототипирование"],
        "result": "Оценка в сторах выросла с 3,8 до 4,6, число жалоб на интерфейс снизилось на треть.",
    },
    {
        "title": "Каталог винтажной электроники",
        "category": "web",
        "year": "2023",
        "short": "Магазин-витрина с фильтрами, поиском и корзиной.",
        "cover": "https://picsum.photos/seed/retro-store/1200/900",
        "gallery": [
            "https://picsum.photos/seed/retro-1/1200/900",
            "https://picsum.photos/seed/retro-2/900/1200",
            "https://picsum.photos/seed/retro-3/1200/800",
            "https://picsum.photos/seed/retro-4/1200/900",
        ],
        "task": "Сверстать магазин винтажной электроники: каталог с фильтрами по годам и типу, поиск, корзина и быстрая выдача.",
        "role": "Фронтенд-разработчик",
        "stack": ["HTML", "CSS", "JavaScript"],
        "result": "Конверсия в заказ выросла на 18%, среднее время на сайте — 4 минуты.",
    },
    {
        "title": "Генератор постеров",
        "category": "other",
        "year": "2025",
        "short": "Эксперимент с генеративной типографикой и canvas.",
        "cover": "https://picsum.photos/seed/poster-gen/1200/900",
        "gallery": [
            "https://picsum.photos/seed/poster-1/1200/900",
            "https://picsum.photos/seed/poster-2/900/1200",
            "https://picsum.photos/seed/poster-3/1200/800",
            "https://picsum.photos/seed/poster-4/1200/900",
        ],
        "task": "Сделать экспериментальный генератор постеров: шум, сетки и шрифтовые композиции, рендер на canvas.",
        "role": "Креативный разработчик",
        "stack": ["HTML", "CSS", "JavaScript", "Canvas API"],
        "result": "Проект стал участником онлайн-фестиваля генеративного искусства.",
    },
]


def seed_profile() -> None:
    """Создаёт таблицы и, если профиля ещё нет, вставляет дефолтные данные."""
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        if db.get(Profile, 1) is None:
            db.add(
                Profile(
                    id=1,
                    name=DEFAULT_PROFILE["name"],
                    role=DEFAULT_PROFILE["role"],
                    photo=DEFAULT_PROFILE["photo"],
                    bio=json.dumps(DEFAULT_PROFILE["bio"], ensure_ascii=False),
                    hero_text=DEFAULT_PROFILE["hero_text"],
                    email=DEFAULT_PROFILE["email"],
                    location=DEFAULT_PROFILE["location"],
                    address=DEFAULT_PROFILE["address"],
                    timezone=DEFAULT_PROFILE["timezone"],
                    telegram=DEFAULT_PROFILE["telegram"],
                    github=DEFAULT_PROFILE["github"],
                    behance=DEFAULT_PROFILE["behance"],
                )
            )
            db.commit()

        # Навыки сидим только если таблица пуста (не мешаем пользователю)
        if db.query(Skill).count() == 0:
            for i, skill in enumerate(DEFAULT_SKILLS):
                db.add(
                    Skill(name=skill["name"], level=skill["level"], position=i)
                )
            db.commit()

        # Отзывы сидим только если таблица пуста
        if db.query(Testimonial).count() == 0:
            for i, item in enumerate(DEFAULT_TESTIMONIALS):
                db.add(
                    Testimonial(
                        name=item["name"],
                        role=item["role"],
                        text=item["text"],
                        position=i,
                    )
                )
            db.commit()

        # Проекты сидим только если таблица пуста
        if db.query(Project).count() == 0:
            for i, project in enumerate(DEFAULT_PROJECTS):
                db.add(
                    Project(
                        title=project["title"],
                        category=project["category"],
                        year=project["year"],
                        short=project["short"],
                        cover=project["cover"],
                        gallery=json.dumps(project["gallery"], ensure_ascii=False),
                        task=project["task"],
                        role=project["role"],
                        stack=json.dumps(project["stack"], ensure_ascii=False),
                        result=project["result"],
                        position=i,
                    )
                )
            db.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Выполняется при старте приложения."""
    seed_profile()
    yield


app = FastAPI(title="Portfolio API", lifespan=lifespan)


@app.get("/api/health")
def health():
    """Проверка, что сервер жив."""
    return {"status": "ok"}


app.include_router(profile_router.router)
app.include_router(contact_router.router)

# Статику подключаем ПОСЛЕ API-маршрутов, чтобы /api работали раньше
app.mount("/", StaticFiles(directory=SITE_DIR, html=True), name="static")
