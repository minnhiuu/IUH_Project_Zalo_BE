from __future__ import annotations

import os
import subprocess
import sys


if __name__ == "__main__":
    host = os.getenv("HOST", "0.0.0.0")
    port = os.getenv("PORT", "8091")
    reload_enabled = os.getenv("RELOAD", "true").lower() in {"1", "true", "yes"}

    command = [
        "uv",
        "run",
        "uvicorn",
        "app.main:app",
        "--host",
        host,
        "--port",
        str(port),
    ]

    if reload_enabled:
        command.append("--reload")

    raise SystemExit(subprocess.call(command, shell=False))
