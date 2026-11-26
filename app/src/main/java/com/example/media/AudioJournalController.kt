package com.example.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "AudioJournalController"
private const val MAX_RECORDING_SECONDS = 295 // Keep safely under 5 minutes for fast AI upload

data class RecorderState(
    val preparing: Boolean = false,
    val recordingActive: Boolean = false,
    val recordingPaused: Boolean = false,
    val elapsedSeconds: Int = 0,
    val amplitudeLevel: Float = 0f,
    val recordedFile: File? = null,
    val recordedFileName: String? = null,
    val recordedFileSizeBytes: Long = 0L,
    val errorMessage: String? = null
)

data class PlayerState(
    val activeFileName: String? = null,
    val playingActive: Boolean = false,
    val progressFraction: Float = 0f,
    val errorMessage: String? = null
)

class AudioJournalController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val audioDir: File by lazy {
        File(appContext.filesDir, "audio_journals")
    }

    private val recorderMutex = Mutex()
    private var mediaRecorder: MediaRecorder? = null
    private var recordMeterJob: Job? = null
    private var currentRecordFile: File? = null
    private var accumulatedRecordMillis: Long = 0L
    private var activeSegmentStartMillis: Long = 0L
    private var recordAudioFocusRequest: AudioFocusRequest? = null

    private val _recorderState = MutableStateFlow(RecorderState())
    val recorderState: StateFlow<RecorderState> = _recorderState.asStateFlow()

    private val playerMutex = Mutex()
    private var mediaPlayer: MediaPlayer? = null
    private var playerProgressJob: Job? = null
    private var playbackAudioFocusRequest: AudioFocusRequest? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitializing = false
    private var pendingSpeech: Pair<String, String>? = null
    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            stopPlayback()
        }
    }

    private fun requestAudioFocus(forRecording: Boolean) {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(
                        if (forRecording) AudioAttributes.USAGE_VOICE_COMMUNICATION
                        else AudioAttributes.USAGE_MEDIA
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                manager.requestAudioFocus(req)
                if (forRecording) {
                    recordAudioFocusRequest = req
                } else {
                    playbackAudioFocusRequest = req
                }
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request skipped", e)
        }
    }

    private fun abandonAudioFocus(forRecording: Boolean) {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = if (forRecording) recordAudioFocusRequest else playbackAudioFocusRequest
                if (req != null) {
                    manager.abandonAudioFocusRequest(req)
                }
                if (forRecording) {
                    recordAudioFocusRequest = null
                } else {
                    playbackAudioFocusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus abandon skipped", e)
        }
    }

    private fun ensureTtsInitialized() {
        if (ttsReady || ttsInitializing) return
        ttsInitializing = true
        tts = TextToSpeech(appContext) { status ->
            ttsInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(0.90f) // Calm, clear pace for dementia-friendly listening
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _speakingId.value = utteranceId
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_speakingId.value == utteranceId) {
                            _speakingId.value = null
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (_speakingId.value == utteranceId) {
                            _speakingId.value = null
                        }
                    }
                })
                ttsReady = true
                val queued = pendingSpeech
                pendingSpeech = null
                if (queued != null) {
                    _speakingId.value = queued.first
                    tts?.speak(queued.second, TextToSpeech.QUEUE_FLUSH, null, queued.first)
                }
            } else {
                pendingSpeech = null
                _speakingId.value = null
            }
        }
    }

    fun getAudioFile(fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val file = File(audioDir, fileName)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording() {
        if (!hasMicrophonePermission()) {
            _recorderState.value = RecorderState(
                errorMessage = "Microphone permission is required before recording."
            )
            return
        }

        stopPlayback()
        stopSpeaking()

        scope.launch {
            recorderMutex.withLock {
                if (mediaRecorder != null) return@withLock

                _recorderState.value = RecorderState(preparing = true)

                val result = withContext(Dispatchers.IO) {
                    try {
                        if (!audioDir.exists()) {
                            audioDir.mkdirs()
                        }
                        val fileName = "diary_${System.currentTimeMillis()}.m4a"
                        val outputFile = File(audioDir, fileName)
                        val recorder = createAndStartRecorderWithFallback(outputFile)
                        Result.success(recorder to outputFile)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                result.fold(
                    onSuccess = { (recorder, outputFile) ->
                        requestAudioFocus(forRecording = true)
                        mediaRecorder = recorder
                        currentRecordFile = outputFile
                        accumulatedRecordMillis = 0L
                        activeSegmentStartMillis = System.currentTimeMillis()

                        _recorderState.value = RecorderState(
                            preparing = false,
                            recordingActive = true,
                            recordingPaused = false,
                            elapsedSeconds = 0,
                            amplitudeLevel = 0.12f,
                            recordedFile = null,
                            recordedFileName = null,
                            recordedFileSizeBytes = 0L,
                            errorMessage = null
                        )
                        startAmplitudeMeter()
                    },
                    onFailure = { err ->
                        Log.e(TAG, "Failed to start microphone recording", err)
                        abandonAudioFocus(forRecording = true)
                        cleanupRecorderOnly()
                        _recorderState.value = RecorderState(
                            errorMessage = "Could not start the microphone. Please check microphone permissions or close other audio apps."
                        )
                    }
                )
            }
        }
    }

    /**
     * Configures and starts MediaRecorder using high-quality AAC mono first, with a voice-optimized
     * fallback (16kHz / VOICE_RECOGNITION) for devices or emulators that reject 44.1kHz MIC capture.
     */
    private fun createAndStartRecorderWithFallback(outputFile: File): MediaRecorder {
        val configs = listOf(
            Triple(MediaRecorder.AudioSource.MIC, 44_100, 96_000),
            Triple(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000, 64_000),
            Triple(MediaRecorder.AudioSource.DEFAULT, 16_000, 48_000)
        )
        var lastError: Exception? = null
        for ((source, sampleRate, bitRate) in configs) {
            var candidate: MediaRecorder? = null
            try {
                if (outputFile.exists()) outputFile.delete()
                candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(appContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                candidate.apply {
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioSamplingRate(sampleRate)
                    setAudioEncodingBitRate(bitRate)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                }
                return candidate
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Recorder config (source=$source, rate=$sampleRate) failed; trying fallback", e)
                try {
                    candidate?.reset()
                    candidate?.release()
                } catch (_: Exception) {
                }
            }
        }
        throw lastError ?: IllegalStateException("Microphone initialization failed.")
    }

    private fun startAmplitudeMeter() {
        recordMeterJob?.cancel()
        recordMeterJob = scope.launch {
            while (isActive) {
                val state = _recorderState.value
                if (!state.recordingActive) break

                if (state.recordingPaused) {
                    _recorderState.value = state.copy(amplitudeLevel = 0.08f)
                } else {
                    val elapsedMillis = accumulatedRecordMillis +
                        (System.currentTimeMillis() - activeSegmentStartMillis).coerceAtLeast(0L)
                    val elapsedSec = (elapsedMillis / 1000L).toInt()
                    val rawAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    val normalized = (rawAmp / 14000f).coerceIn(0.10f, 1.0f)
                    _recorderState.value = state.copy(
                        elapsedSeconds = elapsedSec,
                        amplitudeLevel = normalized
                    )
                    if (elapsedSec >= MAX_RECORDING_SECONDS) {
                        stopRecording()
                        break
                    }
                }
                delay(120L)
            }
        }
    }

    fun pauseRecording() {
        scope.launch {
            recorderMutex.withLock {
                val recorder = mediaRecorder ?: return@withLock
                val state = _recorderState.value
                if (!state.recordingActive || state.recordingPaused) return@withLock

                val pausedOk = withContext(Dispatchers.IO) {
                    try {
                        recorder.pause()
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to pause recording", e)
                        false
                    }
                }
                if (pausedOk) {
                    accumulatedRecordMillis +=
                        (System.currentTimeMillis() - activeSegmentStartMillis).coerceAtLeast(0L)
                    _recorderState.value = _recorderState.value.copy(
                        recordingPaused = true,
                        amplitudeLevel = 0.08f,
                        elapsedSeconds = (accumulatedRecordMillis / 1000L).toInt()
                    )
                }
            }
        }
    }

    fun resumeRecording() {
        scope.launch {
            recorderMutex.withLock {
                val recorder = mediaRecorder ?: return@withLock
                val state = _recorderState.value
                if (!state.recordingActive || !state.recordingPaused) return@withLock

                val resumedOk = withContext(Dispatchers.IO) {
                    try {
                        recorder.resume()
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resume recording", e)
                        false
                    }
                }
                if (resumedOk) {
                    activeSegmentStartMillis = System.currentTimeMillis()
                    _recorderState.value = _recorderState.value.copy(
                        recordingPaused = false,
                        amplitudeLevel = 0.14f
                    )
                }
            }
        }
    }

    /**
     * Stops the active microphone capture, flushes the `.m4a` audio file on Dispatchers.IO,
     * and optionally invokes [onRecorded] on the Main thread so the caller can immediately
     * run sentiment analysis on the resulting audio file.
     */
    fun stopRecording(onRecorded: ((File, Int) -> Unit)? = null) {
        recordMeterJob?.cancel()
        recordMeterJob = null

        scope.launch {
            val completed: Pair<File, Int>? = recorderMutex.withLock {
                val recorder = mediaRecorder
                val file = currentRecordFile
                val state = _recorderState.value

                if (recorder == null && file != null && file.exists() && file.length() > 0L) {
                    val existingDuration = state.elapsedSeconds.coerceAtLeast(1)
                    return@withLock file to existingDuration
                }

                if (!state.recordingPaused && state.recordingActive) {
                    accumulatedRecordMillis +=
                        (System.currentTimeMillis() - activeSegmentStartMillis).coerceAtLeast(0L)
                }
                val duration = (accumulatedRecordMillis / 1000L).toInt()
                    .coerceAtLeast(state.elapsedSeconds)
                    .coerceAtLeast(1)

                val stoppedCleanly = withContext(Dispatchers.IO) {
                    var ok = true
                    try {
                        recorder?.stop()
                    } catch (e: Exception) {
                        ok = false
                        Log.w(TAG, "MediaRecorder stop error (recording may have been too brief)", e)
                    } finally {
                        cleanupRecorderOnly()
                    }
                    ok && file != null && file.exists() && file.length() > 128L
                }

                abandonAudioFocus(forRecording = true)

                if (stoppedCleanly && file != null) {
                    val fileSize = file.length()
                    _recorderState.value = RecorderState(
                        preparing = false,
                        recordingActive = false,
                        recordingPaused = false,
                        elapsedSeconds = duration,
                        amplitudeLevel = 0f,
                        recordedFile = file,
                        recordedFileName = file.name,
                        recordedFileSizeBytes = fileSize,
                        errorMessage = null
                    )
                    file to duration
                } else {
                    withContext(Dispatchers.IO) {
                        try {
                            file?.delete()
                        } catch (_: Exception) {
                        }
                    }
                    currentRecordFile = null
                    _recorderState.value = RecorderState(
                        errorMessage = "Recording was too short. Please tap Start Recording and speak for a couple of seconds."
                    )
                    null
                }
            }

            if (completed != null && onRecorded != null) {
                onRecorded(completed.first, completed.second)
            }
        }
    }

    fun resetRecorder(deleteUnsavedFile: Boolean = false) {
        recordMeterJob?.cancel()
        recordMeterJob = null
        stopPlayback()
        scope.launch {
            recorderMutex.withLock {
                val recorder = mediaRecorder
                val fileToDelete = if (deleteUnsavedFile) {
                    _recorderState.value.recordedFile ?: currentRecordFile
                } else {
                    null
                }
                withContext(Dispatchers.IO) {
                    try {
                        recorder?.stop()
                    } catch (_: Exception) {
                    }
                    cleanupRecorderOnly()
                    if (fileToDelete != null) {
                        try {
                            fileToDelete.delete()
                        } catch (_: Exception) {
                        }
                    }
                }
                abandonAudioFocus(forRecording = true)
                currentRecordFile = null
                accumulatedRecordMillis = 0L
                activeSegmentStartMillis = 0L
                _recorderState.value = RecorderState()
            }
        }
    }

    private fun cleanupRecorderOnly() {
        try {
            mediaRecorder?.reset()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }

    fun togglePlayback(fileName: String) {
        stopSpeaking()
        val current = _playerState.value
        if (current.activeFileName == fileName && current.playingActive) {
            stopPlayback()
            return
        }

        val file = getAudioFile(fileName)
        if (file == null) {
            _playerState.value = PlayerState(
                errorMessage = "Audio recording is only saved on the device where it was recorded."
            )
            return
        }

        stopPlayback()
        scope.launch {
            playerMutex.withLock {
                val prepared = withContext(Dispatchers.IO) {
                    try {
                        val mp = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            setDataSource(file.absolutePath)
                            prepare()
                        }
                        Result.success(mp)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                prepared.fold(
                    onSuccess = { mp ->
                        requestAudioFocus(forRecording = false)
                        mp.setOnCompletionListener {
                            stopPlayback()
                        }
                        mp.start()
                        mediaPlayer = mp
                        _playerState.value = PlayerState(
                            activeFileName = fileName,
                            playingActive = true,
                            progressFraction = 0f,
                            errorMessage = null
                        )
                        playerProgressJob?.cancel()
                        playerProgressJob = scope.launch {
                            while (isActive && mediaPlayer != null) {
                                val player = mediaPlayer ?: break
                                val total = try {
                                    player.duration.coerceAtLeast(1)
                                } catch (_: Exception) {
                                    1
                                }
                                val pos = try {
                                    player.currentPosition.coerceIn(0, total)
                                } catch (_: Exception) {
                                    0
                                }
                                _playerState.value = _playerState.value.copy(
                                    progressFraction = pos.toFloat() / total.toFloat()
                                )
                                delay(150L)
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Playback error", e)
                        stopPlayback()
                        _playerState.value = PlayerState(
                            errorMessage = "Unable to play this audio recording."
                        )
                    }
                )
            }
        }
    }

    fun stopPlayback() {
        playerProgressJob?.cancel()
        playerProgressJob = null
        val player = mediaPlayer
        mediaPlayer = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        abandonAudioFocus(forRecording = false)
        _playerState.value = PlayerState()
    }

    fun toggleSpeak(id: String, text: String) {
        if (_speakingId.value == id) {
            stopSpeaking()
            return
        }
        stopPlayback()
        if (!ttsReady) {
            pendingSpeech = id to text
            _speakingId.value = id
            ensureTtsInitialized()
            return
        }
        _speakingId.value = id
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stopSpeaking() {
        pendingSpeech = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _speakingId.value = null
    }

    fun deleteLocalAudioFile(fileName: String?) {
        if (fileName.isNullOrBlank()) return
        if (_playerState.value.activeFileName == fileName) {
            stopPlayback()
        }
        scope.launch(Dispatchers.IO) {
            try {
                File(audioDir, fileName).delete()
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        recordMeterJob?.cancel()
        recordMeterJob = null
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        cleanupRecorderOnly()
        abandonAudioFocus(forRecording = true)
        stopPlayback()
        stopSpeaking()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
