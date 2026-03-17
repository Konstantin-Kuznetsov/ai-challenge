from datetime import datetime


def banner(title: str) -> str:
    """Return a simple timestamped banner for task logs."""
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    return f"[{now}] {title}"
