"""
routers/profile.py
Эндпоинты работы с профилем «о себе», навыками, отзывами и фото.
"""
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File
from sqlalchemy import delete
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Profile, Project, Skill, Testimonial
from ..schemas import PhotoUploadOut, ProfileIn, ProfileOut, ProjectOut, SkillOut, TestimonialOut
from ..security import require_admin

router = APIRouter(prefix="/api/profile", tags=["profile"])

# Папка сайта: website/ (backend/app/routers/ -> website/)
SITE_DIR = Path(__file__).resolve().parent.parent.parent.parent
UPLOAD_DIR = SITE_DIR / "uploads"
ALLOWED_PHOTO_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".gif"}


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


@router.get("", response_model=ProfileOut)
def get_profile(db: Session = Depends(get_db)):
    """Возвращает профиль «о себе» вместе с навыками и отзывами."""
    row = db.get(Profile, 1)
    if row is None:
        raise HTTPException(status_code=404, detail="Profile not found")
    return _build_out(db, row)


@router.put("", response_model=ProfileOut)
def update_profile(
    payload: ProfileIn,
    db: Session = Depends(get_db),
    _admin: None = Depends(require_admin),
):
    """Обновляет (или создаёт) профиль и полностью заменяет навыки и отзывы."""
    row = db.get(Profile, 1)
    if row is None:
        row = Profile(id=1)
        db.add(row)

    row.name = payload.name
    row.role = payload.role
    row.photo = payload.photo
    row.bio = json.dumps(payload.bio, ensure_ascii=False)
    row.hero_text = payload.hero_text
    row.email = payload.email
    row.location = payload.location
    row.address = payload.address
    row.timezone = payload.timezone
    row.telegram = payload.telegram
    row.github = payload.github
    row.behance = payload.behance
    row.updated_at = _now()

    # Полностью перезаписываем навыки
    db.execute(delete(Skill))
    for i, skill in enumerate(payload.skills):
        db.add(
            Skill(
                name=skill.name,
                level=skill.level,
                position=i,
            )
        )

    # Полностью перезаписываем отзывы
    db.execute(delete(Testimonial))
    for i, item in enumerate(payload.testimonials):
        db.add(
            Testimonial(
                name=item.name,
                role=item.role,
                text=item.text,
                position=i,
            )
        )

    # Полностью перезаписываем проекты портфолио
    db.execute(delete(Project))
    for i, project in enumerate(payload.projects):
        db.add(
            Project(
                title=project.title,
                category=project.category,
                year=project.year,
                short=project.short,
                cover=project.cover,
                gallery=json.dumps(project.gallery, ensure_ascii=False),
                task=project.task,
                role=project.role,
                stack=json.dumps(project.stack, ensure_ascii=False),
                result=project.result,
                position=i,
            )
        )

    db.commit()
    db.refresh(row)
    return _build_out(db, row)


@router.post("/photo", response_model=PhotoUploadOut)
def upload_photo(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    _admin: None = Depends(require_admin),
):
    """Загружает фотографию профиля и сохраняет её в /uploads."""
    ext = Path(file.filename or "").suffix.lower()
    if ext not in ALLOWED_PHOTO_EXTS:
        raise HTTPException(status_code=400, detail="Недопустимый формат файла")
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Файл не является изображением")

    UPLOAD_DIR.mkdir(exist_ok=True)
    # Постоянное имя файла — новая загрузка просто перезаписывает фото
    fname = "profile" + ext
    dest = UPLOAD_DIR / fname

    with dest.open("wb") as out:
        shutil.copyfileobj(file.file, out)
    file.file.close()

    row = db.get(Profile, 1)
    if row is None:
        row = Profile(id=1)
        db.add(row)
    row.photo = f"/uploads/{fname}"
    row.updated_at = _now()
    db.commit()

    return PhotoUploadOut(photo=row.photo)


def _load_skills(db: Session) -> list[SkillOut]:
    """Читает навыки, отсортированные по порядку."""
    rows = db.query(Skill).order_by(Skill.position.asc()).all()
    return [SkillOut(id=s.id, name=s.name, level=s.level) for s in rows]


def _load_testimonials(db: Session) -> list[TestimonialOut]:
    """Читает отзывы, отсортированные по порядку."""
    rows = db.query(Testimonial).order_by(Testimonial.position.asc()).all()
    return [
        TestimonialOut(id=t.id, name=t.name, role=t.role, text=t.text)
        for t in rows
    ]


def _load_projects(db: Session) -> list[ProjectOut]:
    """Читает проекты, отсортированные по порядку."""
    rows = db.query(Project).order_by(Project.position.asc()).all()
    result = []
    for p in rows:
        try:
            gallery = json.loads(p.gallery) if p.gallery else []
        except json.JSONDecodeError:
            gallery = []
        try:
            stack = json.loads(p.stack) if p.stack else []
        except json.JSONDecodeError:
            stack = []
        if not isinstance(gallery, list):
            gallery = []
        if not isinstance(stack, list):
            stack = []
        result.append(
            ProjectOut(
                id=p.id,
                title=p.title or "",
                category=p.category or "web",
                year=p.year or "",
                short=p.short or "",
                cover=p.cover or "",
                gallery=gallery,
                task=p.task or "",
                role=p.role or "",
                stack=stack,
                result=p.result or "",
            )
        )
    return result


def _build_out(db: Session, row: Profile) -> ProfileOut:
    """ORM-строка профиля + навыки + отзывы -> Pydantic-ответ."""
    try:
        bio = json.loads(row.bio) if row.bio else []
    except json.JSONDecodeError:
        bio = []
    if not isinstance(bio, list):
        bio = []

    return ProfileOut(
        name=row.name or "",
        role=row.role or "",
        photo=row.photo or "",
        bio=bio,
        hero_text=row.hero_text or "",
        email=row.email or "",
        location=row.location or "",
        address=row.address or "",
        timezone=row.timezone or "",
        telegram=row.telegram or "",
        github=row.github or "",
        behance=row.behance or "",
        updated_at=row.updated_at or "",
        skills=_load_skills(db),
        testimonials=_load_testimonials(db),
        projects=_load_projects(db),
    )
