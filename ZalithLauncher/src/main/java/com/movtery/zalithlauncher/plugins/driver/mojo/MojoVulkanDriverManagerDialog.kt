package com.movtery.zalithlauncher.plugins.driver.mojo

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.util.concurrent.Executors

/** Mojo-style Installed Drivers dialog: ADD import, REMOVE edit mode, per-driver USE/IN USE. */
class MojoVulkanDriverManagerDialog : DialogFragment() {
    private lateinit var rows: LinearLayout
    private var editing = false
    private val executor = Executors.newSingleThreadExecutor()

    private val openDriverArchive = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val app = requireContext().applicationContext
        Toast.makeText(app, "Validating driver package…", Toast.LENGTH_SHORT).show()
        executor.execute {
            val result = MojoVulkanDriverManager.installFromUri(app, uri)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                when (result) {
                    is MojoDriverInstallResult.Success -> {
                        val suffix = if (result.replaced) " (updated)" else ""
                        Toast.makeText(requireContext(), "Installed ${result.driver.name}$suffix. Select it to use it.", Toast.LENGTH_LONG).show()
                    }
                    is MojoDriverInstallResult.Failure -> Toast.makeText(requireContext(), "Driver import failed: ${result.reason}", Toast.LENGTH_LONG).show()
                }
                refreshRows()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val scroll = ScrollView(context)
        rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, dp(420)))
        val dialog = AlertDialog.Builder(context)
            .setTitle("Installed Drivers")
            .setView(outer)
            .setNeutralButton("REMOVE", null)
            .setNegativeButton("CLOSE", null)
            .setPositiveButton("ADD", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                openDriverArchive.launch(arrayOf("*/*"))
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                editing = !editing
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text = if (editing) "DONE" else "REMOVE"
                refreshRows()
            }
        }
        refreshRows()
        return dialog
    }

    private fun refreshRows() {
        if (!::rows.isInitialized || !isAdded) return
        rows.removeAllViews()
        val context = requireContext()
        val drivers = MojoVulkanDriverManager.getDrivers(context)
        for (driver in drivers) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val title = TextView(context).apply {
                text = driver.name
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            }
            val detail = TextView(context).apply {
                text = driver.detailLine()
                textSize = 13f
                alpha = 0.80f
            }
            texts.addView(title)
            texts.addView(detail)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            if (!editing) {
                val use = Button(context).apply {
                    val inUse = MojoVulkanDriverManager.isPreferred(driver)
                    text = if (inUse) "IN USE" else "USE"
                    isEnabled = !inUse
                    setOnClickListener {
                        MojoVulkanDriverManager.setPreferred(driver)
                        Toast.makeText(context, "${driver.name} selected. Force-stop and reopen Zalith before launching Minecraft.", Toast.LENGTH_LONG).show()
                        refreshRows()
                    }
                }
                actions.addView(use)
            } else if (!driver.builtIn) {
                val remove = Button(context).apply {
                    text = "REMOVE"
                    setOnClickListener { confirmRemove(driver) }
                }
                actions.addView(remove)
            }
            row.addView(actions)
            rows.addView(row)
            val divider = View(context).apply {
                setBackgroundColor(0x33777777)
            }
            rows.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun confirmRemove(driver: MojoVulkanDriver) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove driver?")
            .setMessage("Remove ${driver.name} from Zalith's private driver storage? This cannot be undone.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("REMOVE") { _, _ ->
                val context = requireContext().applicationContext
                executor.execute {
                    val ok = MojoVulkanDriverManager.removeDriver(context, driver)
                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        Toast.makeText(requireContext(), if (ok) "Driver removed" else "Could not remove driver", Toast.LENGTH_SHORT).show()
                        refreshRows()
                    }
                }
            }
            .show()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
