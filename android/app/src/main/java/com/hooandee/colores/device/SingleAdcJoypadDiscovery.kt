package com.hooandee.colores.device

import com.hooandee.colores.led.FileSysfsAccess
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsAccess

object SingleAdcJoypadDiscovery {
    fun scan(
        basePath: String = SingleAdcJoypadDescriptor.DEFAULT_BASE_PATH,
        access: SysfsAccess = FileSysfsAccess,
    ): SingleAdcJoypadDescriptor? {
        val color = "$basePath/custum_rgb_r"
        val commit = "$basePath/led_set"
        if (!access.exists(color) || !access.canWrite(color) || !access.canWrite(commit)) return null
        return SingleAdcJoypadDescriptor(basePath)
    }
}
