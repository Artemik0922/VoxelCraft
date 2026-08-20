"""
schemas.py
Pydantic-схемы для валидации и сериализации профиля.
"""
from pydantic import BaseModel, ConfigDict, Field


class SkillIn(BaseModel):
    """Навык, приходящий из формы «О себе»."""

    name: str = Field(default="", max_length=200)
    level: int = Field(default=0, ge=0, le=100)


class SkillOut(SkillIn):
    """Навык в ответе API."""

    model_config = ConfigDict(from_attributes=True)

    id: int


class TestimonialIn(BaseModel):
    """Отзыв клиента, приходящий из формы «О себе»."""

    name: str = Field(default="", max_length=200)
    role: str = Field(default="", max_length=200)
    text: str = Field(default="", max_length=2000)


class TestimonialOut(TestimonialIn):
    """Отзыв клиента в ответе API."""

    model_config = ConfigDict(from_attributes=True)

    id: int


class PhotoUploadOut(BaseModel):
    """Ответ эндпоинта загрузки фотографии."""

    photo: str


class ProjectIn(BaseModel):
    """Проект портфолио, приходящий из формы настроек."""

    title: str = Field(default="", max_length=200)
    category: str = Field(default="web", max_length=50)
    year: str = Field(default="", max_length=20)
    short: str = Field(default="", max_length=500)
    cover: str = Field(default="", max_length=500)
    gallery: list[str] = Field(default_factory=list, max_length=20)
    task: str = Field(default="", max_length=5000)
    role: str = Field(default="", max_length=200)
    stack: list[str] = Field(default_factory=list, max_length=20)
    result: str = Field(default="", max_length=5000)


class ProjectOut(ProjectIn):
    """Проект портфолио в ответе API."""

    model_config = ConfigDict(from_attributes=True)

    id: int


class ProfileIn(BaseModel):
    """Схема приёма данных из формы «О себе» (PUT)."""

    model_config = ConfigDict(extra="forbid")

    name: str = Field(default="", max_length=200)
    role: str = Field(default="", max_length=200)
    photo: str = Field(default="", max_length=500)
    # Биография — массив абзацев
    bio: list[str] = Field(default_factory=list, max_length=30)
    hero_text: str = Field(default="", max_length=2000)

    email: str = Field(default="", max_length=300)
    location: str = Field(default="", max_length=200)
    address: str = Field(default="", max_length=300)
    timezone: str = Field(default="", max_length=100)
    telegram: str = Field(default="", max_length=300)
    github: str = Field(default="", max_length=300)
    behance: str = Field(default="", max_length=300)

    # Список профессиональных навыков
    skills: list[SkillIn] = Field(default_factory=list, max_length=50)

    # Список отзывов клиентов
    testimonials: list[TestimonialIn] = Field(default_factory=list, max_length=30)

    # Список проектов портфолио
    projects: list[ProjectIn] = Field(default_factory=list, max_length=100)


class ProfileOut(BaseModel):
    """Схема ответа: профиль + навыки + отзывы + дата обновления."""

    model_config = ConfigDict(from_attributes=True)

    name: str
    role: str
    photo: str
    bio: list[str]
    hero_text: str
    email: str
    location: str
    address: str
    timezone: str
    telegram: str
    github: str
    behance: str
    updated_at: str
    skills: list[SkillOut] = Field(default_factory=list)
    testimonials: list[TestimonialOut] = Field(default_factory=list)
    projects: list[ProjectOut] = Field(default_factory=list)


class ContactIn(BaseModel):
    """Сообщение из формы контактов."""

    name: str = Field(min_length=2, max_length=200)
    email: str = Field(pattern=r"^[^\s@]+@[^\s@]+\.[^\s@]+$", max_length=300)
    message: str = Field(min_length=10, max_length=5000)


class ContactOut(ContactIn):
    """Сообщение в ответе API."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    created_at: str
