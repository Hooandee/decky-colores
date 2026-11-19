from run_as_user import user_env


def test_user_env_drops_deckys_bundled_libraries(monkeypatch):
    # Decky's frozen backend points LD_LIBRARY_PATH at its own libpython; a host Python of
    # the same version (Python 3.14 on Anatase) then loads it and cannot find its stdlib.
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEI0000061aYZH4r8")
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)
    monkeypatch.setenv("LD_PRELOAD", "/tmp/_MEI0000061aYZH4r8/libx.so")
    env = user_env("/run/user/1000")
    assert "LD_LIBRARY_PATH" not in env
    assert "LD_PRELOAD" not in env
    assert env["XDG_RUNTIME_DIR"] == "/run/user/1000"


def test_user_env_restores_the_library_path_from_before_decky(monkeypatch):
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEI0000061aYZH4r8")
    monkeypatch.setenv("LD_LIBRARY_PATH_ORIG", "/opt/lib")
    env = user_env(None)
    assert env["LD_LIBRARY_PATH"] == "/opt/lib"
    assert "LD_LIBRARY_PATH_ORIG" not in env
