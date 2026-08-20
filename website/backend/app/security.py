"""
security.py
Проверка пароля администратора для изменяющих эндпоинтов.

Пароль задаётся переменной окружения ADMIN_TOKEN. Если она не задана
или пуста, доступ открыт (режим разработки). Клиент передаёт пароль
в заголовке X-Admin-Token (см. поле «Пароль администратора» на
странице настроек).
"""
import os

from fastapi import Header, HTTPException


def require_admin(x_admin_token: str | None = Header(default=None)) -> None:
    """Зависимость FastAPI: пропускает запрос, если пароль верный."""
    token = os.environ.get("ADMIN_TOKEN", "").strip()
    if not token:
        # Защита не настроена — открытый доступ (разработка)
        return
    if x_admin_token != token:
        raise HTTPException(status_code=401, detail="Неверный пароль администратора")