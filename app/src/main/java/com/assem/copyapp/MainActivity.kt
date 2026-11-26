package com.assem.copyapp

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var statusText: TextView
    private val appChecks = LinkedHashMap<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "نسخه جديد"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        statusText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        val permissionButton = Button(this).apply {
            text = "1) تفعيل الصلاحيات المطلوبة"
            setOnClickListener { requestAllPermissions() }
        }

        val adminButton = Button(this).apply {
            text = "2) تفعيل حماية الجهاز (Device Admin)"
            setOnClickListener { requestDeviceAdmin() }
        }

        val premiumButton = Button(this).apply {
            text = "Premium / إدخال كود الخصم"
            setOnClickListener { showPremiumDialog() }
        }

        val appsLabel = TextView(this).apply {
            text = "3) اختر التطبيقات المراد حظرها أثناء الجلسة"
            textSize = 17f
            setPadding(0, 18, 0, 8)
        }

        val appsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val appsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (300 * resources.displayMetrics.density).toInt()
            )
            addView(appsBox, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        loadInstalledApps(appsBox)

        val durationInput = EditText(this).apply {
            hint = "مدة الجلسة بالدقائق"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("30")
        }

        val startButton = Button(this).apply {
            text = "بدء جلسة تركيز"
            setOnClickListener { startFocusSession(durationInput) }
        }

        val stopButton = Button(this).apply {
            text = "إنهاء الجلسة الحالية"
            setOnClickListener {
                session.stop()
                updateStatus()
                toast("تم إنهاء الجلسة")
            }
        }

        val scheduleLabel = TextView(this).apply {
            text = "4) الجدولة اليومية"
            textSize = 17f
            setPadding(0, 18, 0, 8)
        }

        val startScheduleButton = Button(this).apply {
            text = "وقت البداية"
            setOnClickListener { pickScheduleTime(isStart = true) }
        }
        val endScheduleButton = Button(this).apply {
            text = "وقت النهاية"
            setOnClickListener { pickScheduleTime(isStart = false) }
        }
        val activateScheduleButton = Button(this).apply {
            text = "تفعيل الجدول اليومي"
            setOnClickListener { activateSchedule() }
        }

        val scheduleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(startScheduleButton)
            addView(endScheduleButton)
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(permissionButton)
        root.addView(adminButton)
        root.addView(premiumButton)
        root.addView(appsLabel)
        root.addView(appsScroll)
        root.addView(durationInput)
        root.addView(startButton)
        root.addView(stopButton)
        root.addView(scheduleLabel)
        root.addView(scheduleRow)
        root.addView(activateScheduleButton)

        setContentView(root)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val state = if (session.isActive()) {
            val minutes = (session.remainingMillis() / 60_000L).coerceAtLeast(0)
            "جلسة نشطة — المتبقي $minutes دقيقة"
        } else {
            "لا توجد جلسة نشطة"
        }
        val plan = if (session.isPremium()) "Premium مفعّل" else "الخطة المجانية"
        val limit = if (session.isPremium()) "حتى 24 ساعة / جلسات غير محدودة" else "حتى 6 ساعات / 4 جلسات يوميًا"
        statusText.text = "المالك: ${session.ownerName}\n$plan\n$state\nالحد: $limit"
    }

    private fun loadInstalledApps(container: LinearLayout) {
        container.removeAllViews()
        appChecks.clear()
        val pm = packageManager
        val selected = session.blockedPackages()

        val apps = pm.getInstalledPackages(0)
            .asSequence()
            .filter { it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.applicationInfo?.enabled == true }
            .sortedBy { it.applicationInfo.loadLabel(pm).toString().lowercase(Locale.getDefault()) }
            .toList()

        for (info in apps) {
            val label = info.applicationInfo.loadLabel(pm).toString()
            val check = CheckBox(this).apply {
                text = label
                isChecked = selected.contains(info.packageName)
                tag = info.packageName
            }
            appChecks[info.packageName] = check
            container.addView(check)
        }
    }

    private fun selectedPackages(): Set<String> =
        appChecks.filterValues { it.isChecked }.keys.toSet()

    private fun startFocusSession(input: EditText) {
        val minutes = input.text.toString().trim().toIntOrNull() ?: 30
        val result = session.start(minutes, selectedPackages())
        val message = when (result) {
            SessionManager.StartResult.Success -> {
                ContextCompat.startForegroundService(this, Intent(this, TimerService::class.java))
                "بدأت الجلسة لمدة $minutes دقيقة"
            }
            SessionManager.StartResult.AlreadyRunning -> "توجد جلسة نشطة بالفعل"
            SessionManager.StartResult.InvalidDuration -> "المدة غير صالحة"
            SessionManager.StartResult.LimitExceeded -> "الحد الأقصى للخطة الحالية هو ${session.maxMinutes / 60} ساعة"
            SessionManager.StartResult.DailyLimitReached -> "استهلكت الحد اليومي للخطة المجانية"
            SessionManager.StartResult.NoAppsSelected -> "اختر تطبيقًا واحدًا على الأقل"
        }
        toast(message)
        updateStatus()
    }

    private fun requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        if (!hasUsageStatsAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        if (!isAccessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast("فعّل خدمة نسخه جديد من قائمة الخدمات")
        }

        toast("رجّع التطبيق بعد تفعيل الصلاحيات")
    }

    private fun requestDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(component)) {
            toast("حماية الجهاز مفعّلة بالفعل")
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "تسمح بإبقاء حظر التطبيقات نشطًا أثناء جلسة التركيز."
            )
        }
        startActivity(intent)
    }

    private fun hasUsageStatsAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        } catch (_: Settings.SettingNotFoundException) {
            0
        }
        if (enabled == 0) return false
        val services = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "$packageName/${AppBlockingAccessibilityService::class.java.name}"
        return services.split(':').any { it.equals(target, ignoreCase = true) }
    }

    private fun showPremiumDialog() {
        val input = EditText(this).apply { hint = "كود الخصم" }
        val current = if (session.isPremium()) "Premium مفعّل" else "الخطة المجانية"
        AlertDialog.Builder(this)
            .setTitle("Premium — نسخه جديد")
            .setMessage("المالك: ${session.ownerName}\nالحالة الحالية: $current\n\nأدخل كود الخصم لتفعيل Premium فورًا.")
            .setView(input)
            .setPositiveButton("تفعيل") { _, _ ->
                if (session.redeem(input.text.toString())) {
                    updateStatus()
                    toast("تم تفعيل Premium لحساب ${session.ownerName}")
                } else {
                    toast("الكود غير صحيح")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun pickScheduleTime(isStart: Boolean) {
        val now = Calendar.getInstance()
        val (start, end) = session.schedule()
        val initialHour = if (isStart && start >= 0) start / 60 else now.get(Calendar.HOUR_OF_DAY)
        val initialMinute = if (isStart && start >= 0) start % 60 else now.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val minutesOfDay = hour * 60 + minute
                val (oldStart, oldEnd) = session.schedule()
                val newStart = if (isStart) minutesOfDay else if (oldStart >= 0) oldStart else 0
                val newEnd = if (isStart) (if (oldEnd > oldStart) oldEnd else newStart + 60) else minutesOfDay
                session.setSchedule(newStart, newEnd)
                scheduleDaily(newStart)
                toast("تم حفظ الجدول")
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    private fun activateSchedule() {
        val (start, end) = session.schedule()
        if (start < 0 || end <= start) {
            toast("اختر وقت البداية ووقت النهاية أولًا")
            return
        }
        scheduleDaily(start)
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (current in start until end) {
            val result = session.start(end - current, selectedPackages())
            if (result is SessionManager.StartResult.Success) {
                ContextCompat.startForegroundService(this, Intent(this, TimerService::class.java))
                toast("تم تطبيق الجدول وبدء الجلسة")
            } else {
                toast("تعذّر بدء الجلسة: $result")
            }
        } else {
            toast("تم تفعيل الجدول، سيبدأ تلقائيًا في الوقت المحدد")
        }
        updateStatus()
    }

    private fun scheduleDaily(startMin: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startMin / 60)
            set(Calendar.MINUTE, startMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            200,
            Intent(this, ScheduleReceiver::class.java).setAction("com.assem.copyapp.START_SCHEDULE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
