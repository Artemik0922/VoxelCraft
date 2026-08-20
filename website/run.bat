@echo off
rem Запуск портфолио: FastAPI раздаёт и сайт, и API на http://127.0.0.1:8000
cd /d "%~dp0backend"
pip install -r requirements.txt >nul 2>&1
python -m uvicorn app.main:app --reload --port 8000
