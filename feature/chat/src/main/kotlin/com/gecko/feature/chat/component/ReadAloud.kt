package com.gecko.feature.chat.component

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

internal interface ReadAloudPlayback {
    val speakingId: String?
    fun toggle(id: String, text: String)
}

internal val LocalReadAloud = staticCompositionLocalOf<ReadAloudPlayback?> { null }

@Composable
internal fun rememberReadAloud(): ReadAloudController {
    val context = LocalContext.current.applicationContext
    val controller = remember(context) { ReadAloudController(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); controller.close() }
    }
    return controller
}

/** One engine per chat. Engine callbacks are marshalled onto the Compose/main thread. */
internal class ReadAloudController(private val context: Context) : ReadAloudPlayback {
    override var speakingId by mutableStateOf<String?>(null)
        private set
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var closed = false
    private var generation = 0
    private var chunks = emptyList<String>()
    private var index = 0
    private val engine = TextToSpeech(context) { status ->
        main.post { if (!closed) ready = status == TextToSpeech.SUCCESS }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { main.post {
                if (!closed && id == utteranceId()) {
                    index++
                    if (index < chunks.size) speakChunk() else stop()
                }
            } }
            @Deprecated("Required by the Android TTS listener")
            override fun onError(id: String?) { main.post {
                if (!closed && id == utteranceId()) fail("Read aloud isn't available right now. Check your device's speech settings.")
            } }
        })
    }

    override fun toggle(id: String, text: String) {
        if (speakingId == id) { stop(); return }
        stop()
        if (!ready) { fail("Speech isn't ready. Check that a text-to-speech engine is installed, then try again."); return }
        if (engine.setLanguage(Locale.getDefault()) < TextToSpeech.LANG_AVAILABLE) {
            fail("Install a speech voice for your device language in Android settings."); return
        }
        chunks = speechChunks(text, TextToSpeech.getMaxSpeechInputLength() - 1)
        if (chunks.isEmpty()) return
        speakingId = id
        speakChunk()
    }

    private fun utteranceId() = "$generation:$index"
    private fun speakChunk() {
        if (engine.speak(chunks[index], TextToSpeech.QUEUE_FLUSH, null, utteranceId()) == TextToSpeech.ERROR)
            fail("Couldn't read this answer aloud. Try again.")
    }
    private fun fail(message: String) {
        stop()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    fun stop() {
        generation++
        engine.stop()
        speakingId = null
        chunks = emptyList()
        index = 0
    }
    fun close() { closed = true; stop(); engine.shutdown(); main.removeCallbacksAndMessages(null) }
}

/** Preserve text while respecting the engine limit, including UTF-16 surrogate pairs. */
internal fun speechChunks(text: String, limit: Int): List<String> {
    require(limit >= 2)
    val cleaned = text.replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
        .replace(Regex("!?\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace("**", "").replace("`", "").trim()
    return buildList {
        var start = 0
        while (start < cleaned.length) {
            var end = minOf(start + limit, cleaned.length)
            if (end < cleaned.length && cleaned[end - 1].isHighSurrogate()) end--
            if (end < cleaned.length) {
                val space = cleaned.lastIndexOf(' ', end - 1)
                if (space > start + limit / 2) end = space + 1
            }
            add(cleaned.substring(start, end))
            start = end
        }
    }
}
