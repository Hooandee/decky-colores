import decky

from py_modules.version import read_version


class Plugin:
    async def get_version(self) -> str:
        return read_version()

    async def _main(self):
        decky.logger.info("Colores loaded (v%s)", read_version())

    async def _unload(self):
        decky.logger.info("Colores unloaded")

    async def _uninstall(self):
        decky.logger.info("Colores uninstalled")
