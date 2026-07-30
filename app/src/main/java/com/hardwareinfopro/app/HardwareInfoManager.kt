package com.hardwareinfopro.app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale

/**
 * 硬件信息采集管理器 - 采集设备所有可获取的硬件信息
 */
@SuppressLint("HardwareIds", "MissingPermission")
class HardwareInfoManager(private val context: Context) {

    fun collectAllInfo(): List<InfoSection> {
        val sections = mutableListOf<InfoSection>()
        sections.add(InfoSection(InfoCategory.SYSTEM, getSystemInfo()))
        sections.add(InfoSection(InfoCategory.CPU, getCpuInfo()))
        sections.add(InfoSection(InfoCategory.GPU, getGpuInfo()))
        sections.add(InfoSection(InfoCategory.MEMORY, getMemoryInfo()))
        sections.add(InfoSection(InfoCategory.BATTERY, getBatteryInfo()))
        sections.add(InfoSection(InfoCategory.USB, getUsbInfo()))
        sections.add(InfoSection(InfoCategory.SCREEN, getScreenInfo()))
        sections.add(InfoSection(InfoCategory.WIFI, getWifiInfo()))
        sections.add(InfoSection(InfoCategory.NETWORK, getNetworkInfo()))
        sections.add(InfoSection(InfoCategory.BLUETOOTH, getBluetoothInfo()))
        sections.add(InfoSection(InfoCategory.SENSOR, getSensorInfo()))
        sections.add(InfoSection(InfoCategory.CAMERA, getCameraInfo()))
        sections.add(InfoSection(InfoCategory.AUDIO, getAudioInfo()))
        sections.add(InfoSection(InfoCategory.NFC, getNfcInfo()))
        return sections.filter { it.items.isNotEmpty() }
    }

