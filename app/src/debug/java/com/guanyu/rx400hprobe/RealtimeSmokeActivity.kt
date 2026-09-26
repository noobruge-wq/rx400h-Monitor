package com.guanyu.rx400hprobe

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import java.io.File
import java.nio.file.Files
import org.json.JSONObject

/** Opt-in debug correctness fixtures only. Never touches real probe_sessions. */
class RealtimeSmokeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val results = JSONObject()
        fun test(name: String, check: () -> Unit) {
            results.put(name, runCatching(check).fold({ "PASS" }, { "FAIL: ${it.message}" }))
        }
        val base = Files.createTempDirectory(cacheDir.toPath(), "d064-fixture-").toFile()
        fun session(case: String, status: String = "active"): Pair<File, File> {
            val appFiles = File(base, case).apply { mkdirs() }
            val dir = File(appFiles, "probe_sessions/RX400h_fixture").apply { mkdirs() }
            File(dir, "session.json").writeText(JSONObject().put("session_id", dir.name)
                .put("application_id", "com.guanyu.rx400hprobe.debug").put("status", status)
                .put("archive_name", JSONObject.NULL).put("ended_at", JSONObject.NULL)
                .put("evidence_complete", false).toString())
            File(dir, "raw_io.jsonl").writeText("fixture")
            return appFiles to dir
        }
        test("known_unsaved_deleted") {
            val (app, dir) = session("unsaved")
            LegacySessionCleanup.clean(app)
            check(!dir.exists())
        }
        test("saved_marker_preserved") {
            val (app, dir) = session("saved")
            File(dir, "public_export.json").writeText("{}")
            LegacySessionCleanup.clean(app)
            check(dir.exists())
        }
        test("complete_preserved") {
            val (app, dir) = session("complete", "completed")
            LegacySessionCleanup.clean(app)
            check(dir.exists())
        }
        test("ambiguous_metadata_preserved") {
            val (app, dir) = session("ambiguous")
            File(dir, "session.json").writeText("{")
            LegacySessionCleanup.clean(app)
            check(dir.exists())
        }
        test("partial_save_preserved") {
            val (app, dir) = session("partial")
            File(dir, "finalize_intent.json").writeText("{}")
            LegacySessionCleanup.clean(app)
            check(dir.exists())
        }
        test("foreign_child_preserved") {
            val (app, dir) = session("foreign")
            File(dir, "unknown.dat").writeText("keep")
            LegacySessionCleanup.clean(app)
            check(dir.exists())
        }
        test("symlink_preserved_without_following") {
            val (app, dir) = session("link")
            val outside = File(base, "outside.txt").apply { writeText("keep") }
            Files.createSymbolicLink(File(dir, "decoded.jsonl").toPath(), outside.toPath())
            LegacySessionCleanup.clean(app)
            check(dir.exists() && outside.readText() == "keep")
        }
        test("root_alias_preserved") {
            val app = File(base, "root-alias").apply { mkdirs() }
            val (_, dir) = session("alias-target")
            Files.createSymbolicLink(File(app, "probe_sessions").toPath(), requireNotNull(dir.parentFile).toPath())
            LegacySessionCleanup.clean(app)
            check(dir.exists())
        }
        test("fresh_install_creates_no_log_root") {
            val app = File(base, "fresh").apply { mkdirs() }
            LegacySessionCleanup.clean(app)
            check(app.listFiles()!!.isEmpty())
        }
        test("retired_buttons_and_modal_hitboxes") {
            var reconnects = 0
            var devices = 0
            val view = PixelDashboardView(this, { devices++ }, { reconnects++ })
            view.layout(0, 0, 1280, 720)
            fun tap(x: Float, y: Float) {
                for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(0, 0, action, x, y, 0)
                    view.onTouchEvent(event)
                    event.recycle()
                }
            }
            for (x in floatArrayOf(323f, 503f, 683f)) tap(x, 57f)
            check(reconnects == 0 && devices == 0)
            tap(636f, 346f)
            check(reconnects == 1)
            view.setConnectionBusy(true)
            tap(636f, 346f)
            check(reconnects == 1)
            view.setConnectionBusy(false)
            tap(863f, 57f) // settings
            tap(636f, 346f) // within modal, must not reach vehicle hitbox
            check(reconnects == 1)
            tap(760f, 162f)
            check(devices == 1)
            tap(965f, 110f) // close
            tap(636f, 346f)
            check(reconnects == 2)
        }
        Log.i("RX400hRealtimeSmoke", "COMPLETE " + JSONObject()
            .put("run_id", intent.getStringExtra("smoke_run_id"))
            .put("checks", results)
            .put("passed", results.keys().asSequence().count { results.getString(it) == "PASS" })
            .put("failed", results.keys().asSequence().count { results.getString(it) != "PASS" }))
        // Fixture directories are retained for inspection; no broad cleanup.
        finish()
    }
}
