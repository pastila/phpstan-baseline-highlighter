#!/usr/bin/env python3
"""Refresh the offline identifier allowlist from PHPStan's public documentation.

Only identifier names are copied. JavaScript is parsed as text, never executed;
any unexpected site layout aborts before the existing snapshot is written.
"""
import re
from datetime import date
from pathlib import Path
from urllib.parse import urljoin, urlparse
from urllib.request import urlopen

ROOT = "https://phpstan.org/error-identifiers"


def fetch(url):
    if urlparse(url).netloc != "phpstan.org" or urlparse(url).scheme != "https":
        raise ValueError("Expected an HTTPS PHPStan documentation URL")
    with urlopen(url, timeout=30) as response:
        return response.read().decode("utf-8")


html = fetch(ROOT)
entry = re.search(r'<script type="module" crossorigin src="([^"]+)"', html)
if not entry:
    raise ValueError("Cannot find the error identifier entry point")
entry_url = urljoin(ROOT, entry[1])
module = fetch(entry_url)
search = re.search(r'["\']([^"\']*ErrorIdentifierSearch[^"\']*\.js)["\']', module)
if not search:
    raise ValueError("Cannot find the identifier search data")
data_url = urljoin(entry_url, search[1])
data = fetch(data_url)
keys = re.search(r'Object\.keys\((\{.*?\})\)', data, re.S)
if not keys:
    raise ValueError("Cannot find the identifier catalog")
identifiers = sorted(set(re.findall(r'"([A-Za-z0-9]+(?:\.[A-Za-z0-9]+)*)":\{', keys[1])))
if len(identifiers) < 500 or "missingType.iterableValue" not in identifiers:
    raise ValueError("Unexpected identifier catalog; snapshot was not modified")
target = Path(__file__).resolve().parents[1] / "src/main/resources/documentation/phpstan-identifiers.txt"
target.write_text(f"# Source: {ROOT}\n# Retrieved: {date.today().isoformat()}\n" + "\n".join(identifiers) + "\n")
print(f"Saved {len(identifiers)} identifiers to {target}")
