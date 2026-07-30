package com.hardwareinfopro.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.hardwareinfopro.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*

/**
 * 主界面 - 展示设备所有硬件信息
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HardwareInfoAdapter
    private lateinit var hardwareInfoManager: HardwareInfoManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 权限结果回调后刷新数据
        loadHardwareInfo()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hardwareInfoManager = HardwareInfoManager(this)
        setupUI()
        requestPermissions()
        loadHardwareInfo()
    }

    private fun setupUI() {
        // 设置 RecyclerView
        adapter = HardwareInfoAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(false)
            itemAnimator = null
        }

        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            loadHardwareInfo()
        }
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.accent_blue),
            ContextCompat.getColor(this, R.color.teal_200),
            ContextCompat.getColor(this, R.color.cat_battery)
        )

        // FAB 刷新按钮
        binding.fabRefresh.setOnClickListener {
            loadHardwareInfo()
        }

        // 状态栏
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_dark)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_dark)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun loadHardwareInfo() {
        showLoading(true)

        coroutineScope.launch {
            val sections = withContext(Dispatchers.IO) {
                hardwareInfoManager.collectAllInfo()
            }

            adapter.updateData(sections)
            showLoading(false)

            val totalItems = sections.sumOf { it.items.size }
            Toast.makeText(
                this@MainActivity,
                "已加载 ${sections.size} 个分类, $totalItems 项信息",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.swipeRefresh.isRefreshing = false
        binding.progressIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        binding.fabRefresh.isEnabled = !loading
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
