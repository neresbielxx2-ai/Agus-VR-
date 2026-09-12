package com.agusvr.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.agusvr.R
import com.agusvr.compat.DeviceProfile
import com.agusvr.handtracking.HandModelManager
import com.agusvr.performance.PerfPolicy
import com.agusvr.runtime.VrActivity
import com.agusvr.storage.Settings
import com.agusvr.storage.SettingsStore
import kotlinx.coroutines.launch

/**
 * Tela de configuração inicial (antes do modo VR).
 * Opções reais, persistidas pelo Agus Storage e aplicadas pelo engine.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var store: SettingsStore
    private lateinit var txtStatus: TextView
    private lateinit var txtDevice: TextView

    private val cameraPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        store = SettingsStore(this)
        txtStatus = findViewById(R.id.txtStatus)
        txtDevice = findViewById(R.id.txtDevice)

        // Qualidade gráfica
        buildChips(
            findViewById(R.id.rowQuality),
            listOf("Baixa", "Média", "Alta"),
            { store.current.quality }) { i -> store.update { it.quality = i } }

        // Resolução (render scale)
        buildChips(
            findViewById(R.id.rowResolution),
            listOf("50%", "70%", "85%", "100%"),
            { scaleIndex(store.current.resolutionScale) }
        ) { i ->
            store.update { it.resolutionScale = listOf(0.5f, 0.7f, 0.85f, 1.0f)[i] }
        }

        // FPS
        buildChips(
            findViewById(R.id.rowFps),
            listOf("30", "45", "60", "72"),
            { listOf(30, 45, 60, 72).indexOf(store.current.fpsCap).coerceAtLeast(0) }
        ) { i -> store.update { it.fpsCap = listOf(30, 45, 60, 72)[i] } }

        // Modo desempenho/economia
        buildChips(
            findViewById(R.id.rowMode),
            listOf("Economia", "Desempenho", "Equilibrado", "Qualidade"),
            { store.current.perfMode }
        ) { i ->
            store.update { PerfPolicy().applyMode(it, i) }
            refreshAllChips()
            syncSwitches()
        }

        // Switches
        findViewById<SwitchCompat>(R.id.swHands).setOnCheckedChangeListener { _, v ->
            store.update { it.handTrackingEnabled = v }
        }
        findViewById<SwitchCompat>(R.id.swEffects).setOnCheckedChangeListener { _, v ->
            store.update { it.effectsEnabled = v }
        }
        findViewById<SwitchCompat>(R.id.swSound).setOnCheckedChangeListener { _, v ->
            store.update { it.soundEnabled = v }
        }
        syncSwitches()

        // Botões de status
        findViewById<Button>(R.id.btnDownloadModel).setOnClickListener { downloadModel() }
        findViewById<Button>(R.id.btnCameraPerm).setOnClickListener {
            cameraPerm.launch(Manifest.permission.CAMERA)
        }

        // Iniciar Motor VR
        findViewById<Button>(R.id.btnStartVr).setOnClickListener {
            startActivity(Intent(this, VrActivity::class.java))
        }

        txtDevice.text = DeviceProfile(this).summary
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun scaleIndex(scale: Float): Int {
        val opts = listOf(0.5f, 0.7f, 0.85f, 1.0f)
        var best = 0; var bd = Float.MAX_VALUE
        for (i in opts.indices) {
            val d = kotlin.math.abs(opts[i] - scale)
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    // ----------------------------------------------------------------- UI
    private val chipGroups = ArrayList<Pair<LinearLayout, () -> Int>>()

    private fun buildChips(
        row: LinearLayout,
        labels: List<String>,
        selected: () -> Int,
        onPick: (Int) -> Unit
    ) {
        row.removeAllViews()
        val buttons = ArrayList<Button>()
        for (i in labels.indices) {
            val b = Button(this)
            b.text = labels[i]
            b.textSize = 12f
            b.setTextColor(resources.getColor(R.color.agus_text, theme))
            b.setBackgroundResource(R.drawable.bg_chip)
            b.stateListAnimator = null
            val lp = LinearLayout.LayoutParams(0, dp(40), 1f)
            if (i > 0) lp.marginStart = dp(8)
            b.layoutParams = lp
            b.setOnClickListener {
                onPick(i)
                refreshChips(row, buttons, selected)
            }
            buttons.add(b)
            row.addView(b)
        }
        chipGroups.add(row to selected)
        refreshChips(row, buttons, selected)
    }

    private fun refreshChips(row: LinearLayout, buttons: List<Button>, selected: () -> Int) {
        val sel = selected()
        for (i in buttons.indices) buttons[i].isSelected = i == sel
    }

    private fun refreshAllChips() {
        for ((row, sel) in chipGroups) {
            val buttons = ArrayList<Button>()
            for (i in 0 until row.childCount) {
                val c = row.getChildAt(i)
                if (c is Button) buttons.add(c)
            }
            refreshChips(row, buttons, sel)
        }
    }

    private fun syncSwitches() {
        val s = store.current
        findViewById<SwitchCompat>(R.id.swHands).isChecked = s.handTrackingEnabled
        findViewById<SwitchCompat>(R.id.swEffects).isChecked = s.effectsEnabled
        findViewById<SwitchCompat>(R.id.swSound).isChecked = s.soundEnabled
    }

    private fun refreshStatus() {
        val cameraOk =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        val status = HandModelManager.status(this)
        val sb = StringBuilder()

        // Diagnóstico: se o motor VR caiu alguma vez, mostra o erro completo
        // (começa pela exceção, não pelo fim da pilha).
        val crashFull = com.agusvr.runtime.CrashLog.readAll(this)
        val failPoint = com.agusvr.runtime.BootGuard.failurePoint
        if (failPoint.isNotEmpty() || com.agusvr.runtime.BootGuard.safeMode) {
            sb.append("⚠ Última sessão parou em: ").append(failPoint.ifEmpty { "?" })
            if (com.agusvr.runtime.BootGuard.safeMode) sb.append(" — modo seguro ativo nesta sessão")
            sb.append('\n')
        }
        if (crashFull != null) {
            sb.append("⚠ Diagnóstico do motor VR:\n").append(crashFull).append("\n\n")
            showLogButtons(crashFull)
        } else if (com.agusvr.runtime.BootGuard.safeMode) {
            showLogButtons(null)
        }
        sb.append(when (status) {
            is HandModelManager.Status.Ready -> "✔ Modelo de hand tracking pronto"
            is HandModelManager.Status.Downloading -> "⬇ Baixando modelo…"
            is HandModelManager.Status.Error ->
                "✖ Erro no modelo: ${status.msg} (hand tracking ficará desativado)"
            is HandModelManager.Status.Missing ->
                "• Modelo ausente — toque em “Baixar modelo” (~8 MB)"
        })
        sb.append('\n')
        sb.append(if (cameraOk) "✔ Permissão de câmera concedida"
        else "• Toque em “Permitir câmera” para ativar o hand tracking")
        if (!cameraOk || status !is HandModelManager.Status.Ready) {
            sb.append("\nSem eles o VR usa fallback: gaze + toque (documentado).")
        }
        txtStatus.text = sb.toString()
    }

    private var logButtonsAdded = false

    private fun showLogButtons(fullLog: String?) {
        if (logButtonsAdded) return
        logButtonsAdded = true
        val card = findViewById<LinearLayout>(R.id.cardStatus)

        if (fullLog != null) {
            val copy = Button(this)
            copy.text = "Copiar log completo"
            copy.textSize = 12f
            copy.setTextColor(resources.getColor(R.color.agus_text, theme))
            copy.setBackgroundResource(R.drawable.bg_chip)
            copy.stateListAnimator = null
            val lpC = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
            lpC.topMargin = dp(10)
            copy.layoutParams = lpC
            copy.setOnClickListener {
                try {
                    val phase = com.agusvr.runtime.BootGuard.failurePoint
                    val text = "Agus VR — diagnóstico\nParou em: ${phase.ifEmpty { "?" }}\n\n$fullLog"
                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("agus_log", text))
                    copy.text = "✔ Copiado — cole no chat"
                } catch (_: Throwable) {}
            }
            card.addView(copy)
        }

        val b = Button(this)
        b.text = "Limpar log de erro"
        b.textSize = 12f
        b.setTextColor(resources.getColor(R.color.agus_warn, theme))
        b.setBackgroundResource(R.drawable.bg_chip)
        b.stateListAnimator = null
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
        lp.topMargin = dp(10)
        b.layoutParams = lp
        b.setOnClickListener {
            com.agusvr.runtime.CrashLog.clear(this)
            com.agusvr.runtime.BootGuard.clearFailure(this)
            recreate() // reconstroi a tela limpa
        }
        card.addView(b)
    }

    private fun downloadModel() {
        lifecycleScope.launch {
            refreshStatus()
            val ok = HandModelManager.download(this@LauncherActivity)
            refreshStatus()
            if (ok) {
                txtStatus.append("\nConcluído — o hand tracking funcionará no modo VR.")
            }
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
