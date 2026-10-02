#!/usr/bin/env python3
"""Send a command to the VoxelCraft debug console and wait for it to run.

The game polls debug_commands.txt once per tick and appends results to
debug_results.txt. Torn reads happen when the poller catches the file
mid-append, so this tool waits for a fresh [OK]/[ERROR] line and retries
the append if nothing shows up in time.

Usage: python send_cmd.py "<command>" [timeout_seconds]
"""
import pathlib
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent
CMDS = ROOT / "debug_commands.txt"
RES = ROOT / "debug_results.txt"


def read_results():
    try:
        return RES.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def main():
    if len(sys.argv) < 2:
        print("usage: send_cmd.py <command> [timeout_s]")
        return 2
    cmd = sys.argv[1]
    timeout = float(sys.argv[2]) if len(sys.argv) > 2 else 20.0
    token = cmd.split()[0]

    before = read_results()
    deadline = time.time() + timeout
    attempt = 0
    try:
        while time.time() < deadline:
            # Append every attempt including the first: the game reads the
            # inbox incrementally from its own offset, so a rewrite would
            # land behind it and never be picked up. Torn appends are fine
            # now - the game retries a short read on the next tick.
            with CMDS.open("a", encoding="utf-8") as f:
                f.write(cmd + "\n")
            attempt += 1
            time.sleep(1.0)
            new = read_results()[len(before):]
            for line in new.splitlines():
                s = line.strip()
                if s.startswith("[OK]") or s.startswith("[ERROR]"):
                    if token in s:
                        print(s)
                        return 0 if s.startswith("[OK]") else 1
    finally:
        # Leave an empty inbox: the game skips everything present at its
        # first sight after boot, but leftovers would still be replayed by
        # the currently running instance and confuse the next command
        try:
            CMDS.write_text("", encoding="utf-8")
        except OSError:
            pass
    print(f"TIMEOUT: no [OK]/[ERROR] for '{cmd}' after {timeout}s "
          f"({attempt} appends) - is the game running?")
    return 3


if __name__ == "__main__":
    sys.exit(main())
