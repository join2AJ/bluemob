package com.bluemob.app.bot

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** A language model Sky can download and run on the phone. */
data class AiModel(
    val id: String,
    val title: String,
    val blurb: String,
    val file: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val family: AiFamily,
    /** Tokens the model can hold at once (question, recent chat and answer together). */
    val contextTokens: Int,
    /** Phone memory it needs to run comfortably. */
    val minRamGb: Int,
)

/** How a model expects a conversation to be written out. */
enum class AiFamily { QWEN, GEMMA, OTHER }

object AiModels {
    private const val HF = "https://huggingface.co/litert-community"
    val LITE = AiModel(
        "qwen2.5-0.5b", "Lite", "Small and quick. Good for simple questions and short help.",
        "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        "$HF/Qwen2.5-0.5B-Instruct/resolve/6c237a59eedeb06a821b21f0a59b03d346ac8bc3/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        546_660_344, "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2", AiFamily.QWEN, 1280, 3,
    )
    val STANDARD = AiModel(
        "qwen2.5-1.5b", "Standard", "Smarter, longer answers: explaining, writing, planning, maths.",
        "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
        "$HF/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
        1_598_556_720, "82968d0a6c3872cf016fdbcfc591571605f4c7fd2b0f64d2533df502cc6596b3", AiFamily.QWEN, 4096, 6,
    )
    val ALL = listOf(LITE, STANDARD)

    fun familyOf(fileName: String): AiFamily = fileName.lowercase().let {
        when { "gemma" in it -> AiFamily.GEMMA; "qwen" in it -> AiFamily.QWEN; else -> AiFamily.OTHER }
    }
}

/** Writing the conversation out for the model, and tidying what comes back. No Android here, so it's unit-tested. */
object AiPrompt {
    const val SYSTEM = "You are Sky, a friendly assistant inside BlueMob, an app for messaging and SOS over Bluetooth and Wi-Fi when there is no " +
        "mobile network. You run fully offline on the user's phone. Help with whatever they ask: questions, explanations, writing, plans, maths, " +
        "learning, first aid and survival. Keep answers short and clear for a phone screen, in the user's language. If you are not sure, say so " +
        "instead of guessing. If someone may be in danger, tell them first to call emergency services if they can and to press BlueMob's SOS " +
        "button, which reaches nearby phones without signal."

    /** One earlier line of the Sky chat: [fromMe] is the user. */
    data class Turn(val fromMe: Boolean, val text: String)

    /** Roughly how many characters fit in [tokens] (about 3.5 per token for English; less for other scripts, so be careful). */
    fun charsFor(tokens: Int) = tokens * 3

    /**
     * The full prompt: instructions, as much recent chat as fits in [contextTokens] (leaving room for the answer), then
     * the question, ending where the model should start answering.
     */
    fun build(family: AiFamily, history: List<Turn>, question: String, contextTokens: Int): String {
        val answerRoom = if (contextTokens >= 2048) 768 else 480
        var budget = charsFor(contextTokens - answerRoom) - SYSTEM.length - question.length
        val kept = ArrayList<Turn>()
        for (t in history.asReversed()) {
            val text = t.text.trim().take(800)
            if (text.isEmpty()) continue
            if (text.length > budget) break
            budget -= text.length + 40
            kept.add(0, t.copy(text = text))
        }
        // A conversation starts with the user.
        while (kept.isNotEmpty() && !kept.first().fromMe) kept.removeAt(0)
        val q = question.trim()
        return when (family) {
            AiFamily.QWEN -> buildString {
                append("<|im_start|>system\n").append(SYSTEM).append("<|im_end|>\n")
                kept.forEach { append(if (it.fromMe) "<|im_start|>user\n" else "<|im_start|>assistant\n").append(it.text).append("<|im_end|>\n") }
                append("<|im_start|>user\n").append(q).append("<|im_end|>\n<|im_start|>assistant\n")
            }
            // Gemma has no system role: the instructions go in front of the first question.
            AiFamily.GEMMA -> buildString {
                val turns = kept + Turn(true, q)
                turns.forEachIndexed { i, t ->
                    append(if (t.fromMe) "<start_of_turn>user\n" else "<start_of_turn>model\n")
                    if (i == 0) append(SYSTEM).append("\n\n")
                    append(t.text).append("<end_of_turn>\n")
                }
                append("<start_of_turn>model\n")
            }
            AiFamily.OTHER -> buildString {
                append(SYSTEM).append("\n\n")
                kept.forEach { append(if (it.fromMe) "User: " else "Sky: ").append(it.text).append("\n") }
                append("User: ").append(q).append("\nSky:")
            }
        }
    }

    private val special = Regex("<\\|im_(start|end)\\|>(assistant|user|system)?|<\\|endoftext\\|>|<(start|end)_of_turn>(model|user)?|<eos>|</s>")

    /** The answer without any control tokens the model let slip, or a made-up next question from the user. */
    fun clean(raw: String): String {
        var t = raw.replace(special, "")
        for (stop in listOf("\nUser:", "\nuser\n")) t = t.substringBefore(stop)
        return t.trim()
    }
}

