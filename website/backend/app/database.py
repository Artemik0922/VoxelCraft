"""
database.py
Настройка подключения к SQLite через SQLAlchemy.
Файл базы создаётся автоматически при первом подключении.
"""
from pathlib import Path

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

# Файл базы лежит рядом с этим модулем: backend/profile.db
BASE_DIR = Path(__file__).resolve().parent.parent
DB_PATH = BASE_DIR / "profile.db"

# check_same_thread=False — FastAPI может обращаться из разных потоков
engine = create_engine(
    f"sqlite:///{DB_PATH}",
    connect_args={"check_same_thread": False},
)

SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


class Base(DeclarativeBase):
    """Базовый класс всех ORM-моделей."""


def get_db():
    """Зависимость FastAPI: даёт сессию БД и закрывает её после запроса."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
