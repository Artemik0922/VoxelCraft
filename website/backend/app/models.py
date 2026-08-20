"""
models.py
ORM-модели. Пока одна таблица — profile (одна строка с содержимым «о себе»).
"""
from datetime import datetime, timezone

from sqlalchemy import DateTime, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base


class Profile(Base):
    """Строка с содержимым профиля «о себе» и контактами."""

    __tablename__ = "profile"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)

    # Кто я
    name: Mapped[str] = mapped_column(String(200), default="")
    role: Mapped[str] = mapped_column(String(200), default="")

    # Фотография профиля: URL (внешний) или путь к загруженному файлу (/uploads/...)
    photo: Mapped[str] = mapped_column(String(500), default="")

    # Биография — JSON-массив абзацев
    bio: Mapped[str] = mapped_column(Text, default="[]")

    # Текст-интро героя на главной
    hero_text: Mapped[str] = mapped_column(Text, default="")

    # Контакты
    email: Mapped[str] = mapped_column(String(300), default="")
    location: Mapped[str] = mapped_column(String(200), default="")
    address: Mapped[str] = mapped_column(String(300), default="")
    timezone: Mapped[str] = mapped_column(String(100), default="")
    telegram: Mapped[str] = mapped_column(String(300), default="")
    github: Mapped[str] = mapped_column(String(300), default="")
    behance: Mapped[str] = mapped_column(String(300), default="")

    updated_at: Mapped[str] = mapped_column(String(40), default=lambda: _now())

    def __repr__(self) -> str:
        return f"<Profile id={self.id} name={self.name!r}>"


class Skill(Base):
    """Профессиональный навык: название и уровень владения (0-100)."""

    __tablename__ = "skills"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(200), default="")
    level: Mapped[int] = mapped_column(Integer, default=0)
    # Порядок вывода списка навыков
    position: Mapped[int] = mapped_column(Integer, default=0)

    def __repr__(self) -> str:
        return f"<Skill id={self.id} name={self.name!r} level={self.level}>"


class Testimonial(Base):
    """Отзыв клиента: имя, роль и текст отзыва."""

    __tablename__ = "testimonials"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(200), default="")
    role: Mapped[str] = mapped_column(String(200), default="")
    text: Mapped[str] = mapped_column(Text, default="")
    # Порядок вывода списка отзывов
    position: Mapped[int] = mapped_column(Integer, default=0)

    def __repr__(self) -> str:
        return f"<Testimonial id={self.id} name={self.name!r}>"


class Project(Base):
    """Проект портфолио: карточка и полное описание кейса."""

    __tablename__ = "projects"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    title: Mapped[str] = mapped_column(String(200), default="")
    category: Mapped[str] = mapped_column(String(50), default="web")
    year: Mapped[str] = mapped_column(String(20), default="")
    short: Mapped[str] = mapped_column(String(500), default="")
    cover: Mapped[str] = mapped_column(String(500), default="")
    # Галерея кейса — JSON-массив URL
    gallery: Mapped[str] = mapped_column(Text, default="[]")
    task: Mapped[str] = mapped_column(Text, default="")
    role: Mapped[str] = mapped_column(String(200), default="")
    # Стек — JSON-массив строк
    stack: Mapped[str] = mapped_column(Text, default="[]")
    result: Mapped[str] = mapped_column(Text, default="")
    # Порядок вывода списка проектов
    position: Mapped[int] = mapped_column(Integer, default=0)

    def __repr__(self) -> str:
        return f"<Project id={self.id} title={self.title!r}>"


class Message(Base):
    """Сообщение из формы контактов."""

    __tablename__ = "messages"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(200), default="")
    email: Mapped[str] = mapped_column(String(300), default="")
    message: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[str] = mapped_column(String(40), default=lambda: _now())

    def __repr__(self) -> str:
        return f"<Message id={self.id} name={self.name!r}>"


def _now() -> str:
    """Текущее время в ISO-формате (UTC)."""
    return datetime.now(timezone.utc).isoformat()
