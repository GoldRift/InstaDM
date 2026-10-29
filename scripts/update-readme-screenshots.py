#!/usr/bin/env python3
"""Regenerate the screenshot gallery between the README markers."""
from __future__ import annotations

import html
import os
import sys
from pathlib import Path

START = "<!-- demo-screenshots:start -->"
END = "<!-- demo-screenshots:end -->"

GROUPS = [
    ("light", "Light", 4, 220),
    ("dark", "Dark", 4, 220),
    ("two-pane", "Two-pane", 2, 430),
]

CAPTIONS = {
    "01-inbox-light": "Inbox",
    "02-chat-light": "Chat",
    "03-media-viewer-light": "Media viewer",
    "04-actions-light": "Message actions",
    "05-voice-light": "Voice note",
    "06-echo-light": "Send and reply",
    "07-edit-light": "Edit",
    "08-unsend-light": "Unsend",
    "09-link-and-voice-light": "Links and voice",
    "10-group-light": "Group chat",
    "11-media-cards-light": "Video, reel, view-once",
    "01-inbox-dark": "Inbox (dark)",
    "02-chat-dark": "Chat (dark)",
    "03-media-viewer-dark": "Media viewer (dark)",
    "04-actions-dark": "Message actions (dark)",
    "05-group-dark": "Group chat (dark)",
    "01-two-pane-empty-light": "Two-pane inbox",
    "02-two-pane-chat-light": "Two-pane chat",
}


def caption(stem: str) -> str:
    if stem in CAPTIONS:
        return CAPTIONS[stem]
    words = stem.split("-")
    words = [word for word in words if not word.isdigit() and word not in {"light", "dark"}]
    return " ".join(words).capitalize()


def gallery(images: list[Path], root: Path, width: int, columns: int) -> str:
    rows = []
    for index in range(0, len(images), columns):
        cells = []
        for image in images[index:index + columns]:
            label = html.escape(caption(image.stem))
            src = os.path.relpath(image, root).replace(os.sep, "/")
            cells.append(
                f'<td align="center"><img src="{src}" width="{width}" alt="{label}">'
                f"<br><sub>{label}</sub></td>"
            )
        while len(cells) < columns:
            cells.append("<td></td>")
        rows.append("<tr>\n      " + "\n      ".join(cells) + "\n    </tr>")
    return "<table>\n    " + "\n    ".join(rows) + "\n</table>"


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: update-readme-screenshots.py <screenshots-dir> <README>", file=sys.stderr)
        return 2

    screenshots = Path(sys.argv[1])
    readme = Path(sys.argv[2])
    sections = []
    for folder, title, columns, width in GROUPS:
        images = sorted(
            image for image in (screenshots / folder).glob("*.png")
            if image.is_file() and image.stat().st_size > 0
        )
        if images:
            sections.append(f"### {title}\n\n{gallery(images, readme.parent, width, columns)}")
    if not sections:
        print(f"no screenshots found under {screenshots}", file=sys.stderr)
        return 1

    text = readme.read_text(encoding="utf-8")
    if START not in text or END not in text:
        print(f"missing {START} / {END} markers in {readme}", file=sys.stderr)
        return 1

    head, rest = text.split(START, 1)
    _, tail = rest.split(END, 1)
    block = f"{START}\n\n" + "\n\n".join(sections) + f"\n\n{END}"
    readme.write_text(head + block + tail, encoding="utf-8")
    print(f"updated {readme} with {sum(len(list((screenshots / g[0]).glob('*.png'))) for g in GROUPS)} images")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
