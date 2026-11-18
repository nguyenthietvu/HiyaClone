package com.example.hiyaclone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var switchBlockUnknown: SwitchCompat
    private val blockedItems = mutableListOf<String>()

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Đã kích hoạt chế độ bảo vệ!", Toast.LENGTH_SHORT).show()
        }
    }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            BlockedListManager.setBlockUnknown(this, true)
            switchBlockUnknown.isChecked = true
            Toast.makeText(this, "Đã bật chặn toàn bộ số ngoài danh bạ!", Toast.LENGTH_SHORT).show()
        } else {
            BlockedListManager.setBlockUnknown(this, false)
            switchBlockUnknown.isChecked = false
            Toast.makeText(this, "Cần cấp quyền Danh bạ để sử dụng tính năng này.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 40)
        }

        val tvHeader = TextView(this).apply {
            text = "1. Cấu hình quyền hệ thống"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 12)
        }

        val btnRole = Button(this).apply {
            text = "Cấp quyền Chặn & Lọc cuộc gọi"
            setOnClickListener { requestScreeningRole() }
        }

        val btnOverlay = Button(this).apply {
            text = "Cấp quyền Hiển thị Popup Caller ID"
            setOnClickListener { requestOverlayPermission() }
        }

        val tvSmartBlockTitle = TextView(this).apply {
            text = "2. Chế độ bảo vệ nâng cao"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 30, 0, 12)
        }

        switchBlockUnknown = SwitchCompat(this).apply {
            text = "Chặn tất cả số không lưu trong danh bạ"
            textSize = 15f
            isChecked = BlockedListManager.isBlockUnknownEnabled(this@MainActivity)
            setPadding(10, 20, 10, 20)

            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    handleEnableBlockUnknown()
                } else {
                    BlockedListManager.setBlockUnknown(this@MainActivity, false)
                    Toast.makeText(this@MainActivity, "Đã tắt chặn số ngoài danh bạ", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val tvBlockTitle = TextView(this).apply {
            text = "3. Danh sách số / đầu số chặn thủ công"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 30, 0, 12)
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val etPhone = EditText(this).apply {
            hint = "Nhập SĐT hoặc đầu số (VD: 024777)"
            inputType = InputType.TYPE_CLASS_PHONE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnAdd = Button(this).apply {
            text = "Chặn"
            setOnClickListener {
                val input = etPhone.text.toString()
                if (input.isBlank()) {
                    Toast.makeText(this@MainActivity, "Vui lòng nhập số điện thoại", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val added = BlockedListManager.addNumber(this@MainActivity, input)
                if (added) {
                    etPhone.text.clear()
                    refreshBlockedList()
                    Toast.makeText(this@MainActivity, "Đã thêm vào danh sách chặn!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Số không hợp lệ hoặc đã tồn tại", Toast.LENGTH_SHORT).show()
                }
            }
        }

        inputRow.addView(etPhone)
        inputRow.addView(btnAdd)

        val tvHint = TextView(this).apply {
            text = "* Chạm vào một số bên dưới để bỏ chặn"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 12)
        }

        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blockedItems)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedPhone = blockedItems[position]
            AlertDialog.Builder(this)
                .setTitle("Bỏ chặn số này?")
                .setMessage("Bạn có muốn xóa $selectedPhone khỏi danh sách chặn không?")
                .setPositiveButton("Xóa") { _, _ ->
                    BlockedListManager.removeNumber(this, selectedPhone)
                    refreshBlockedList()
                    Toast.makeText(this, "Đã bỏ chặn $selectedPhone", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        rootLayout.addView(tvHeader)
        rootLayout.addView(btnRole)
        rootLayout.addView(btnOverlay)
        rootLayout.addView(tvSmartBlockTitle)
        rootLayout.addView(switchBlockUnknown)
        rootLayout.addView(tvBlockTitle)
        rootLayout.addView(inputRow)
        rootLayout.addView(tvHint)
        rootLayout.addView(listView)

        setContentView(rootLayout)
        refreshBlockedList()
    }

    private fun handleEnableBlockUnknown() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            BlockedListManager.setBlockUnknown(this, true)
            Toast.makeText(this, "Đã bật chặn toàn bộ số ngoài danh bạ!", Toast.LENGTH_SHORT).show()
        } else {
            switchBlockUnknown.isChecked = false
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun refreshBlockedList() {
        blockedItems.clear()
        blockedItems.addAll(BlockedListManager.getBlockedNumbers(this))
        adapter.notifyDataSetChanged()
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            } else {
                Toast.makeText(this, "Quyền lọc cuộc gọi đã được cấp!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Quyền hiển thị Popup đã được cấp!", Toast.LENGTH_SHORT).show()
        }
    }
}
