package com.assem.copyapp

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private data class AppItem(val label: String, val packageName: String, val icon: Drawable)

    private lateinit var session: SessionManager
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var planChip: TextView
    private lateinit var permissionText: TextView
    private lateinit var scheduleText: TextView
    private lateinit var premiumStatusText: TextView
    private lateinit var usageText: TextView
    private lateinit var deviceText: TextView
    private lateinit var statsText: TextView
    private lateinit var strictCheck: CheckBox
    private lateinit var appsBox: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var durationInput: EditText

    private val appChecks = LinkedHashMap<String, CheckBox>()
    private var allApps: List<AppItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        setContentView(buildUi())
        loadInstalledApps()
        renderApps("")
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bg))
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        planChip = chip("مجاني", R.color.surface_2)
        val title = TextView(this).apply {
            text = "نسخه جديد"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.text))
        }
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(18))
                addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(planChip)
            }
        )

        val dash = card("الحالة الحالية")
        timerText = TextView(this).apply {
            text = "لا توجد جلسة نشطة"
            textSize = 30f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(color(R.color.text))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        statusText = body("")
        statusText.setPadding(0, dp(12), 0, dp(8))
        dash.addView(timerText)
        dash.addView(progressBar)
        dash.addView(statusText)
        dash.addView(
            buttonRow(
                actionButton("بدء جلسة تركيز", R.color.primary) { startFocusSession() },
                actionButton("إنهاء الجلسة", R.color.surface_2) { stopSession() }
            )
        )
        strictCheck = CheckBox(this).apply {
            text = "وضع التركيز الشديد (حظر كل التطبيقات إلا الهاتف والرسائل)"
            textSize = 13f
            setTextColor(color(R.color.muted))
            buttonTintList = ColorStateList.valueOf(color(R.color.primary))
            isChecked = session.strictMode
            setOnClickListener {
                session.setStrictMode(isChecked)
                toast(if (isChecked) "تم تفعيل الوضع الشديد" else "تم إيقاف الوضع الشديد")
            }
        }
        dash.addView(
            strictCheck,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }
        )
        root.addView(dash)

        val permCard = card("الصلاحيات")
        permissionText = body("—")
        permCard.addView(permissionText)
        val reviewButton = actionButton("مراجعة الصلاحيات", R.color.surface_2) { requestAllPermissions() }
        reviewButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(10), 0, 0) }
        permCard.addView(reviewButton)
        val adminButton = actionButton("تفعيل حماية الجهاز (Device Admin)", R.color.surface_2) {
            requestDeviceAdmin()
        }
        adminButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, 0) }
        permCard.addView(adminButton)
        root.addView(permCard)

        val appsCard = card("التطبيقات المحظورة")
        searchInput = EditText(this).apply {
            hint = "ابحث عن تطبيق…"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(color(R.color.text))
            setHintTextColor(color(R.color.muted))
            background = rounded(R.color.surface_2, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        appsCard.addView(
            searchInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
        )
        appsCard.addView(
            buttonRow(
                actionButton("تحديد الكل", R.color.surface_2) {
                    appChecks.values.forEach { it.isChecked = true }
                },
                actionButton("إلغاء الكل", R.color.surface_2) {
                    appChecks.values.forEach { it.isChecked = false }
                }
            )
        )
        appsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val appsScroll = ScrollView(this).apply {
            background = rounded(R.color.surface, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(280)
            ).apply { setMargins(0, dp(10), 0, 0) }
            addView(appsBox, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        appsCard.addView(appsScroll)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderApps(s?.toString().orEmpty())
            }
        })
        root.addView(appsCard)

        val durationCard = card("مدة الجلسة")
        durationInput = EditText(this).apply {
            hint = "بالدقائق"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("30")
            setTextColor(color(R.color.text))
            background = rounded(R.color.surface_2, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        durationCard.addView(
            durationInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        durationCard.addView(
            withTopMargin(
                buttonRow(
                    actionButton("15 د", R.color.surface_2) { durationInput.setText("15") },
                    actionButton("30 د", R.color.surface_2) { durationInput.setText("30") },
                    actionButton("60 د", R.color.surface_2) { durationInput.setText("60") },
                    actionButton("120 د", R.color.surface_2) { durationInput.setText("120") }
                ),
                dp(8)
            )
        )
        root.addView(durationCard)

        val scheduleCard = card("الجدول اليومي")
        scheduleText = body("الجدول: غير مفعّل")
        scheduleCard.addView(scheduleText)
        scheduleCard.addView(
            withTopMargin(
                buttonRow(
                    actionButton("وقت البداية", R.color.surface_2) { pickScheduleTime(true) },
                    actionButton("وقت النهاية", R.color.surface_2) { pickScheduleTime(false) }
                ),
                dp(8)
            )
        )
        scheduleCard.addView(
            withTopMargin(
                buttonRow(
                    actionButton("تفعيل الجدول", R.color.primary) { activateSchedule() },
                    actionButton("إلغاء الجدول", R.color.surface_2) { clearSchedule() }
                ),
                dp(6)
            )
        )
        root.addView(scheduleCard)

        val premiumCard = card("Premium")
        premiumStatusText = body("—")
        premiumCard.addView(premiumStatusText)
        val codeInput = EditText(this).apply {
            hint = "كود الخصم"
            setTextColor(color(R.color.text))
            setHintTextColor(color(R.color.muted))
            background = rounded(R.color.surface_2, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        premiumCard.addView(
            codeInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }
        )
        val redeemButton = actionButton("تفعيل الكود", R.color.accent) {
            if (session.redeem(codeInput.text.toString())) {
                updateStatus()
                toast("تم تفعيل Premium — ${session.ownerName}")
            } else {
                toast("الكود غير صحيح")
            }
        }
        redeemButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, 0) }
        premiumCard.addView(redeemButton)
        root.addView(premiumCard)

        val cleanCard = card("نظافة الجهاز والأداء")
        usageText = body("—")
        cleanCard.addView(usageText)
        val usageButton = actionButton("إحصاءات الاستخدام والصلاحية", R.color.surface_2) {
            showUsageInfo()
        }
        usageButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), 0, 0) }
        cleanCard.addView(usageButton)
        val cleanButton = actionButton("تنظيف التطبيقات في الخلفية", R.color.primary) {
            showCleanupDialog()
        }
        cleanButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, 0) }
        cleanCard.addView(cleanButton)
        root.addView(cleanCard)

        val deviceCard = card("معلومات الجهاز")
        deviceText = body("—")
        deviceCard.addView(deviceText)
        root.addView(deviceCard)

        val statsCard = card("إحصاءات التركيز")
        statsText = body("—")
        statsCard.addView(statsText)
        root.addView(statsCard)

        root.addView(
            body("نسخه جديد · إصدار 1.2 · حظر كامل + تنظيف الخلفية", R.color.muted, 12f)
                .apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0) }
        )

        return ScrollView(this).apply {
            setBackgroundColor(color(R.color.bg))
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun updateStatus() {
        val active = session.isActive()
        val premium = session.isPremium

        planChip.text = if (premium) "Premium" else "مجاني"
        planChip.background = rounded(if (premium) R.color.accent else R.color.surface_2, 999)
        planChip.setTextColor(color(if (premium) R.color.bg else R.color.muted))

        timerText.text = if (active) formatTime(session.remainingMillis()) else "لا توجد جلسة نشطة"
        progressBar.progress = if (active) session.progressPercent() else 0

        val plan = if (premium) "Premium" else "مجانية"
        val limit = if (premium) "24 ساعة / جلسات غير محدودة" else "6 ساعات / 4 جلسات يوميًا"
        statusText.text = "المالك: ${session.ownerName}\nالخطة: $plan\nالحد: $limit\nعدد التطبيقات المحظورة: ${session.blockedPackages().size}"

        permissionText.text = permissionSummary()

        val (start, end) = session.schedule()
        scheduleText.text = if (start >= 0 && end > start) {
            "الجدول: ${formatMinute(start)} — ${formatMinute(end)}"
        } else {
            "الجدول: غير مفعّل"
        }

        premiumStatusText.text = if (premium) {
            "Premium مفعّل ✅ — المالك: ${session.ownerName}"
        } else {
            "الخطة المجانية — أدخل الكود لتفعيل Premium"
        }

        strictCheck.isChecked = session.strictMode
        usageText.text = usageSummary()
        deviceText.text = deviceSummary()
        statsText.text = focusSummary()
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        allApps = pm.getInstalledPackages(0)
            .asSequence()
            .mapNotNull { info ->
                val ai = info.applicationInfo ?: return@mapNotNull null
                if (!ai.enabled) return@mapNotNull null
                val systemApp = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
                if (systemApp && info.packageName != packageName) return@mapNotNull null
                try {
                    AppItem(ai.loadLabel(pm).toString(), info.packageName, ai.loadIcon(pm))
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    private fun renderApps(query: String) {
        appsBox.removeAllViews()
        appChecks.clear()
        val q = query.trim().lowercase(Locale.getDefault())
        val selected = session.blockedPackages()

        for (item in allApps) {
            if (q.isNotEmpty() && !item.label.lowercase(Locale.getDefault()).contains(q)) continue

            val check = CheckBox(this).apply {
                text = item.label
                isChecked = selected.contains(item.packageName)
                tag = item.packageName
                textSize = 14f
                setTextColor(color(R.color.text))
                buttonTintList = ColorStateList.valueOf(color(R.color.primary))
            }
            val icon = ImageView(this).apply {
                setImageDrawable(item.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    setMargins(0, 0, dp(10), 0)
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(R.color.surface_2, 10)
                addView(icon)
                addView(check, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            appChecks[item.packageName] = check
            appsBox.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun selectedPackages(): Set<String> =
        appChecks.filterValues { it.isChecked }.keys.toSet()

    private fun startFocusSession() {
        if (!isAccessibilityEnabled()) {
            toast("فعّل خدمة الحظر من قسم الصلاحيات أولًا")
            return
        }
        val minutes = durationInput.text.toString().trim().toIntOrNull() ?: 30
        val result = session.start(minutes, selectedPackages())
        val message = when (result) {
            SessionManager.StartResult.Success -> {
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
                    Intent(this, TimerService::class.java)
                )
                "بدأت الجلسة لمدة $minutes دقيقة"
            }
            SessionManager.StartResult.AlreadyRunning -> "توجد جلسة نشطة بالفعل"
            SessionManager.StartResult.InvalidDuration -> "المدة غير صالحة"
            SessionManager.StartResult.LimitExceeded -> "الحد الأقصى ${session.maxMinutes / 60} ساعة"
            SessionManager.StartResult.DailyLimitReached -> "استهلكت الحد اليومي للخطة المجانية"
            SessionManager.StartResult.NoAppsSelected -> "اختر تطبيقًا واحدًا على الأقل"
        }
        toast(message)
        updateStatus()
    }

    private fun stopSession() {
        session.stop()
        updateStatus()
        toast("تم إنهاء الجلسة")
    }

    private fun requestAllPermissions() {
        if (!isAccessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        if (!hasUsageStatsAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !isIgnoringBatteryOptimization()
        ) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        toast("فعّل الخدمة وارجع إلى التطبيق")
    }

    private fun requestDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val component = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(component)) {
            toast("حماية الجهاز مفعّلة بالفعل")
            return
        }
        startActivity(
            Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "تساعد على إبقاء حظر التطبيقات نشطًا أثناء جلسة التركيز."
                )
            }
        )
    }

    private fun permissionSummary(): String {
        val accessibility = if (isAccessibilityEnabled()) "✅" else "❌"
        val notifications = if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) "✅" else "❌"
        val battery = if (isIgnoringBatteryOptimization()) "✅" else "❌"
        val usage = if (hasUsageStatsAccess()) "✅" else "❌"
        val deviceAdmin = if (isAdminActive()) "✅" else "❌"
        return "الحظر $accessibility   |   الإحصاءات $usage   |   الإشعارات $notifications\nالبطارية $battery   |   الحماية $deviceAdmin"
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

    private fun isIgnoringBatteryOptimization(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
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
                val newEnd = if (isStart) {
                    if (oldEnd > oldStart) oldEnd else (newStart + 60) % (24 * 60)
                } else {
                    minutesOfDay
                }
                session.setSchedule(newStart, newEnd)
                scheduleDaily(newStart)
                updateStatus()
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
        if (!isAccessibilityEnabled()) {
            toast("فعّل خدمة الحظر أولًا")
            return
        }
        scheduleDaily(start)
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (current in start until end) {
            val result = session.start(end - current, selectedPackages())
            if (result is SessionManager.StartResult.Success) {
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
                    Intent(this, TimerService::class.java)
                )
                toast("تم تطبيق الجدول وبدء الجلسة")
            } else {
                toast("تعذّر بدء الجلسة: $result")
            }
        } else {
            toast("تم تفعيل الجدول، سيبدأ تلقائيًا في الوقت المحدد")
        }
        updateStatus()
    }

    private fun clearSchedule() {
        session.setSchedule(-1, -1)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            200,
            Intent(this, ScheduleReceiver::class.java).setAction("com.assem.copyapp.START_SCHEDULE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        updateStatus()
        toast("تم إلغاء الجدول")
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
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
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

    private fun usageSummary(): String {
        if (!hasUsageStatsAccess()) {
            return "إحصاءات الاستخدام غير مفعّلة.\nاضغط الزر أدناه واسمح للتطبيق بقراءة استخدام التطبيقات."
        }
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            from,
            System.currentTimeMillis()
        ).orEmpty()
        val totals = stats
            .groupBy { it.packageName }
            .map { (pkg, list) -> pkg to list.sumOf { it.totalTimeInForeground } }
            .filter { (pkg, ms) -> ms > 0 && !session.isEssential(pkg) }
            .sortedByDescending { it.second }
        if (totals.isEmpty()) return "لا توجد بيانات استخدام لهذا اليوم."
        val total = totals.sumOf { it.second }
        val top = totals.take(4).joinToString("\n") {
            "• ${labelFor(it.first)} — ${formatDuration(it.second)}"
        }
        return "زمن الشاشة اليوم: ${formatDuration(total)}\n$top"
    }

    private fun showUsageInfo() {
        if (!hasUsageStatsAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            toast("فعّل إحصاءات الاستخدام ثم ارجع")
        } else {
            usageText.text = usageSummary()
            toast("تم تحديث الإحصاءات")
        }
    }

    private fun showCleanupDialog() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val processes = activityManager.runningAppProcesses.orEmpty()
            .filter {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND
            }
            .map { it.processName }
            .filter { name ->
                name != packageName &&
                    !session.isEssential(name) &&
                    !name.startsWith("com.google.android.gms") &&
                    !name.startsWith("android.")
            }
            .distinct()
            .take(10)
        if (processes.isEmpty()) {
            toast("لا توجد تطبيقات قابلة للتنظيف حاليًا")
            return
        }
        val labels = processes.map { labelFor(it) }.toTypedArray()
        val checked = BooleanArray(processes.size) { true }
        AlertDialog.Builder(this)
            .setTitle("تنظيف الخلفية")
            .setMessage("سيتم إيقاف التطبيقات المختارة دون حذف بياناتها.")
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton("تنظيف") { _, _ ->
                var count = 0
                for (index in processes.indices) {
                    if (checked[index]) {
                        activityManager.killBackgroundProcesses(processes[index])
                        count++
                    }
                }
                toast("تم تنظيف $count تطبيق")
                updateStatus()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deviceSummary(): String {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = if (plugged > 0) "يشحن" else "على البطارية"
        val storage = StatFs(Environment.getDataDirectory().path)
        val freeGb = storage.availableBytes / (1024f * 1024f * 1024f)
        val totalGb = storage.totalBytes / (1024f * 1024f * 1024f)
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val availableMb = memory.availMem / (1024f * 1024f)
        return "البطارية: ${if (percent >= 0) "$percent%" else "غير معروف"} ($charging)\n" +
            "المساحة المتاحة: ${String.format(Locale.getDefault(), "%.1f", freeGb)} / " +
            "${String.format(Locale.getDefault(), "%.1f", totalGb)} GB\n" +
            "الرام المتاحة: ${availableMb.toInt()} MB"
    }

    private fun focusSummary(): String {
        val history = session.history()
        val lines = history.take(3).joinToString("\n") {
            "• ${it.first}: ${it.second} دقيقة / ${it.third} تطبيق"
        }
        return "جلسات اليوم: ${session.sessionsToday()}\n" +
            "دقائق التركيز اليوم: ${session.focusMinutesToday()}\n" +
            if (lines.isEmpty()) "لا يوجد سجل بعد" else "آخر الجلسات:\n$lines"
    }

    private fun labelFor(packageName: String): String =
        allApps.firstOrNull { it.packageName == packageName }?.label ?: try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(packageManager).toString()
        } catch (_: Exception) {
            packageName
        }

    private fun formatDuration(millis: Long): String {
        val minutes = millis / 60_000L
        return if (minutes >= 60) "${minutes / 60}س ${minutes % 60}د" else "$minutes د"
    }

    private fun formatTime(millis: Long): String {
        val total = (millis / 1000L).coerceAtLeast(0)
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            total / 3600,
            (total % 3600) / 60,
            total % 60
        )
    }

    private fun formatMinute(minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

    private fun color(res: Int): Int = ContextCompat.getColor(this, res)

    private fun rounded(colorRes: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color(colorRes))
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun body(text: String, colorRes: Int = R.color.muted, size: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color(colorRes))
        }

    private fun chip(text: String, colorRes: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.muted))
            background = rounded(colorRes, 999)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

    private fun card(title: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.surface, 18)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(color(R.color.text))
                    setPadding(0, 0, 0, dp(12))
                }
            )
        }

    private fun actionButton(text: String, colorRes: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(color(R.color.text))
            background = rounded(colorRes, 12)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(3), 0, dp(3), 0) }
            setOnClickListener { onClick() }
        }

    private fun buttonRow(vararg buttons: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            buttons.forEach {
                addView(
                    it,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(dp(3), 0, dp(3), 0) }
                )
            }
        }

    private fun <T : android.view.View> withTopMargin(view: T, top: Int): T = view.apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, top, 0, 0) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
