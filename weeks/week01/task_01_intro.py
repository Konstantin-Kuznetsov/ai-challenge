from pathlib import Path
import sys

if str(Path(__file__).resolve().parents[2]) not in sys.path:
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from src.common.utils import banner


def main() -> None:
    print(banner("Week 01 / Task 01"))
    print("AI challenge project is ready.")


if __name__ == "__main__":
    main()