    // ==================== 系统信息 ====================
    private fun getSystemInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        items.add(InfoItem("设备品牌", Build.BRAND))
        items.add(InfoItem("设备型号", Build.MODEL))
        items.add(InfoItem("设备名称", Build.DEVICE))
        items.add(InfoItem("主板型号", Build.BOARD))
        items.add(InfoItem("硬件名称", Build.HARDWARE))
        items.add(InfoItem("Android 版本", Build.VERSION.RELEASE))
        items.add(InfoItem("API 级别", Build.VERSION.SDK_INT.toString()))
        items.add(InfoItem("安全补丁", Build.VERSION.SECURITY_PATCH))
        items.add(InfoItem("构建编号", Build.DISPLAY))
        items.add(InfoItem("内核版本", getKernelVersion()))
        items.add(InfoItem("系统架构", getSystemArch()))
        items.add(InfoItem("Bootloader", Build.BOOTLOADER))
        items.add(InfoItem("基带版本", getBasebandVersion()))
        items.add(InfoItem("Java 虚拟机", System.getProperty("java.vm.name") ?: "未知"))
        items.add(InfoItem("OpenGL ES 版本", getGlEsVersion()))
        items.add(InfoItem("系统语言", Locale.getDefault().displayName))
        items.add(InfoItem("系统时区", java.util.TimeZone.getDefault().displayName))
        items.add(InfoItem("开机时间", getUptime()))
        items.add(InfoItem("是否 Root", if (isRooted()) "是" else "否"))
        return items
    }

    private fun getKernelVersion(): String {
        return try {
            val process = Runtime.getRuntime().exec("uname -r")
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            System.getProperty("os.version") ?: "未知"
        }
    }

    private fun getSystemArch(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.joinToString(", ")
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI
        }
    }

    private fun getBasebandVersion(): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, "gsm.version.baseband", "未知") as String
        } catch (e: Exception) {
            "未知"
        }
    }

    private fun getGlEsVersion(): String {
        return try {
            val pm = context.packageManager
            pm.systemAvailableFeatures.firstOrNull { it.isGLFeature }?.glEsVersion ?: "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    private fun getUptime(): String {
        val uptimeMs = android.os.SystemClock.elapsedRealtime()
        val seconds = uptimeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return "${days}天 ${hours % 24}小时 ${minutes % 60}分钟"
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su")
        return paths.any { File(it).exists() }
    }

    // ==================== CPU 信息 ====================
    private fun getCpuInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val cpuInfo = readCpuInfo()

        // CPU 型号
        val hardware = cpuInfo["Hardware"] ?: ""
        val processor = cpuInfo["Processor"] ?: cpuInfo["model name"] ?: ""
        items.add(InfoItem("CPU 型号", hardware.ifEmpty { processor }.ifEmpty { Build.HARDWARE }))

        // CPU 架构
        items.add(InfoItem("CPU 架构", getSystemArch()))

        // 核心数
        val coreCount = getCpuCoreCount()
        items.add(InfoItem("CPU 核心数", "$coreCount 核"))

        // CPU 频率信息
        val maxFreq = readFromFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
        val minFreq = readFromFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq")
        if (maxFreq.isNotEmpty()) {
            items.add(InfoItem("最大主频", "${maxFreq.toLongOrNull()?.div(1000) ?: 0} MHz"))
        }
        if (minFreq.isNotEmpty()) {
            items.add(InfoItem("最低主频", "${minFreq.toLongOrNull()?.div(1000) ?: 0} MHz"))
        }

        // 每个核心的频率
        for (i in 0 until coreCount) {
            val curFreq = readFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val coreMaxFreq = readFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            val governor = readFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
            val freqStr = buildString {
                if (curFreq.isNotEmpty()) append("当前 ${curFreq.toLongOrNull()?.div(1000) ?: "?"} MHz")
                if (coreMaxFreq.isNotEmpty()) append(" / 最大 ${coreMaxFreq.toLongOrNull()?.div(1000) ?: "?"} MHz")
                if (governor.isNotEmpty()) append(" [$governor]")
            }
            if (freqStr.isNotEmpty()) {
                items.add(InfoItem("核心 $i", freqStr))
            }
        }

        // CPU 使用率
        val cpuUsage = getCpuUsage()
        items.add(InfoItem("CPU 总使用率", "$cpuUsage%"))

        // CPU Features
        val features = cpuInfo["Features"] ?: cpuInfo["flags"] ?: ""
        if (features.isNotEmpty()) {
            items.add(InfoItem("CPU 特性", features.take(200)))
        }

        // BogoMIPS
        val bogoMips = cpuInfo["BogoMIPS"] ?: ""
        if (bogoMips.isNotEmpty()) {
            items.add(InfoItem("BogoMIPS", bogoMips))
        }

        return items
    }

    private fun readCpuInfo(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/cpuinfo")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split(":", limit = 2)
                    if (parts.size == 2) {
                        map[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return map
    }

    private fun getCpuCoreCount(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            1
        }
    }

    private fun getCpuUsage(): String {
        return try {
            val reader1 = BufferedReader(FileReader("/proc/stat"))
            val load1 = reader1.readLine()
            reader1.close()
            Thread.sleep(300)
            val reader2 = BufferedReader(FileReader("/proc/stat"))
            val load2 = reader2.readLine()
            reader2.close()

            val toks1 = load1.split("\\s+".toRegex())
            val toks2 = load2.split("\\s+".toRegex())

            val idle1 = toks1[4].toLong()
            val idle2 = toks2[4].toLong()
            val total1 = (1..7).sumOf { toks1[it].toLong() }
            val total2 = (1..7).sumOf { toks2[it].toLong() }

            val totalDiff = total2 - total1
            val idleDiff = idle2 - idle1
            if (totalDiff > 0) {
                String.format("%.1f", (totalDiff - idleDiff).toDouble() / totalDiff * 100)
            } else "0.0"
        } catch (e: Exception) {
            "N/A"
        }
    }

    // ==================== GPU 信息 ====================
    private fun getGpuInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()

        // 通过 OpenGL 获取 GPU 渲染器
        val glRenderer = readFromFile("/sys/class/kgsl/kgsl-3d0/gpu_model")
            .ifEmpty { getGpuRendererFromGL() }
        items.add(InfoItem("GPU 型号", glRenderer.ifEmpty { "未知" }))

        // GPU 当前频率
        val gpuFreq = readFromFile("/sys/class/kgsl/kgsl-3d0/gpuclk")
            .ifEmpty { readFromFile("/sys/class/devfreq/soc:qcom,gpu/cur_freq") }
            .ifEmpty { readFromFile("/sys/devices/platform/soc/1c00000.qcom,kgsl-3d0/kgsl/kgsl-3d0/gpuclk") }
        if (gpuFreq.isNotEmpty()) {
            val freqMhz = gpuFreq.toLongOrNull()?.div(1000000) ?: (gpuFreq.toLongOrNull()?.div(1000) ?: 0)
            items.add(InfoItem("GPU 当前频率", "$freqMhz MHz"))
        }

        // GPU 最大频率
        val gpuMaxFreq = readFromFile("/sys/class/kgsl/kgsl-3d0/max_gpuclk")
            .ifEmpty { readFromFile("/sys/class/devfreq/soc:qcom,gpu/max_freq") }
        if (gpuMaxFreq.isNotEmpty()) {
            val freqMhz = gpuMaxFreq.toLongOrNull()?.div(1000000) ?: (gpuMaxFreq.toLongOrNull()?.div(1000) ?: 0)
            items.add(InfoItem("GPU 最大频率", "$freqMhz MHz"))
        }

        // GPU 可用频率列表
        val gpuFreqs = readFromFile("/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies")
            .ifEmpty { readFromFile("/sys/class/devfreq/soc:qcom,gpu/available_frequencies") }
        if (gpuFreqs.isNotEmpty()) {
            val freqList = gpuFreqs.split(" ").mapNotNull {
                it.toLongOrNull()?.let { f -> "${f / 1000000}MHz" }
            }
            items.add(InfoItem("GPU 可用频率", freqList.joinToString(", ").take(200)))
        }

        // GPU Governor
        val gpuGovernor = readFromFile("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
            .ifEmpty { readFromFile("/sys/class/devfreq/soc:qcom,gpu/governor") }
        if (gpuGovernor.isNotEmpty()) {
            items.add(InfoItem("GPU 调度器", gpuGovernor))
        }

        // GPU 使用率 (部分设备支持)
        val gpuUsage = readFromFile("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
            .ifEmpty { readFromFile("/sys/class/devfreq/soc:qcom,gpu/gpu_busy_percentage") }
        if (gpuUsage.isNotEmpty()) {
            items.add(InfoItem("GPU 使用率", "${gpuUsage.trim()}%"))
        }

        // GPU 温度
        val gpuTemp = readFromFile("/sys/class/kgsl/kgsl-3d0/temp")
            .ifEmpty { readFromFile("/sys/class/thermal/thermal_zone10/temp") }
        if (gpuTemp.isNotEmpty()) {
            val temp = gpuTemp.toDoubleOrNull()
            if (temp != null) {
                items.add(InfoItem("GPU 温度", "${if (temp > 100) temp / 1000 else temp}°C"))
            }
        }

        items.add(InfoItem("OpenGL ES 版本", getGlEsVersion()))

        return items
    }

    private fun getGpuRendererFromGL(): String {
        // 尝试通过 ActivityManager 获取 GL 渲染器信息
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val configInfo = am.deviceConfigurationInfo
            val reqGlEsVersion = configInfo.reqGlEsVersion
            val major = (reqGlEsVersion and 0xffff0000.toInt()) shr 16
            val minor = reqGlEsVersion and 0x0000ffff
            "OpenGL ES $major.$minor"
        } catch (e: Exception) {
            "未知"
        }
    }

    // ==================== 内存与存储信息 ====================
    private fun getMemoryInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()

        // RAM 信息
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val usedRam = totalRam - availRam

        items.add(InfoItem("RAM 总容量", formatBytes(totalRam)))
        items.add(InfoItem("RAM 已使用", formatBytes(usedRam)))
        items.add(InfoItem("RAM 可用", formatBytes(availRam)))
        items.add(InfoItem("RAM 使用率", String.format("%.1f%%", usedRam.toDouble() / totalRam * 100)))
        items.add(InfoItem("低内存阈值", formatBytes(memInfo.threshold)))

        // 从 /proc/meminfo 获取更详细的信息
        val memInfoMap = readMemInfo()
        memInfoMap["Buffers"]?.let { items.add(InfoItem("Buffers", it)) }
        memInfoMap["Cached"]?.let { items.add(InfoItem("缓存 (Cached)", it)) }
        memInfoMap["SwapTotal"]?.let { items.add(InfoItem("Swap 总量", it)) }
        memInfoMap["SwapFree"]?.let {
            val swapTotal = memInfoMap["SwapTotal"]?.replace("[^0-9]".toRegex(), "")?.toLongOrNull() ?: 0
            val swapFree = it.replace("[^0-9]".toRegex(), "").toLongOrNull() ?: 0
            items.add(InfoItem("Swap 已使用", formatBytes((swapTotal - swapFree) * 1024)))
        }
        memInfoMap["Zram"]?.let { items.add(InfoItem("ZRam", it)) }

        // ROM / 存储信息
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalStorage = statFs.blockSizeLong * statFs.blockCountLong
        val availStorage = statFs.blockSizeLong * statFs.availableBlocksLong
        val usedStorage = totalStorage - availStorage

        items.add(InfoItem("内部存储 总容量", formatBytes(totalStorage)))
        items.add(InfoItem("内部存储 已使用", formatBytes(usedStorage)))
        items.add(InfoItem("内部存储 可用", formatBytes(availStorage)))
        items.add(InfoItem("内部存储 使用率", String.format("%.1f%%", usedStorage.toDouble() / totalStorage * 100)))

        // 文件系统
        items.add(InfoItem("文件系统类型", getFileSystemType()))

        // 外部存储
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val extStatFs = StatFs(Environment.getExternalStorageDirectory().path)
            val extTotal = extStatFs.blockSizeLong * extStatFs.blockCountLong
            val extAvail = extStatFs.blockSizeLong * extStatFs.availableBlocksLong
            items.add(InfoItem("外部存储 总容量", formatBytes(extTotal)))
            items.add(InfoItem("外部存储 可用", formatBytes(extAvail)))
        }

        return items
    }

    private fun readMemInfo(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/meminfo")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split(":", limit = 2)
                    if (parts.size == 2) {
                        map[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return map
    }

    private fun getFileSystemType(): String {
        return try {
            val process = Runtime.getRuntime().exec("mount")
            val output = process.inputStream.bufferedReader().readText()
            val dataMount = output.lines().firstOrNull { it.contains("/data ") }
            if (dataMount != null) {
                val parts = dataMount.split(" ")
                if (parts.size >= 5) parts[4] else "未知"
            } else "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    // ==================== 电池信息 ====================
    private fun getBatteryInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()

        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)

        if (batteryStatus != null) {
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()
            items.add(InfoItem("电量", String.format("%.0f%%", batteryPct)))

            // 电压
            val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            items.add(InfoItem("电池电压", String.format("%.2f V", voltage / 1000.0)))

            // 温度
            val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            items.add(InfoItem("电池温度", String.format("%.1f°C", temperature / 10.0)))

            // 充电状态
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
                BatteryManager.BATTERY_STATUS_FULL -> "已充满"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
                else -> "未知"
            }
            items.add(InfoItem("充电状态", statusText))

            // 充电方式
            val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val pluggedText = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC 充电器"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
                else -> "未插入"
            }
            items.add(InfoItem("充电方式", pluggedText))

            // 健康状态
            val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthText = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
                BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
                BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
                else -> "未知"
            }
            items.add(InfoItem("电池健康", healthText))

            // 电池技术
            val technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "未知"
            items.add(InfoItem("电池技术", technology))
        }

        // 充放电电流 (通过 BatteryManager API)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (currentNow != Long.MIN_VALUE) {
            val currentMa = currentNow / 1000.0
            val prefix = if (currentMa > 0) "充电" else "放电"
            items.add(InfoItem("${prefix}电流", String.format("%.0f mA", Math.abs(currentMa))))
        }

        // 电池容量 (剩余)
        val chargeCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (chargeCounter != Long.MIN_VALUE) {
            items.add(InfoItem("当前电荷量", "${chargeCounter / 1000} mAh"))
        }

        // 电池剩余容量 (通过 sysfs)
        val chargeFull = readFromFile("/sys/class/power_supply/battery/charge_full_design")
            .ifEmpty { readFromFile("/sys/class/power_supply/battery/charge_full") }
        if (chargeFull.isNotEmpty()) {
            items.add(InfoItem("设计容量", "${chargeFull.toLongOrNull()?.div(1000) ?: 0} mAh"))
        }

        // 电池循环次数
        val cycleCount = readFromFile("/sys/class/power_supply/battery/cycle_count")
        if (cycleCount.isNotEmpty()) {
            items.add(InfoItem("充放电循环次数", cycleCount))
        }

        return items
    }

    // ==================== USB 信息 ====================
    private fun getUsbInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()

        // USB 电压
        val usbVoltage = readFromFile("/sys/class/power_supply/usb/voltage_now")
            .ifEmpty { readFromFile("/sys/class/power_supply/usb/voltage_max") }
        if (usbVoltage.isNotEmpty()) {
            val v = usbVoltage.toDoubleOrNull()
            if (v != null) {
                items.add(InfoItem("USB 电压", String.format("%.2f V", if (v > 100) v / 1000000 else v / 1000)))
            }
        }

        // USB 电流
        val usbCurrent = readFromFile("/sys/class/power_supply/usb/current_now")
            .ifEmpty { readFromFile("/sys/class/power_supply/usb/current_max") }
        if (usbCurrent.isNotEmpty()) {
            val c = usbCurrent.toDoubleOrNull()
            if (c != null) {
                items.add(InfoItem("USB 电流", String.format("%.0f mA", if (c > 10000) c / 1000 else c)))
            }
        }

        // USB 类型
        val usbType = readFromFile("/sys/class/power_supply/usb/usb_type")
            .ifEmpty { readFromFile("/sys/class/power_supply/usb/type") }
        if (usbType.isNotEmpty()) {
            items.add(InfoItem("USB 类型", usbType))
        }

        // USB PD (Power Delivery)
        val usbPd = readFromFile("/sys/class/power_supply/usb/usb_pd")
        if (usbPd.isNotEmpty()) {
            items.add(InfoItem("USB PD", if (usbPd == "1") "支持" else "不支持"))
        }

        // USB OTG
        val otg = readFromFile("/sys/class/power_supply/usb/otg")
        if (otg.isNotEmpty()) {
            items.add(InfoItem("USB OTG", if (otg == "1") "已连接" else "未连接"))
        }

        // USB 快速充电类型
        val fastChargeType = readFromFile("/sys/class/power_supply/usb/fast_charge_mode")
        if (fastChargeType.isNotEmpty()) {
            items.add(InfoItem("快充模式", fastChargeType))
        }

        return items
    }

    // ==================== 屏幕信息 ====================
    private fun getScreenInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()

        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        val widthPx = dm.widthPixels
        val heightPx = dm.heightPixels
        items.add(InfoItem("屏幕分辨率", "${widthPx} x ${heightPx}"))
        items.add(InfoItem("DPI 密度", "${dm.densityDpi} dpi (${getDensityName(dm.densityDpi)})"))
        items.add(InfoItem("缩放密度", String.format("%.2f", dm.density)))
        items.add(InfoItem("缩放密度比例", "${(dm.density * 100).toInt()}%"))

        // 物理尺寸
        val xInches = widthPx.toDouble() / dm.xdpi
        val yInches = heightPx.toDouble() / dm.ydpi
        val diagonal = Math.sqrt(xInches * xInches + yInches * yInches)
        items.add(InfoItem("屏幕尺寸", String.format("%.1f 英寸", diagonal)))
        items.add(InfoItem("X 轴 DPI", String.format("%.1f", dm.xdpi)))
        items.add(InfoItem("Y 轴 DPI", String.format("%.1f", dm.ydpi)))

        // 刷新率
        @Suppress("DEPRECATION")
        val refreshRate = wm.defaultDisplay.mode?.refreshRate
        if (refreshRate != null) {
            items.add(InfoItem("刷新率", String.format("%.1f Hz", refreshRate)))
        }

        // 支持的刷新率模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val modes = wm.defaultDisplay.supportedModes
            val rates = modes.map { String.format("%.0f", it.refreshRate) }.distinct().sorted()
            if (rates.isNotEmpty()) {
                items.add(InfoItem("支持的刷新率", rates.joinToString(" / ") + " Hz"))
            }
        }

        // HDR 支持
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            val isHdr = wm.defaultDisplay.isHdr
            items.add(InfoItem("HDR 支持", if (isHdr) "是" else "否"))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val isWideColorGamut = wm.defaultDisplay.isWideColorGamut
            items.add(InfoItem("广色域", if (isWideColorGamut) "是" else "否"))
        }

        // 屏幕方向
        val config = context.resources.configuration
        val orientation = when (config.orientation) {
            1 -> "竖屏"
            2 -> "横屏"
            else -> "未知"
        }
        items.add(InfoItem("当前方向", orientation))

        // 亮度
        try {
            val brightness = android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS
            )
            items.add(InfoItem("屏幕亮度", "$brightness / 255"))
        } catch (e: Exception) { /* ignore */ }

        // 多点触控
        val hasMultitouch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
        val hasMultitouchJazz = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND)
        val touchDesc = when {
            hasMultitouchJazz -> "支持 (10点触控)"
            hasMultitouch -> "支持 (多点触控)"
            else -> "单点触控"
        }
        items.add(InfoItem("触控支持", touchDesc))

        return items
    }

    private fun getDensityName(dpi: Int): String {
        return when {
            dpi <= 120 -> "ldpi"
            dpi <= 160 -> "mdpi"
            dpi <= 240 -> "hdpi"
            dpi <= 320 -> "xhdpi"
            dpi <= 480 -> "xxhdpi"
            dpi <= 640 -> "xxxhdpi"
            else -> "超高密度"
        }
    }

    // ==================== WiFi 信息 ====================
    @SuppressLint("MissingPermission")
    private fun getWifiInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // WiFi 是否开启
        items.add(InfoItem("WiFi 状态", if (wifiManager.isWifiEnabled) "已开启" else "已关闭"))

        // WiFi 芯片信息
        try {
            val method = wifiManager.javaClass.getMethod("getChipId")
            val chipId = method.invoke(wifiManager)
            items.add(InfoItem("WiFi 芯片ID", chipId.toString()))
        } catch (e: Exception) { /* ignore */ }

        // 当前连接的 WiFi 信息
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo != null && wifiInfo.supplicantState == android.net.wifi.SupplicantState.COMPLETED) {
            val ssid = wifiInfo.ssid?.replace("\"", "") ?: "未知"
            items.add(InfoItem("当前 SSID", ssid))

            val bssid = wifiInfo.bssid ?: "未知"
            items.add(InfoItem("BSSID", bssid))

            val freq = wifiInfo.frequency
            items.add(InfoItem("连接频率", "$freq MHz"))

            // 判断频段
            val band = when {
                freq in 2400..2500 -> "2.4 GHz"
                freq in 5000..5900 -> "5 GHz"
                freq in 5900..7200 -> "6 GHz (WiFi 6E/7)"
                else -> "未知"
            }
            items.add(InfoItem("频段", band))

            // 信道
            val channel = getWifiChannel(freq)
            items.add(InfoItem("信道", "信道 $channel"))

            // 信号强度
            val rssi = wifiInfo.rssi
            val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)
            items.add(InfoItem("信号强度", "$rssi dBm (${getSignalStrengthDesc(rssi)}, ${signalLevel + 1}/5)"))

            // 连接速度
            val linkSpeed = wifiInfo.linkSpeed
            items.add(InfoItem("连接速率", "$linkSpeed Mbps"))

            // WiFi 标准
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wifiStandard = when (wifiInfo.wifiStandard) {
                    android.net.wifi.WifiInfo.WIFI_STANDARD_LEGACY -> "802.11a/b/g (Legacy)"
                    android.net.wifi.WifiInfo.WIFI_STANDARD_11N -> "802.11n (WiFi 4)"
                    android.net.wifi.WifiInfo.WIFI_STANDARD_11AC -> "802.11ac (WiFi 5)"
                    android.net.wifi.WifiInfo.WIFI_STANDARD_11AX -> "802.11ax (WiFi 6)"
                    android.net.wifi.WifiInfo.WIFI_STANDARD_11BE -> "802.11be (WiFi 7)"
                    else -> "未知"
                }
                items.add(InfoItem("WiFi 标准", wifiStandard))
            }

            // MAC 地址
            @Suppress("DEPRECATION")
            val mac = wifiInfo.macAddress
            if (mac != null && mac != "02:00:00:00:00:00") {
                items.add(InfoItem("MAC 地址", mac))
            }

            // IP 地址
            val ipInt = wifiInfo.ipAddress
            if (ipInt != 0) {
                val ip = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff, (ipInt shr 8) and 0xff,
                    (ipInt shr 16) and 0xff, (ipInt shr 24) and 0xff
                )
                items.add(InfoItem("IP 地址", ip))
            }
        }

        // 当前吞吐量估算
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps != null) {
                val downKbps = caps.linkDownstreamBandwidthKbps
                val upKbps = caps.linkUpstreamBandwidthKbps
                if (downKbps > 0) {
                    items.add(InfoItem("下行吞吐量", formatSpeed(downKbps)))
                }
                if (upKbps > 0) {
                    items.add(InfoItem("上行吞吐量", formatSpeed(upKbps)))
                }
            }
        }

        // WiFi 支持的协议
        val protocols = mutableListOf<String>()
        protocols.add("802.11b")
        protocols.add("802.11g")
        protocols.add("802.11n (WiFi 4)")
        try {
            // 尝试检测 AC/AX 支持
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                protocols.add("802.11ac (WiFi 5)")
                protocols.add("802.11ax (WiFi 6)")
            } else {
                protocols.add("802.11ac (可能支持)")
            }
        } catch (e: Exception) { /* ignore */ }
        items.add(InfoItem("支持的协议", protocols.joinToString(", ")))

        // WiFi Direct
        items.add(InfoItem("WiFi Direct", "可用"))

        return items
    }

    private fun getWifiChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2400..2500 -> (freq - 2407) / 5
            freq in 5000..5900 -> (freq - 5000) / 5
            freq in 5900..7200 -> (freq - 5950) / 5
            else -> 0
        }
    }

    private fun getSignalStrengthDesc(rssi: Int): String {
        return when {
            rssi >= -50 -> "极好"
            rssi >= -60 -> "好"
            rssi >= -70 -> "中等"
            rssi >= -80 -> "差"
            else -> "极差"
        }
    }

    private fun formatSpeed(kbps: Int): String {
        return when {
            kbps >= 1000000 -> String.format("%.1f Gbps", kbps / 1000000.0)
            kbps >= 1000 -> String.format("%.1f Mbps", kbps / 1000.0)
            else -> "$kbps Kbps"
        }
    }

    // ==================== 移动网络信息 ====================
    @SuppressLint("MissingPermission")
    private fun getNetworkInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (tm != null) {
            // 网络类型
            val networkType = when (tm.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA -> "3G HSPA"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
                TelephonyManager.NETWORK_TYPE_CDMA -> "2G CDMA"
                TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G EVDO"
                else -> "未知或无信号"
            }
            items.add(InfoItem("网络类型", networkType))

            // 运营商
            val carrier = tm.networkOperatorName
            if (carrier.isNotEmpty()) {
                items.add(InfoItem("运营商", carrier))
            }

            // MCC/MNC
            val mccMnc = tm.networkOperator
            if (mccMnc.isNotEmpty()) {
                items.add(InfoItem("MCC/MNC", mccMnc))
            }

            // SIM 卡状态
            val simState = when (tm.simState) {
                TelephonyManager.SIM_STATE_READY -> "就绪"
                TelephonyManager.SIM_STATE_ABSENT -> "未插入"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "需要 PIN"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "需要 PUK"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "网络锁定"
                else -> "未知"
            }
            items.add(InfoItem("SIM 卡状态", simState))

            val simCarrier = tm.simOperatorName
            if (simCarrier.isNotEmpty()) {
                items.add(InfoItem("SIM 运营商", simCarrier))
            }

            // 信号强度
            val signalStrength = tm.signalStrength
            if (signalStrength != null) {
                val level = signalStrength.level
                items.add(InfoItem("信号等级", "${level}/4"))
            }

            // 手机类型
            val phoneType = when (tm.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                else -> "无"
            }
            items.add(InfoItem("电话类型", phoneType))
        }

        return items
    }

    // ==================== 蓝牙信息 ====================
    @SuppressLint("MissingPermission")
    private fun getBluetoothInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            if (btAdapter != null) {
                items.add(InfoItem("蓝牙", "支持"))
                items.add(InfoItem("蓝牙状态", if (btAdapter.isEnabled) "已开启" else "已关闭"))
                items.add(InfoItem("蓝牙名称", btAdapter.name ?: "未知"))
                items.add(InfoItem("蓝牙地址", btAdapter.address ?: "未知"))

                // 蓝牙 LE
                items.add(InfoItem("蓝牙 LE", if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) "支持" else "不支持"))
            } else {
                items.add(InfoItem("蓝牙", "不支持"))
            }
        } catch (e: Exception) {
            items.add(InfoItem("蓝牙", "无法获取 (${e.message})"))
        }
        return items
    }

    // ==================== 传感器信息 ====================
    private fun getSensorInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorList = sm.getSensorList(Sensor.TYPE_ALL)

        items.add(InfoItem("传感器数量", "${sensorList.size} 个"))

        for ((index, sensor) in sensorList.withIndex()) {
            val sensorType = getSensorTypeName(sensor.type)
            val info = "$sensorType | ${sensor.vendor} | v${sensor.version} | 功耗 ${sensor.power}mA"
            items.add(InfoItem("${index + 1}. ${sensor.name}", info))
        }

        return items
    }

    private fun getSensorTypeName(type: Int): String {
        return when (type) {
            Sensor.TYPE_ACCELEROMETER -> "加速度计"
            Sensor.TYPE_GYROSCOPE -> "陀螺仪"
            Sensor.TYPE_MAGNETIC_FIELD -> "磁力计"
            Sensor.TYPE_LIGHT -> "光线传感器"
            Sensor.TYPE_PROXIMITY -> "距离传感器"
            Sensor.TYPE_PRESSURE -> "气压计"
            Sensor.TYPE_TEMPERATURE -> "温度传感器"
            Sensor.TYPE_GRAVITY -> "重力传感器"
            Sensor.TYPE_LINEAR_ACCELERATION -> "线性加速度"
            Sensor.TYPE_ROTATION_VECTOR -> "旋转矢量"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "湿度传感器"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "环境温度"
            Sensor.TYPE_STEP_COUNTER -> "计步器"
            Sensor.TYPE_STEP_DETECTOR -> "步行检测"
            Sensor.TYPE_HEART_RATE -> "心率传感器"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "游戏旋转矢量"
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "地磁旋转矢量"
            Sensor.TYPE_SIGNIFICANT_MOTION -> "显著运动"
            Sensor.TYPE_STATIONARY_DETECT -> "静止检测"
            Sensor.TYPE_MOTION_DETECT -> "运动检测"
            Sensor.TYPE_HEART_BEAT -> "心跳检测"
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "离体检测"
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "未校准加速度计"
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "未校准陀螺仪"
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "未校准磁力计"
            else -> "传感器(0x${Integer.toHexString(type)})"
        }
    }

    // ==================== 摄像头信息 ====================
    @SuppressLint("MissingPermission")
    private fun getCameraInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cm.cameraIdList
            items.add(InfoItem("摄像头数量", "${cameraIds.size} 个"))

            for (id in cameraIds) {
                val chars = cm.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val facingStr = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "前置"
                    CameraCharacteristics.LENS_FACING_BACK -> "后置"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "外置"
                    else -> "未知"
                }

                // 硬件级别
                val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                val hwLevelStr = when (hwLevel) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "完整"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "有限"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "传统"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "外置"
                    else -> "未知"
                }

                // 传感器分辨率
                val pixelArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val resolution = if (pixelArraySize != null) {
                    val mp = (pixelArraySize.width.toLong() * pixelArraySize.height) / 1000000.0
                    "${pixelArraySize.width}x${pixelArraySize.height} (${String.format("%.1f", mp)} MP)"
                } else "未知"

                // 焦距
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val focalStr = focalLengths?.joinToString(", ") { "${it}mm" } ?: "未知"

                // 光圈
                val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                val apertureStr = apertures?.joinToString(", ") { "f/${String.format("%.1f", it)}" } ?: "未知"

                // ISO 范围
                val sensitivityRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val isoStr = if (sensitivityRange != null) "${sensitivityRange.lower} - ${sensitivityRange.upper}" else "未知"

                // OIS
                val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                val hasOis = oisModes?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) ?: false

                // 支持的输出格式
                val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                val hasRaw = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) ?: false

                items.add(InfoItem("摄像头 $id ($facingStr)", resolution))
                items.add(InfoItem("  硬件级别", hwLevelStr))
                items.add(InfoItem("  焦距", focalStr))
                items.add(InfoItem("  光圈", apertureStr))
                items.add(InfoItem("  ISO 范围", isoStr))
                items.add(InfoItem("  光学防抖", if (hasOis) "支持" else "不支持"))
                items.add(InfoItem("  RAW 输出", if (hasRaw) "支持" else "不支持"))
            }
        } catch (e: Exception) {
            items.add(InfoItem("摄像头", "无法获取 (${e.message})"))
        }
        return items
    }

    // ==================== 音频信息 ====================
    private fun getAudioInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 采样率
        val sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        if (sampleRate != null) {
            items.add(InfoItem("输出采样率", "$sampleRate Hz"))
        }

        // 帧大小
        val framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        if (framesPerBuffer != null) {
            items.add(InfoItem("缓冲区帧数", "$framesPerBuffer frames"))
        }

        // 低延迟支持
        val hasLowLatency = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
        items.add(InfoItem("Pro Audio", if (hasLowLatency) "支持" else "不支持"))

        // 麦克风
        val hasMic = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        items.add(InfoItem("麦克风", if (hasMic) "支持" else "不支持"))

        // 当前音量
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        items.add(InfoItem("媒体音量", "$curVol / $maxVol"))

        // 扬声器
        val hasSpeaker = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        items.add(InfoItem("音频输出", if (hasSpeaker) "支持" else "不支持"))

        return items
    }

    // ==================== NFC 信息 ====================
    @Suppress("DEPRECATION")
    private fun getNfcInfo(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val hasNfc = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        items.add(InfoItem("NFC 硬件", if (hasNfc) "支持" else "不支持"))

        if (hasNfc) {
            val nfcManager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
            val adapter = nfcManager?.defaultAdapter
            items.add(InfoItem("NFC 状态", if (adapter?.isEnabled == true) "已开启" else "已关闭"))
            items.add(InfoItem("NFC HCE", if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) "支持" else "不支持"))
            val beamEnabled = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { try { adapter?.isNdefPushEnabled == true } catch (e: Exception) { false } } else false
            items.add(InfoItem("NFC Beam", if (beamEnabled) "已开启" else "不支持/已关闭"))
        }
        return items
    }

    // ==================== 工具方法 ====================
    private fun readFromFile(path: String): String {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine()?.trim() ?: "" }
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
