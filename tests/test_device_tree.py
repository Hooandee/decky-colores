import os

from py_modules import device_tree


def _write(root, rel, data, mode="w"):
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, mode) as handle:
        handle.write(data)


def _thor(root):
    _write(root, "proc/device-tree/model", b"AYN Thor\0", "wb")
    _write(root, "proc/device-tree/compatible", b"ayn,thor\0qcom,sm8550\0", "wb")
    _write(root, "sys/devices/soc0/family", "Snapdragon\n")
    _write(root, "sys/devices/soc0/machine", "SM8550\n")


def test_thor_under_fex_is_arm_without_dmi(tmp_path):
    root = str(tmp_path)
    _thor(root)
    assert device_tree.is_arm(root)
    assert device_tree.model(root) == "AYN Thor"
    assert device_tree.compatible(root) == ["ayn,thor", "qcom,sm8550"]
    assert device_tree.soc(root) == "Snapdragon SM8550"


def test_midr_marks_arm_even_with_dmi(tmp_path):
    root = str(tmp_path)
    _write(root, "sys/class/dmi/id/sys_vendor", "Vendor\n")
    _write(root, "sys/devices/system/cpu/cpu0/regs/identification/midr_el1", "0x41\n")
    assert device_tree.is_arm(root)


def test_x86_with_dmi_is_not_arm(tmp_path):
    root = str(tmp_path)
    _write(root, "sys/class/dmi/id/sys_vendor", "ASUSTeK\n")
    assert not device_tree.is_arm(root)
    assert device_tree.model(root) == ""
    assert device_tree.soc(root) is None


def test_host_os_release_wins_over_emulated_guest(tmp_path):
    root = str(tmp_path)
    _write(root, "etc/os-release", 'NAME="Arch Linux"\n')
    _write(root, "proc/self/root/etc/os-release", 'PRETTY_NAME="Armada OS"\nNAME=Armada\n')
    assert device_tree.host_os_release(root)["PRETTY_NAME"] == "Armada OS"


def test_host_os_release_falls_back_to_etc(tmp_path):
    root = str(tmp_path)
    _write(root, "etc/os-release", 'PRETTY_NAME="SteamOS"\n')
    assert device_tree.host_os_release(root) == {"PRETTY_NAME": "SteamOS"}
    assert device_tree.host_os_release(str(tmp_path / "missing")) == {}
