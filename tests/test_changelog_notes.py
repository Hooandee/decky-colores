import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "changelog_notes.py"


def _run(tmp_path: Path, mode: str, changelog: str) -> subprocess.CompletedProcess[str]:
    (tmp_path / "CHANGELOG.md").write_text(changelog, encoding="utf-8")
    return subprocess.run(
        [sys.executable, str(SCRIPT), mode],
        cwd=tmp_path,
        text=True,
        capture_output=True,
        check=False,
    )


def test_check_accepts_all_languages_with_an_unlinked_version_heading(tmp_path):
    changelog = """# Changelog

## 0.24.0 (2026-09-07)

### Español

* Mejora la iluminación.

### English

* Improves lighting.

### Italiano

* Migliora l'illuminazione.

### Deutsch

* Verbessert die Beleuchtung.
"""

    result = _run(tmp_path, "--check", changelog)

    assert result.returncode == 0, result.stdout + result.stderr
    assert "quadrilingual" in result.stdout


def test_check_requires_german_for_every_new_english_entry(tmp_path):
    changelog = """# Changelog

## [0.24.0](https://example.test/0.24.0)

* **ES:** Mejora la iluminación.
* **EN:** Improves lighting.
* **IT:** Migliora l'illuminazione.
"""

    result = _run(tmp_path, "--check", changelog)

    assert result.returncode == 1
    assert "German (**DE:**)" in result.stdout


def test_check_requires_italian_for_every_new_english_entry(tmp_path):
    changelog = """# Changelog

## [0.24.0](https://example.test/0.24.0)

* **ES:** Mejora la iluminación.
* **EN:** Improves lighting.
* **DE:** Verbessert die Beleuchtung.
"""

    result = _run(tmp_path, "--check", changelog)

    assert result.returncode == 1
    assert "Italian (**IT:**)" in result.stdout


def test_release_body_emits_four_sections_and_strips_links(tmp_path):
    changelog = """# Changelog

## [0.24.0](https://example.test/0.24.0)

* **ES:** Mejora la iluminación. ([#123](https://example.test/123))
* **EN:** Improves lighting. ([#123](https://example.test/123))
* **IT:** Migliora l'illuminazione. ([#123](https://example.test/123))
* **DE:** Verbessert die Beleuchtung. ([#123](https://example.test/123))
"""

    result = _run(tmp_path, "--release-body", changelog)

    assert result.returncode == 0, result.stdout + result.stderr
    assert result.stdout == """### Novedades

- Mejora la iluminación.

### What's new

- Improves lighting.

### Novità

- Migliora l'illuminazione.

### Neuigkeiten

- Verbessert die Beleuchtung.
"""


def test_release_body_rejects_incomplete_translations(tmp_path):
    changelog = """# Changelog

## 0.24.0 (2026-09-07)

* **ES:** Mejora la iluminación.
* **EN:** Improves lighting.
* **IT:** Migliora l'illuminazione.
"""

    result = _run(tmp_path, "--release-body", changelog)

    assert result.returncode == 1
    assert "German (**DE:**)" in result.stderr


def test_check_ignores_older_sections_that_predate_german(tmp_path):
    changelog = """# Changelog

## 0.24.0 (2026-09-07)

* **ES:** Mejora la iluminación.
* **EN:** Improves lighting.
* **IT:** Migliora l'illuminazione.
* **DE:** Verbessert die Beleuchtung.

## 0.23.0 (2026-08-09)

* **ES:** Entrada histórica.
* **EN:** Historical entry.
"""

    result = _run(tmp_path, "--check", changelog)

    assert result.returncode == 0, result.stdout + result.stderr


def test_check_rejects_unknown_language_labels(tmp_path):
    changelog = """# Changelog

## 0.24.0 (2026-09-07)

* **ES:** Mejora la iluminación.
* **EN:** Improves lighting.
* **IT:** Migliora l'illuminazione.
* **DE:** Verbessert die Beleuchtung.
* **FR:** Améliore l'éclairage.
"""

    result = _run(tmp_path, "--check", changelog)

    assert result.returncode == 1
    assert "unsupported language label: FR" in result.stdout
