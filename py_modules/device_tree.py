import os

# Decky runs under x86 emulation (FEX) on ARM handhelds, so os.uname() and /proc/cpuinfo
# report an x86 CPU there. These kernel interfaces are not emulated.
_MIDR = "sys/devices/system/cpu/cpu0/regs/identification/midr_el1"
_DT = "proc/device-tree"
_SOC = "sys/devices/soc0"
# FEX serves the x86 guest rootfs for paths that exist in both; /proc/self/root is the host.
_HOST_ROOT = "proc/self/root"


def _read_bytes(path):
    try:
        with open(path, "rb") as handle:
            return handle.read()
    except OSError:
        return b""


def _read_text(path):
    return _read_bytes(path).decode("utf-8", "replace").strip("\0").strip()


def model(root="/"):
    return _read_text(os.path.join(root, _DT, "model"))


def compatible(root="/"):
    raw = _read_bytes(os.path.join(root, _DT, "compatible"))
    return [item for item in raw.decode("utf-8", "replace").split("\0") if item]


def is_arm(root="/"):
    if os.path.exists(os.path.join(root, _MIDR)):
        return True
    has_dmi = bool(_read_text(os.path.join(root, "sys/class/dmi/id/sys_vendor")))
    return not has_dmi and bool(compatible(root))


def soc(root="/"):
    family = _read_text(os.path.join(root, _SOC, "family"))
    machine = _read_text(os.path.join(root, _SOC, "machine"))
    return " ".join(part for part in (family, machine) if part) or None


def host_roots(root="/"):
    return (os.path.join(root, _HOST_ROOT), root)


def host_os_release(root="/"):
    for base in host_roots(root):
        try:
            with open(os.path.join(base, "etc/os-release")) as handle:
                release = {}
                for line in handle:
                    key, sep, value = line.rstrip("\n").partition("=")
                    if sep:
                        release[key] = value.strip('"')
                return release
        except OSError:
            continue
    return {}
