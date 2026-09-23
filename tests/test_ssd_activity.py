import asyncio

import pytest

from ssd_activity import ActivityPulse, SsdActivity, active

pytest_plugins = ("test_main_profiles",)


def test_samples_only_physical_nonrotating_disks(tmp_path):
    sys_block = tmp_path / "block"
    sys_block.mkdir()
    for name, rotational in (("nvme0n1", "0"), ("sda", "1"), ("zram0", "0")):
        queue = sys_block / name / "queue"
        queue.mkdir(parents=True)
        (queue / "rotational").write_text(rotational)
    diskstats = tmp_path / "diskstats"
    sampler = SsdActivity(sys_block, diskstats)

    diskstats.write_text(
        "259 0 nvme0n1 1 0 10 0 1 0 20 0\n"
        "8 0 sda 1 0 10 0 1 0 20 0\n"
        "252 0 zram0 1 0 10 0 1 0 20 0\n"
    )
    assert sampler.sample() == (0, 0)
    diskstats.write_text(
        "259 0 nvme0n1 1 0 14 0 1 0 28 0\n"
        "8 0 sda 1 0 100 0 1 0 200 0\n"
        "252 0 zram0 1 0 100 0 1 0 200 0\n"
    )
    assert sampler.sample() == (4, 8)
    assert active(4, 8, "read", 100)
    assert active(4, 8, "write", 100)
    assert not active(4, 8, "both", 0)


def test_mode_restores_power_led_and_rejects_invalid_settings(profile_plugin):
    plugin = profile_plugin
    writes = []

    class Led:
        def set(self, off):
            writes.append(off)
            return True

    plugin._power_led = Led()
    plugin._capabilities["powerLed"] = True
    plugin._capabilities["powerLedSeparateStates"] = False

    async def run():
        with pytest.raises(ValueError):
            await plugin.set_ssd_activity_led(True, "invalid", 50)
        await plugin.set_ssd_activity_led(True, "both", 60)
        assert plugin._settings["ssd_activity_led"]
        await asyncio.sleep(0)
        await plugin.set_ssd_activity_led(False, "both", 60)
        await asyncio.sleep(0)

    asyncio.run(run())
    assert writes[-1] is False
    assert plugin._ssd_task is None


def test_short_bursts_and_sustained_io_both_flicker():
    pulse = ActivityPulse()
    assert pulse.update(True, 1.00)
    assert pulse.update(False, 1.05)
    assert not pulse.update(False, 1.10)
    assert not pulse.update(False, 1.15)

    # IO while lit queues only one further flash after the dark gap.
    pulse = ActivityPulse()
    assert pulse.update(True, 2.00)
    assert pulse.update(True, 2.05)
    assert not pulse.update(True, 2.10)
    assert not pulse.update(False, 2.14)
    assert pulse.update(False, 2.15)
    assert not pulse.update(False, 2.25)
    assert not pulse.update(False, 2.30)
