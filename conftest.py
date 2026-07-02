import logging
import os
import sys
import types

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "py_modules"))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "py_modules", "huesync"))

# The decky runtime module only exists on-device. Stub it so backend modules that
# import it (e.g. updater.py) are importable under pytest.
if "decky" not in sys.modules:
    decky_stub = types.ModuleType("decky")
    decky_stub.logger = logging.getLogger("decky")
    sys.modules["decky"] = decky_stub
