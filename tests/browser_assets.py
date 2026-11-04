"""Serve only known, public UI assets to isolated browser tests."""
from pathlib import Path
from urllib.parse import urlsplit

ASSETS = Path(__file__).resolve().parents[1] / "custom_components/home_assistant_tv"


def serve_localization(route):
    path = urlsplit(route.request.url).path
    if path == "/home_assistant_tv/brand-icon.svg":
        route.fulfill(body=(ASSETS / "brand/icon.svg").read_text(), content_type="image/svg+xml")
        return True
    if path not in {"/home_assistant_tv/localize.js", "/home_assistant_tv/ui-catalog.js"}:
        return False
    route.fulfill(body=(ASSETS / path.rsplit("/", 1)[1]).read_text(), content_type="application/javascript")
    return True
