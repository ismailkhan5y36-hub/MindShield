package com.mindshield.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var quotaManager: DailyQuotaManager
    private lateinit var productivityManager: ProductivityManager
    private lateinit var antiCheatManager: AntiCheatManager

    // Header & Tabs
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var tabShield: View
    private lateinit var tabGoals: View
    private lateinit var tabFocus: View
    private lateinit var tabMindset: View
    private lateinit var bottomNavigation: BottomNavigationView

    // Tab 1: Shield Views
    private lateinit var viewStatusDot: View
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvRemainingLabel: TextView
    private lateinit var tvUsedToday: TextView
    private lateinit var pbQuota: ProgressBar
    private lateinit var btnAccessibility: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnBattery: MaterialButton
    private lateinit var btnDeviceAdmin: MaterialButton
    private lateinit var btnDnsGuard: MaterialButton
    private lateinit var tvRiskStatus: TextView
    private val VPN_REQUEST_CODE = 3030

    // Tab 1: Time Saved & Screen Time Views
    private lateinit var tvTimeSavedWeekly: TextView
    private lateinit var tvTotalScreenTime: TextView
    private lateinit var btnUsagePermission: MaterialButton
    private lateinit var rvAppUsage: RecyclerView
    private lateinit var appUsageAdapter: AppUsageAdapter

    // Tab 2: Goals Views
    private lateinit var tvGoalsPercentage: TextView
    private lateinit var pbGoalsProgress: ProgressBar
    private lateinit var etNewGoal: EditText
    private lateinit var btnAddGoal: Button
    private lateinit var rvGoals: RecyclerView
    private lateinit var goalsAdapter: GoalsAdapter

    // Tab 3: Focus (Pomodoro) Views
    private lateinit var tvFocusPhase: TextView
    private lateinit var tvFocusTime: TextView
    private lateinit var btnStartFocus: MaterialButton
    private lateinit var btnResetFocus: MaterialButton
    private lateinit var tvFocusSessionsCount: TextView
    private var pomodoroTimer: CountDownTimer? = null
    private var isFocusRunning = false
    private var focusTimeLeftMs = 25 * 60 * 1000L // 25 mins

    // Anti-Cheat Arming Card (Tab 1)
    private lateinit var tvAntiCheatBadge: TextView
    private lateinit var tvAntiCheatStatusTitle: TextView
    private lateinit var tvAntiCheatStatusDesc: TextView
    private lateinit var btnArmAntiCheat: MaterialButton

    // Tab 4: Mindset Views
    private lateinit var tvStreakDays: TextView
    private lateinit var btnOpenMindReset: MaterialButton
    private lateinit var switchReminders: SwitchCompat
    private lateinit var tvMindsetAntiCheatStatus: TextView
    private lateinit var tvMindsetAntiCheatBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        quotaManager = DailyQuotaManager(this)
        productivityManager = ProductivityManager(this)
        antiCheatManager = AntiCheatManager(this)
        DomainDatabase.init(this)

        initViews()
        setupBottomNavigation()
        setupShieldListeners()
        setupScreenTimeView()
        setupGoals()
        setupFocusTimer()
        setupMindset()
        configureDailyVictoryScorecard()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    private fun initViews() {
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle)
        tabShield = findViewById(R.id.tabShield)
        tabGoals = findViewById(R.id.tabGoals)
        tabFocus = findViewById(R.id.tabFocus)
        tabMindset = findViewById(R.id.tabMindset)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Shield views
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvStatusDesc = findViewById(R.id.tvStatusDesc)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        tvRemainingLabel = findViewById(R.id.tvRemainingLabel)
        tvUsedToday = findViewById(R.id.tvUsedToday)
        pbQuota = findViewById(R.id.pbQuota)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnBattery = findViewById(R.id.btnBattery)
        btnDeviceAdmin = findViewById(R.id.btnDeviceAdmin)
        btnDnsGuard = findViewById(R.id.btnDnsGuard)
        tvRiskStatus = findViewById(R.id.tvRiskStatus)

        // Time Saved & Screen Time
        tvTimeSavedWeekly = findViewById(R.id.tvTimeSavedWeekly)
        tvTotalScreenTime = findViewById(R.id.tvTotalScreenTime)
        btnUsagePermission = findViewById(R.id.btnUsagePermission)
        rvAppUsage = findViewById(R.id.rvAppUsage)

        // Goals views
        tvGoalsPercentage = findViewById(R.id.tvGoalsPercentage)
        pbGoalsProgress = findViewById(R.id.pbGoalsProgress)
        etNewGoal = findViewById(R.id.etNewGoal)
        btnAddGoal = findViewById(R.id.btnAddGoal)
        rvGoals = findViewById(R.id.rvGoals)

        // Focus views
        tvFocusPhase = findViewById(R.id.tvFocusPhase)
        tvFocusTime = findViewById(R.id.tvFocusTime)
        btnStartFocus = findViewById(R.id.btnStartFocus)
        btnResetFocus = findViewById(R.id.btnResetFocus)
        tvFocusSessionsCount = findViewById(R.id.tvFocusSessionsCount)

        // Anti-Cheat Arming Card (Tab 1)
        tvAntiCheatBadge = findViewById(R.id.tvAntiCheatBadge)
        tvAntiCheatStatusTitle = findViewById(R.id.tvAntiCheatStatusTitle)
        tvAntiCheatStatusDesc = findViewById(R.id.tvAntiCheatStatusDesc)
        btnArmAntiCheat = findViewById(R.id.btnArmAntiCheat)

        // Mindset views
        tvStreakDays = findViewById(R.id.tvStreakDays)
        btnOpenMindReset = findViewById(R.id.btnOpenMindReset)
        switchReminders = findViewById(R.id.switchReminders)
        tvMindsetAntiCheatStatus = findViewById(R.id.tvMindsetAntiCheatStatus)
        tvMindsetAntiCheatBadge = findViewById(R.id.tvMindsetAntiCheatBadge)
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            tabShield.visibility = View.GONE
            tabGoals.visibility = View.GONE
            tabFocus.visibility = View.GONE
            tabMindset.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_shield -> {
                    tabShield.visibility = View.VISIBLE
                    tvHeaderSubtitle.text = "Digital Detox & Focus Guard"
                }
                R.id.nav_goals -> {
                    tabGoals.visibility = View.VISIBLE
                    tvHeaderSubtitle.text = "Daily Goals & Achievements"
                    updateGoalsList()
                }
                R.id.nav_focus -> {
                    tabFocus.visibility = View.VISIBLE
                    tvHeaderSubtitle.text = "Deep Work Pomodoro Timer"
                    updateFocusUI()
                }
                R.id.nav_mindset -> {
                    tabMindset.visibility = View.VISIBLE
                    tvHeaderSubtitle.text = "Mindset & Daily Discipline"
                    updateMindsetUI()
                }
            }
            true
        }
    }

    // --- TAB 1: SHIELD & SCREEN TIME LOGIC ---
    private fun setupShieldListeners() {
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        btnBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }

        btnDeviceAdmin.setOnClickListener {
            if (!isDeviceAdminActive()) {
                requestDeviceAdmin()
            }
        }

        btnUsagePermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        btnDnsGuard.setOnClickListener {
            val vpnIntent = android.net.VpnService.prepare(this)
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
            } else {
                startDnsFirewall()
            }
        }

        btnArmAntiCheat.setOnClickListener {
            if (antiCheatManager.isAntiCheatEnabled()) {
                android.widget.Toast.makeText(this, "Lock already permanently armed!", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Arm 2-Hour Lock?")
                .setMessage("Testing mukammal ho chuki hai? Lock karne ke baad MindShield 2 ghante ke countdown ke baghair uninstall ya disable nahi hoga.\n\nKya aap lock active karna chahte hain?")
                .setPositiveButton("Yes, Lock MindShield") { _, _ ->
                    antiCheatManager.armAntiCheatPermanently()
                    updateDashboard()
                    android.widget.Toast.makeText(this, "🛡️ MindShield 2-Hour Lock Armed & Active!", android.widget.Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Abhi Test Karna Hai", null)
                .show()
        }
    }

    private fun setupScreenTimeView() {
        rvAppUsage.layoutManager = LinearLayoutManager(this)
        appUsageAdapter = AppUsageAdapter(emptyList())
        rvAppUsage.adapter = appUsageAdapter
    }

    private fun updateScreenTimeStats() {
        if (!UsageStatsHelper.hasUsageStatsPermission(this)) {
            btnUsagePermission.visibility = View.VISIBLE
            tvTotalScreenTime.text = "Permission Required (Click Grant)"
            appUsageAdapter.updateList(emptyList())
        } else {
            btnUsagePermission.visibility = View.GONE
            val (totalMillis, topApps) = UsageStatsHelper.getDailyUsageStats(this)
            tvTotalScreenTime.text = "Total: ${UsageStatsHelper.formatDuration(totalMillis)} Today"
            appUsageAdapter.updateList(topApps)
        }
    }

    // --- TAB 2: GOALS LOGIC ---
    private fun setupGoals() {
        rvGoals.layoutManager = LinearLayoutManager(this)
        goalsAdapter = GoalsAdapter(
            items = emptyList(),
            onToggle = { item ->
                productivityManager.toggleGoal(item.id)
                updateGoalsList()
            },
            onDelete = { item ->
                productivityManager.deleteGoal(item.id)
                updateGoalsList()
            }
        )
        rvGoals.adapter = goalsAdapter

        btnAddGoal.setOnClickListener {
            val text = etNewGoal.text.toString().trim()
            if (text.isNotEmpty()) {
                productivityManager.addGoal(text)
                etNewGoal.text.clear()
                updateGoalsList()
            }
        }
    }

    private fun updateGoalsList() {
        val list = productivityManager.getGoals()
        goalsAdapter.updateList(list)

        val total = list.size
        val completed = list.count { it.isCompleted }
        val percent = if (total > 0) (completed * 100) / total else 0

        tvGoalsPercentage.text = "$percent%"
        pbGoalsProgress.progress = percent
    }

    // --- TAB 3: FOCUS (POMODORO) LOGIC ---
    private fun setupFocusTimer() {
        btnStartFocus.setOnClickListener {
            if (isFocusRunning) {
                pauseFocusTimer()
            } else {
                startFocusTimer()
            }
        }

        btnResetFocus.setOnClickListener {
            resetFocusTimer()
        }
    }

    private fun startFocusTimer() {
        isFocusRunning = true
        btnStartFocus.text = "Pause"

        pomodoroTimer?.cancel()
        pomodoroTimer = object : CountDownTimer(focusTimeLeftMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                focusTimeLeftMs = millisUntilFinished
                updateFocusTimerDisplay()
            }

            override fun onFinish() {
                isFocusRunning = false
                btnStartFocus.text = "Start"
                focusTimeLeftMs = 25 * 60 * 1000L
                updateFocusTimerDisplay()
                productivityManager.incrementFocusSession()
                updateFocusUI()
            }
        }.start()
    }

    private fun pauseFocusTimer() {
        isFocusRunning = false
        btnStartFocus.text = "Resume"
        pomodoroTimer?.cancel()
    }

    private fun resetFocusTimer() {
        isFocusRunning = false
        pomodoroTimer?.cancel()
        btnStartFocus.text = "Start"
        focusTimeLeftMs = 25 * 60 * 1000L
        updateFocusTimerDisplay()
    }

    private fun updateFocusTimerDisplay() {
        val minutes = (focusTimeLeftMs / 1000) / 60
        val seconds = (focusTimeLeftMs / 1000) % 60
        tvFocusTime.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateFocusUI() {
        updateFocusTimerDisplay()
        val sessions = productivityManager.getFocusSessionsToday()
        tvFocusSessionsCount.text = "Completed Today: $sessions Sessions"
    }

    // --- TAB 4: MINDSET & ANTI-CHEAT LOGIC ---
    private fun setupMindset() {
        btnOpenMindReset.setOnClickListener {
            startActivity(Intent(this, MindResetActivity::class.java))
        }

        switchReminders.isChecked = productivityManager.isRemindersEnabled()
        switchReminders.setOnCheckedChangeListener { _, isChecked ->
            productivityManager.setRemindersEnabled(isChecked)
            configureHourlyReminder(isChecked)
        }
    }

    private fun updateMindsetUI() {
        val days = productivityManager.getStreakDays()
        tvStreakDays.text = "Day $days Streak"
    }

    private fun configureHourlyReminder(enabled: Boolean) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (enabled) {
            val intervalMs = 60 * 60 * 1000L // 1 hour
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMs,
                intervalMs,
                pendingIntent
            )
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun configureDailyVictoryScorecard() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, VictoryScorecardReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 21)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            if (before(java.util.Calendar.getInstance())) {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    // --- DASHBOARD UPDATER ---
    private fun updateDashboard() {
        val isServiceOn = isAccessibilityServiceEnabled()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (isServiceOn) {
            viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.success))
            tvServiceStatus.text = "Focus Shield Active"
            tvStatusDesc.text = "Reels quota aur 2-hour anti-cheat 100% active hain."
            btnAccessibility.text = "✓ Accessibility Service Enabled"
            btnAccessibility.strokeColor = ContextCompat.getColorStateList(this, R.color.success)
        } else {
            viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.danger))
            tvServiceStatus.text = "Protection Inactive"
            tvStatusDesc.text = "Neeche button par click karke 'MindShield' service ko on karein."
            btnAccessibility.text = "1. Turn On Accessibility Service"
        }

        if (hasOverlay) {
            btnOverlay.text = "✓ Display Over Apps Allowed"
            btnOverlay.strokeColor = ContextCompat.getColorStateList(this, R.color.success)
        } else {
            btnOverlay.text = "2. Allow Display Over Other Apps"
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm.isIgnoringBatteryOptimizations(packageName)) {
            btnBattery.text = "✓ HiOS Battery Optimization Ignored"
            btnBattery.strokeColor = ContextCompat.getColorStateList(this, R.color.success)
        } else {
            btnBattery.text = "3. Tecno/HiOS Battery Exemption"
        }

        if (isDeviceAdminActive()) {
            btnDeviceAdmin.text = "✓ 4. Hardware Uninstall Lock Active"
            btnDeviceAdmin.strokeColor = ContextCompat.getColorStateList(this, R.color.success)
        } else {
            btnDeviceAdmin.text = "4. Enable Hardware Uninstall Lock (Device Admin)"
            btnDeviceAdmin.strokeColor = ContextCompat.getColorStateList(this, R.color.card_stroke)
        }

        val vpnPrepared = android.net.VpnService.prepare(this) == null
        if (vpnPrepared) {
            btnDnsGuard.text = "✓ 5. Network Firewall Active (950k+ Domains)"
            btnDnsGuard.strokeColor = ContextCompat.getColorStateList(this, R.color.success)
        } else {
            btnDnsGuard.text = "5. Enable 24/7 Network Firewall (Local DNS)"
            btnDnsGuard.strokeColor = ContextCompat.getColorStateList(this, R.color.card_stroke)
        }

        if (RiskTimeEngine.isHighRiskWindow()) {
            tvRiskStatus.text = "🚨 HIGH-RISK ZONE ACTIVE (Maximum Strict AI)"
            tvRiskStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
        } else {
            tvRiskStatus.text = "🟢 Normal Protection Active"
            tvRiskStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
        }

        // Reels Quota
        val remainingSecs = quotaManager.getRemainingSeconds()
        val usedSecs = quotaManager.getUsedSeconds()

        val remainingMins = remainingSecs / 60
        val remainingSecondsInMin = remainingSecs % 60
        tvRemainingTime.text = String.format("%02d:%02d", remainingMins, remainingSecondsInMin)

        pbQuota.progress = usedSecs
        val usedMins = usedSecs / 60
        tvUsedToday.text = "Used Today: $usedMins / 60 mins"

        if (remainingSecs <= 0) {
            tvRemainingLabel.text = "DAILY QUOTA EXHAUSTED! BLOCKED."
            tvRemainingLabel.setTextColor(ContextCompat.getColor(this, R.color.danger))
        } else {
            tvRemainingLabel.text = "Remaining (out of 60 mins)"
            tvRemainingLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        // Time Saved Life Counter
        val streak = productivityManager.getStreakDays()
        val savedHours = streak * 2
        tvTimeSavedWeekly.text = "Mashallah! ${savedHours}h+ Saved This Week"

        // Anti-Cheat Status & Arming
        val isArmed = antiCheatManager.isAntiCheatEnabled()
        if (isArmed) {
            tvAntiCheatBadge.text = "🔒 ARMED & LOCKED"
            tvAntiCheatBadge.setTextColor(ContextCompat.getColor(this, R.color.success))
            tvAntiCheatStatusTitle.text = "Status: Permanently Armed"
            tvAntiCheatStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.success))
            tvAntiCheatStatusDesc.text = "Anti-Uninstall, Anti-Freezer aur Accessibility Protection 100% active hain. Reboot hone par bhi lock rahega."
            btnArmAntiCheat.text = "✓ Armed & Protected (Permanent)"
            btnArmAntiCheat.isEnabled = false
            btnArmAntiCheat.alpha = 0.6f

            tvMindsetAntiCheatBadge.text = "🔒 Armed"
            tvMindsetAntiCheatBadge.setTextColor(ContextCompat.getColor(this, R.color.success))
            tvMindsetAntiCheatStatus.text = "2-Hour delay active. Accidental uninstall se mehfooz."
        } else {
            tvAntiCheatBadge.text = "Testing Mode"
            tvAntiCheatBadge.setTextColor(ContextCompat.getColor(this, R.color.accent))
            tvAntiCheatStatusTitle.text = "Status: Unlocked (Ready to Test)"
            tvAntiCheatStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            tvAntiCheatStatusDesc.text = "Pehle aaram se app test karein. Jab aap satisfied hon, tab Lock ON karein. Ek baar Lock ho gaya toh 2 ghante se pehle delete ya disable nahi ho sakega."
            btnArmAntiCheat.text = "🔒 ARM 2-HOUR LOCK (PERMANENT)"
            btnArmAntiCheat.isEnabled = true
            btnArmAntiCheat.alpha = 1.0f

            tvMindsetAntiCheatBadge.text = "Testing Mode"
            tvMindsetAntiCheatBadge.setTextColor(ContextCompat.getColor(this, R.color.accent))
            tvMindsetAntiCheatStatus.text = "Lock abhi off hai. Tab 1 se permanent arm karein."
        }

        updateScreenTimeStats()
        updateGoalsList()
        updateFocusUI()
        updateMindsetUI()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "${packageName}/${ShieldAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(expectedComponentName) ||
                enabledServices.contains(ShieldAccessibilityService::class.java.simpleName)
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val componentName = android.content.ComponentName(this, ShieldDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    private fun requestDeviceAdmin() {
        val componentName = android.content.ComponentName(this, ShieldDeviceAdminReceiver::class.java)
        val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "MindShield Hardware Lock: Disables the Uninstall button to maintain deep focus, discipline, and habit consistency."
            )
        }
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startDnsFirewall()
        }
    }

    private fun startDnsFirewall() {
        val intent = Intent(this, LocalDnsService::class.java).apply {
            action = LocalDnsService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        updateDashboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        pomodoroTimer?.cancel()
    }
}
