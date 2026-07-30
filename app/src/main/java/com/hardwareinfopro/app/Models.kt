package com.hardwareinfopro.app

/**
 * 硬件信息数据模型
 */

enum class InfoCategory(val title: String, val colorResId: Int) {
    SYSTEM("系统信息", R.color.cat_system),
    CPU("处理器 (CPU)", R.color.cat_cpu),
    GPU("图形处理器 (GPU)", R.color.cat_gpu),
    MEMORY("内存与存储", R.color.cat_memory),
    BATTERY("电池信息", R.color.cat_battery),
    USB("USB 信息", R.color.cat_usb),
    SCREEN("屏幕显示", R.color.cat_screen),
    WIFI("无线网络 (WiFi)", R.color.cat_wifi),
    NETWORK("移动网络", R.color.cat_network),
    BLUETOOTH("蓝牙", R.color.cat_bluetooth),
    SENSOR("传感器", R.color.cat_sensor),
    CAMERA("摄像头", R.color.cat_camera),
    AUDIO("音频", R.color.cat_audio),
    NFC("NFC", R.color.cat_nfc)
}

data class InfoItem(
    val key: String,
    val value: String
)

data class InfoSection(
    val category: InfoCategory,
    val items: List<InfoItem>,
    var isExpanded: Boolean = true
)
