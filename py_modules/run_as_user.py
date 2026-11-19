import os


def user_env(runtime_dir):
    # Host programs must not load Decky's bundled libraries (its libpython clashes with a
    # host Python of the same version), so the library path from before Decky comes back.
    env = dict(os.environ)
    original = env.pop("LD_LIBRARY_PATH_ORIG", None)
    if original:
        env["LD_LIBRARY_PATH"] = original
    else:
        env.pop("LD_LIBRARY_PATH", None)
    env.pop("LD_PRELOAD", None)
    if runtime_dir:
        env["XDG_RUNTIME_DIR"] = runtime_dir
    return env


def user_cred(uid, gid):
    if uid is None:
        return {}
    return {"user": uid, "group": gid}