/**
 * Sky's optional offline AI. Nothing is downloaded unless the user asks; then one model file (550 MB or 1.6 GB) is
 * fetched with Android's download manager, checked against its known fingerprint, and run on the phone with
 * MediaPipe. Questions never leave the phone. The model is loaded only while it's in use and let go after a few idle
 * minutes, so it doesn't hold on to memory.
 */
class OfflineAi(private val context: Context, private val scope: CoroutineScope) {
    data class Download(val model: AiModel, val done: Long, val total: Long, val waitingForWifi: Boolean, val checking: Boolean = false)

    data class Status(
        /** The installed model: a catalogue entry, or a file the user brought in. */
        val installed: String? = null,
        val installedBytes: Long = 0,
        val enabled: Boolean = true,
        val download: Download? = null,
        val error: String? = null,
        /** True while Sky is thinking with the model. */
        val busy: Boolean = false,
    ) {
        val ready get() = installed != null && enabled
    }

    private val prefs = context.getSharedPreferences("offline_ai", Context.MODE_PRIVATE)
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val lock = Mutex()
    private var engine: LlmInference? = null
    private var idle: Job? = null
    private var watcher: Job? = null

    init {
        if (prefs.getLong("download_id", -1L) >= 0) watch()
    }

    fun dir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "ai").apply { mkdirs() }

    /** The phone's memory in GB, to suggest the right model. */
    fun ramGb(): Int {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return ((info.totalMem + 512L * 1024 * 1024) / (1024L * 1024 * 1024)).toInt()
    }

    fun freeBytes(): Long = dir().usableSpace

    private fun readStatus(): Status {
        val path = prefs.getString("model_path", null)
        val file = path?.let(::File)?.takeIf { it.exists() }
        return Status(
            installed = file?.let { prefs.getString("model_title", it.name) },
            installedBytes = file?.length() ?: 0,
            enabled = prefs.getBoolean("enabled", true),
        )
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        _status.update { it.copy(enabled = on) }
        if (!on) scope.launch { release() }
    }

    /** Starts downloading [model]. With [wifiOnly], it waits for Wi-Fi instead of using mobile data. */
    fun download(model: AiModel, wifiOnly: Boolean) {
        cancelDownload()
        val part = File(dir(), model.file + ".part").apply { delete() }
        val req = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("Sky offline AI (${model.title})")
            .setDescription("One-time download, then Sky's AI works with no internet")
            .setDestinationUri(Uri.fromFile(part))
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val id = dm.enqueue(req)
        prefs.edit().putLong("download_id", id).putString("download_model", model.id).apply()
        _status.update { it.copy(download = Download(model, 0, model.bytes, false), error = null) }
        watch()
    }

    fun cancelDownload() {
        val id = prefs.getLong("download_id", -1L)
        if (id >= 0) dm.remove(id)
        watcher?.cancel()
        prefs.edit().remove("download_id").remove("download_model").apply()
        dir().listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        _status.update { it.copy(download = null) }
    }

    private fun watch() {
        watcher?.cancel()
        watcher = scope.launch(Dispatchers.IO) {
            val id = prefs.getLong("download_id", -1L)
            val model = AiModels.ALL.firstOrNull { it.id == prefs.getString("download_model", null) } ?: return@launch cancelDownload()
            while (true) {
                val c = dm.query(DownloadManager.Query().setFilterById(id))
                val row = c?.use { if (it.moveToFirst()) Triple(it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))) else null }
                if (row == null) { fail("The download was stopped. Tap Download to try again."); return@launch }
                val (st, done, reason) = row
                when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> { finish(model); return@launch }
                    DownloadManager.STATUS_FAILED -> {
                        fail(if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) "Not enough free space on the phone." else "The download failed (error $reason). Tap Download to try again.")
                        return@launch
                    }
                    else -> _status.update { it.copy(download = Download(model, done, model.bytes,
                        waitingForWifi = st == DownloadManager.STATUS_PAUSED && reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI)) }
                }
                delay(1_000)
            }
        }
    }

    private fun fail(msg: String) {
        prefs.edit().remove("download_id").remove("download_model").apply()
        dir().listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        _status.update { it.copy(download = null, error = msg) }
    }

    /** Checks the downloaded file is exactly the model we asked for, then puts it in place. */
    private suspend fun finish(model: AiModel) {
        _status.update { it.copy(download = Download(model, model.bytes, model.bytes, false, checking = true)) }
        val part = File(dir(), model.file + ".part")
        val ok = part.length() == model.bytes && sha256(part) == model.sha256
        if (!ok) return fail("The downloaded file was damaged. Tap Download to try again.")
        val dest = File(dir(), model.file)
        removeModelFiles(except = part)
        if (!part.renameTo(dest)) return fail("Couldn't save the model. Free some space and try again.")
        install(dest, model.title, model.family, model.contextTokens)
        prefs.edit().remove("download_id").remove("download_model").apply()
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input -> val buf = ByteArray(1 shl 20); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun install(file: File, title: String, family: AiFamily, contextTokens: Int) {
        release()
        prefs.edit().putString("model_path", file.path).putString("model_title", title).putString("family", family.name)
            .putInt("context", contextTokens).putBoolean("enabled", true).putString("gpu", "untested").apply()
        _status.value = readStatus()
    }

    /** Brings in a model file the user already has (for example Gemma from Hugging Face). */
    suspend fun import(uri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        val clean = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!clean.endsWith(".task") && !clean.endsWith(".bin")) {
            _status.update { it.copy(error = "Choose a MediaPipe model file (.task).") }
            return@withContext false
        }
        _status.update { it.copy(error = null, download = Download(AiModel("import", clean, "", clean, "", 0, "", AiModels.familyOf(clean), 2048, 4), 0, 0, false, checking = true)) }
        val dest = File(dir(), "imported-$clean")
        val ok = runCatching { context.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { i.copyTo(it, 1 shl 20) } } }.isSuccess
        _status.update { it.copy(download = null) }
        if (!ok || dest.length() < 10_000_000) {
            dest.delete()
            _status.update { it.copy(error = "Couldn't read that file.") }
            return@withContext false
        }
        removeModelFiles(except = dest)
        val ctx = Regex("ekv(\\d+)").find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: 1280
        install(dest, clean.removeSuffix(".task").removeSuffix(".bin"), AiModels.familyOf(clean), ctx)
        true
    }

    /** Deletes the model and frees its space. */
    suspend fun delete() {
        release()
        removeModelFiles(except = null)
        prefs.edit().remove("model_path").remove("model_title").remove("family").remove("context").apply()
        _status.value = readStatus()
    }

    private fun removeModelFiles(except: File?) {
        dir().listFiles()?.filter { it != except && !it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    private suspend fun release() = lock.withLock {
        idle?.cancel()
        runCatching { engine?.close() }
        engine = null
    }

    private fun load(path: String, contextTokens: Int): LlmInference {
        engine?.let { return it }
        fun make(backend: LlmInference.Backend) = LlmInference.createFromOptions(context,
            LlmInference.LlmInferenceOptions.builder().setModelPath(path).setMaxTokens(contextTokens.coerceAtMost(2048)).setPreferredBackend(backend).build())
        // The GPU is faster but not every phone's driver copes. Try it once; if the app died while trying, stay on the CPU.
        val gpu = prefs.getString("gpu", "untested")
        val made = if (gpu == "untested" || gpu == "ok") {
            prefs.edit().putString("gpu", "trying").commit()
            runCatching { make(LlmInference.Backend.GPU) }.getOrNull().also { if (it == null) prefs.edit().putString("gpu", "bad").apply() }
        } else {
            if (gpu == "trying") prefs.edit().putString("gpu", "bad").apply()
            null
        }
        return (made ?: make(LlmInference.Backend.CPU)).also { engine = it }
    }

    /**
     * Sky's answer to [question], written by the model on this phone. [onPartial] gets the answer so far as it's
     * written. Null if no model is ready or it failed.
     */
    suspend fun answer(question: String, history: List<AiPrompt.Turn>, onPartial: (String) -> Unit): String? {
        val st = _status.value
        if (!st.ready) return null
        val path = prefs.getString("model_path", null) ?: return null
        val family = runCatching { AiFamily.valueOf(prefs.getString("family", "OTHER")!!) }.getOrDefault(AiFamily.OTHER)
        val ctx = prefs.getInt("context", 2048).coerceAtMost(2048)
        return lock.withLock {
            idle?.cancel()
            _status.update { it.copy(busy = true, error = null) }
            try {
                withContext(Dispatchers.Default) {
                    val llm = load(path, ctx)
                    // The prompt is written out in full here, so the model file's own template is switched off.
                    val none = PromptTemplates.builder().setUserPrefix("").setUserSuffix("").setModelPrefix("").setModelSuffix("")
                        .setSystemPrefix("").setSystemSuffix("").build()
                    val session = LlmInferenceSession.createFromOptions(llm, LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(40).setTopP(0.95f).setTemperature(0.6f).setPromptTemplates(none).build())
                    try {
                        session.addQueryChunk(AiPrompt.build(family, history, question, ctx))
                        val sb = StringBuilder()
                        val future = session.generateResponseAsync { partial, _ -> sb.append(partial); onPartial(AiPrompt.clean(sb.toString())) }
                        try { runInterruptible(Dispatchers.IO) { future.get() } } catch (e: CancellationException) { session.cancelGenerateResponseAsync(); throw e }
                        if (prefs.getString("gpu", "") == "trying") prefs.edit().putString("gpu", "ok").apply()
                        AiPrompt.clean(sb.toString()).ifBlank { null }
                    } finally { runCatching { session.close() } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _status.update { it.copy(error = "The offline AI stopped: ${e.message ?: e.javaClass.simpleName}") }
                runCatching { engine?.close() }
                engine = null
                null
            } finally {
                _status.update { it.copy(busy = false) }
                idle = scope.launch { delay(4 * 60_000); release() }
            }
        }
    }
}
