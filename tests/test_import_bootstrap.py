import os
from pathlib import Path
import subprocess
import sys
import textwrap


ROOT = Path(__file__).resolve().parents[1]


def test_plugin_prefers_bundled_modules_over_system_name_collision(tmp_path):
    system_package = tmp_path / "site-packages" / "report"
    system_package.mkdir(parents=True)
    (system_package / "__init__.py").write_text(
        'raise RuntimeError("system report package imported")\n'
    )
    script = textwrap.dedent(
        f"""
        import pathlib
        import sys
        import types

        decky = types.ModuleType("decky")
        sys.modules["decky"] = decky
        sys.path.append({str(ROOT / "py_modules")!r})
        sys.path.insert(0, {str(system_package.parent)!r})

        import main

        bundled = pathlib.Path({str(ROOT / "py_modules")!r}).resolve()
        loaded = pathlib.Path(main.report_collector.__file__).resolve()
        assert bundled in loaded.parents, loaded
        assert main.report_collector.__name__ == "colores_report.collector"
        """
    )
    environment = os.environ.copy()
    environment.pop("PYTHONPATH", None)

    result = subprocess.run(
        [sys.executable, "-c", script],
        cwd=ROOT,
        env=environment,
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 0, result.stderr
