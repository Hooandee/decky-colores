import os

from py_modules.thermal import apu_temperature, temperature_available


def _hwmon(root, name, chip_name, temp1=None):
    path = os.path.join(root, name)
    os.makedirs(path)
    with open(os.path.join(path, "name"), "w") as handle:
        handle.write(chip_name + "\n")
    if temp1 is not None:
        with open(os.path.join(path, "temp1_input"), "w") as handle:
            handle.write(str(temp1) + "\n")


def _zone(root, name, temp, ztype=None):
    path = os.path.join(root, name)
    os.makedirs(path)
    with open(os.path.join(path, "temp"), "w") as handle:
        handle.write(str(temp) + "\n")
    if ztype is not None:
        with open(os.path.join(path, "type"), "w") as handle:
            handle.write(ztype + "\n")


def test_reads_k10temp_in_celsius(tmp_path):
    hw = str(tmp_path / "hwmon")
    _hwmon(hw, "hwmon0", "k10temp", 72000)
    assert apu_temperature(hw, str(tmp_path / "thermal")) == 72.0


def test_prefers_k10temp_over_amdgpu(tmp_path):
    hw = str(tmp_path / "hwmon")
    _hwmon(hw, "hwmon0", "amdgpu", 55000)
    _hwmon(hw, "hwmon1", "k10temp", 80000)
    assert apu_temperature(hw, str(tmp_path / "thermal")) == 80.0


def test_reads_intel_coretemp(tmp_path):
    hw = str(tmp_path / "hwmon")
    _hwmon(hw, "hwmon0", "coretemp", 61000)
    assert apu_temperature(hw, str(tmp_path / "thermal")) == 61.0


def test_falls_back_to_thermal_zone(tmp_path):
    hw = str(tmp_path / "hwmon")
    os.makedirs(hw)
    th = str(tmp_path / "thermal")
    _zone(th, "thermal_zone0", 48000, "acpitz")
    _zone(th, "thermal_zone1", 66000, "x86_pkg_temp")
    # x86_pkg_temp is preferred even when another zone is hotter/cooler.
    assert apu_temperature(hw, th) == 66.0


def test_thermal_zone_hottest_when_no_pkg(tmp_path):
    hw = str(tmp_path / "hwmon")
    os.makedirs(hw)
    th = str(tmp_path / "thermal")
    _zone(th, "thermal_zone0", 40000, "acpitz")
    _zone(th, "thermal_zone1", 52000, "acpitz")
    assert apu_temperature(hw, th) == 52.0


def test_none_when_nothing_readable(tmp_path):
    hw = str(tmp_path / "hwmon")
    _hwmon(hw, "hwmon0", "nvme")  # a chip with no temp1_input
    assert apu_temperature(hw, str(tmp_path / "thermal")) is None
    assert temperature_available(hw, str(tmp_path / "thermal")) is False


def test_available_true_when_sensor_present(tmp_path):
    hw = str(tmp_path / "hwmon")
    _hwmon(hw, "hwmon0", "k10temp", 50000)
    assert temperature_available(hw, str(tmp_path / "thermal")) is True


def test_missing_roots_return_none(tmp_path):
    assert apu_temperature(str(tmp_path / "no_hw"), str(tmp_path / "no_th")) is None
