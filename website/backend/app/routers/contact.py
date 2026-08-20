"""
routers/contact.py
Эндпоинты сообщений из формы контактов.
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Message
from ..schemas import ContactIn, ContactOut
from ..security import require_admin

router = APIRouter(prefix="/api/contact", tags=["contact"])


@router.post("", response_model=ContactOut, status_code=201)
def create_message(payload: ContactIn, db: Session = Depends(get_db)):
    """Сохраняет сообщение из формы контактов."""
    row = Message(
        name=payload.name.strip(),
        email=payload.email.strip(),
        message=payload.message.strip(),
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return ContactOut(
        id=row.id,
        name=row.name,
        email=row.email,
        message=row.message,
        created_at=row.created_at,
    )


@router.get("/messages", response_model=list[ContactOut])
def list_messages(
    db: Session = Depends(get_db),
    _admin: None = Depends(require_admin),
):
    """Список сообщений (новые сверху) — только для администратора."""
    rows = db.query(Message).order_by(Message.created_at.desc()).limit(100).all()
    return [
        ContactOut(
            id=m.id,
            name=m.name,
            email=m.email,
            message=m.message,
            created_at=m.created_at,
        )
        for m in rows
    ]