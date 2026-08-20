#!/usr/bin/env python3
"""
Запустить все скрипты генерации текстур жителей.

Использование:
    python run_all.py
"""

import subprocess
import sys
import os

def run_script(script_name):
    """Запустить один скрипт."""
    script_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), script_name)
    print(f"\n{'─' * 60}")
    print(f"  Запуск: {script_name}")
    print(f"{'─' * 60}\n")

    result = subprocess.run([sys.executable, script_path], capture_output=False)
    if result.returncode != 0:
        print(f"\n❌ ОШИБКА в {script_name}!")
        return False
    return True


def check_dependencies():
    """Проверить зависимости."""
    try:
        import PIL
        print(f"✅ Pillow {PIL.__version__}")
        return True
    except ImportError:
        print("❌ Pillow не установлен!")
        print("   Установите: pip install Pillow")
        return False


def main():
    print("╔══════════════════════════════════════════════════════════╗")
    print("║  ГЕНЕРАЦИЯ ТЕКСТУР ЖИТЕЛЕЙ ДЕРЕВЕНЬ                     ║")
    print("║  Village Villager Texture Generation                     ║")
    print("╚══════════════════════════════════════════════════════════╝")
    print()

    if not check_dependencies():
        sys.exit(1)

    scripts = [
        "generate_villager_numpy.py",
    ]

    success = True
    for script in scripts:
        if not run_script(script):
            success = False

    print()
    print("╔══════════════════════════════════════════════════════════╗")
    if success:
        print("║  ✅ ВСЕ ТЕКСТУРЫ УСПЕШНО СОЗДАНЫ!                       ║")
        print("║  Запустите игру чтобы увидеть жителей в деревнях!      ║")
    else:
        print("║  ⚠️  НЕКОТОРЫЕ СКРИПТЫ ЗАВЕРШИЛИСЬ С ОШИБКОЙ            ║")
    print("╚══════════════════════════════════════════════════════════╝")

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
