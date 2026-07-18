import os

from py_modules.performance import gpu_busy_percent, CpuSampler, performance_available


def _make_gpu(root, card, value):
    dev = os.path.join(root, card, "device")
    os.makedirs(dev)
    open(os.path.join(dev, "gpu_busy_percent"), "w").write(value)


def test_gpu_busy_reads_first_card(tmp_path):
    _make_gpu(str(tmp_path), "card0", "42\n")
    assert gpu_busy_percent(str(tmp_path)) == 42


def test_gpu_busy_clamps(tmp_path):
    _make_gpu(str(tmp_path), "card0", "250")
    assert gpu_busy_percent(str(tmp_path)) == 100


def test_gpu_busy_none_when_absent(tmp_path):
    os.makedirs(os.path.join(str(tmp_path), "card0"))
    assert gpu_busy_percent(str(tmp_path)) is None


def _write_stat(path, idle, busy):
    # cpu  user nice system idle iowait irq softirq
    open(path, "w").write(f"cpu {busy} 0 0 {idle} 0 0 0\n")


def test_cpu_sampler_needs_two_samples(tmp_path):
    stat = os.path.join(str(tmp_path), "stat")
    _write_stat(stat, idle=100, busy=0)
    sampler = CpuSampler(stat)
    assert sampler.percent() is None  # first call primes


def test_cpu_sampler_computes_load(tmp_path):
    stat = os.path.join(str(tmp_path), "stat")
    _write_stat(stat, idle=100, busy=0)
    sampler = CpuSampler(stat)
    sampler.percent()
    # +30 busy, +10 idle -> 30 of 40 ticks busy = 75%
    _write_stat(stat, idle=110, busy=30)
    assert sampler.percent() == 75


def test_performance_available_true_when_stat_present(tmp_path):
    stat = os.path.join(str(tmp_path), "stat")
    _write_stat(stat, 100, 0)
    assert performance_available(os.path.join(str(tmp_path), "drm"), stat) is True
