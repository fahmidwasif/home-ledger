package nz.afhome.ledger.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nz.afhome.ledger.data.Prefs
import java.io.File

/**
 * Wrapper around LiteRT-LM running a Gemma model fully on the phone.
 * The model file (e.g. Gemma 3 1B IT, ~550 MB, `.litertlm`) is imported once from phone storage and
 * copied into the app's private folder. Nothing is sent anywhere.
 */
class LocalLlm(private val context: Context, private val prefs: Prefs) {

    enum class State { NO_MODEL, IDLE, LOADING, READY, ERROR }

    private val _state = MutableStateFlow(if (modelFile()?.exists() == true) State.IDLE else State.NO_MODEL)
    val state: StateFlow<State> = _state
    var lastError: String? = null
        private set

    private var engine: Engine? = null
    private val lock = Mutex()

    fun modelFile(): File? = prefs.modelPath?.let(::File)

    val modelName: String? get() = modelFile()?.name

    /** Copies a model the user picked (Downloads, SD card…) into private storage. */
    suspend fun importModel(uri: Uri, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: "model.litertlm"
            require(name.endsWith(".litertlm") || name.endsWith(".task")) {
                "Please pick a .litertlm model file (Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm)."
            }
            require(!isChipSpecific(name)) {
                "\"$name\" is built for a different phone's AI chip and won't run on the GT Master (Snapdragon 778G). " +
                    "Download the general file instead: Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm or gemma-4-E2B-it.litertlm."
            }
            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            val dir = File(context.filesDir, "models").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, name)
            context.contentResolver.openInputStream(uri)!!.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        copied += n
                        if (size > 0) onProgress(copied.toFloat() / size)
                    }
                }
            }
            unload()
            prefs.modelPath = out.absolutePath
            _state.value = State.IDLE
            out
        }
    }

    /** NPU builds are compiled for one chip family (e.g. _sm8850, _mt6991, _Google_Tensor_G5) and fail on others. */
    private fun isChipSpecific(name: String): Boolean =
        Regex("""_(sm\d{4}|mt\d{4}|qualcomm|google_tensor|intel|qcs\d+)""", RegexOption.IGNORE_CASE).containsMatchIn(name)

    fun removeModel() {
        unload()
        modelFile()?.delete()
        prefs.modelPath = null
        _state.value = State.NO_MODEL
    }

    private suspend fun ensureEngine(): Engine = lock.withLock {
        engine?.let { return it }
        val file = modelFile()?.takeIf { it.exists() } ?: error("No AI model installed yet. Add one in Settings → On-device AI.")
        _state.value = State.LOADING
        try {
            val e = withContext(Dispatchers.Default) {
                Engine(
                    EngineConfig(
                        modelPath = file.absolutePath,
                        backend = if (prefs.useGpu) Backend.GPU() else Backend.CPU(),
                        maxNumTokens = 4096,
                        cacheDir = context.cacheDir.absolutePath,
                    )
                ).also { it.initialize() }
            }
            engine = e
            _state.value = State.READY
            return e
        } catch (t: Throwable) {
            lastError = friendly(t.message, file.name)
            _state.value = State.ERROR
            throw IllegalStateException(lastError, t)
        }
    }

    private fun friendly(msg: String?, file: String): String = when {
        isChipSpecific(file) || msg?.contains("Input tensor not found") == true ->
            "This model file doesn't fit this phone (it's built for a different chip). Remove it and install " +
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm or gemma-4-E2B-it.litertlm."
        prefs.useGpu -> "The model couldn't start on the GPU. Turn off \"Use GPU\" in Settings and try again. (${msg?.lineSequence()?.firstOrNull()})"
        else -> msg?.lineSequence()?.firstOrNull() ?: "Unknown error"
    }

    /** One-shot generation with a fresh conversation (no memory between calls). */
    suspend fun ask(system: String, prompt: String, temperature: Double = 0.4): String {
        val e = ensureEngine()
        return withContext(Dispatchers.Default) {
            e.createConversation(config(system, temperature)).use { conv -> conv.sendMessage(prompt).text() }
        }
    }

    /** Streams the answer piece by piece for the chat screen. */
    suspend fun stream(system: String, prompt: String): Flow<String> {
        val e = ensureEngine()
        return flow {
            e.createConversation(config(system, 0.6)).use { conv -> emitAll(conv.sendMessageAsync(prompt).map { it.text() }) }
        }.flowOn(Dispatchers.Default)
    }

    private fun config(system: String, temperature: Double) = ConversationConfig(
        systemInstruction = Contents.of(system),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature, seed = 0),
    )

    fun unload() {
        engine?.close()
        engine = null
        if (_state.value == State.READY) _state.value = State.IDLE
    }

    private fun Message.text(): String = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
}
