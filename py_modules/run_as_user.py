import os


def user_env(runtime_dir):
    env = dict(os.environ)
    if runtime_dir:
        env["XDG_RUNTIME_DIR"] = runtime_dir
    return env


def user_cred(uid, gid):
    if uid is None:
        return {}
    return {"user": uid, "group": gid}
