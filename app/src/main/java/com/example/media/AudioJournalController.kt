package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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

private const val TAG = "AudioJournalController"

data class RecorderState(
    val recordingActive: Boolean = false,
    val elapsedSeconds: Int = 0,
    val amplitudeLevel: Float = 0f,
    val recordedFile: File? = null,
    val recordedFileName: String? = null,
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

    private val audioDir: File = File(appContext.filesDir, "audio_journals").apply {
        if (!exists()) mkdirs()
    }

    private var mediaRecorder: MediaRecorder? = null
    private var recordMeterJob: Job? = null
    private var currentRecordFile: File? = null

    private val _recorderState = MutableStateFlow(RecorderState())
    val recorderState: StateFlow<RecorderState> = _recorderState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playerProgressJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
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
            }
        }
    }

    fun getAudioFile(fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val file = File(audioDir, fileName)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun startRecording() {
        stopPlayback()
        stopSpeaking()
        try {
            if (!audioDir.exists()) audioDir.mkdirs()
            val fileName = "diary_${System.currentTimeMillis()}.m4a"
            val outputFile = File(audioDir, fileName)
            currentRecordFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _recorderState.value = RecorderState(
                recordingActive = true,
                elapsedSeconds = 0,
                amplitudeLevel = 0.1f,
                recordedFile = null,
                recordedFileName = null,
                errorMessage = null
            )

            val startMillis = System.currentTimeMillis()
            recordMeterJob?.cancel()
            recordMeterJob = scope.launch {
                while (isActive) {
                    val elapsed = ((System.currentTimeMillis() - startMillis) / 1000L).toInt()
                    val rawAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    val normalized = (rawAmp / 16000f).coerceIn(0.08f, 1.0f)
                    _recorderState.value = _recorderState.value.copy(
                        elapsedSeconds = elapsed,
                        amplitudeLevel = normalized
                    )
                    if (elapsed >= 295) { // Keep safely under 5 minutes
                        stopRecording()
                        break
                    }
                    delay(120L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanupRecorderOnly()
            _recorderState.value = RecorderState(
                errorMessage = "Could not start the microphone. Please check microphone permissions."
            )
        }
    }

    fun stopRecording() {
        recordMeterJob?.cancel()
        recordMeterJob = null
        val duration = _recorderState.value.elapsedSeconds.coerceAtLeast(1)
        val file = currentRecordFile
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop error", e)
        } finally {
            cleanupRecorderOnly()
        }

        if (file != null && file.exists() && file.length() > 0L) {
            _recorderState.value = RecorderState(
                recordingActive = false,
                elapsedSeconds = duration,
                amplitudeLevel = 0f,
                recordedFile = file,
                recordedFileName = file.name,
                errorMessage = null
            )
        } else {
            _recorderState.value = RecorderState(
                errorMessage = "Recording was too short. Please tap Start Recording and speak for a few seconds."
            )
        }
    }

    fun resetRecorder(deleteUnsavedFile: Boolean = false) {
        recordMeterJob?.cancel()
        recordMeterJob = null
        cleanupRecorderOnly()
        if (deleteUnsavedFile) {
            try {
                _recorderState.value.recordedFile?.delete()
            } catch (_: Exception) {
            }
        }
        currentRecordFile = null
        _recorderState.value = RecorderState()
    }

    private fun cleanupRecorderOnly() {
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
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
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
                    val total = player.duration.coerceAtLeast(1)
                    val pos = player.currentPosition.coerceIn(0, total)
                    _playerState.value = _playerState.value.copy(
                        progressFraction = pos.toFloat() / total.toFloat()
                    )
                    delay(150L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playback error", e)
            stopPlayback()
            _playerState.value = PlayerState(errorMessage = "Unable to play this audio recording.")
        }
    }

    fun stopPlayback() {
        playerProgressJob?.cancel()
        playerProgressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _playerState.value = PlayerState()
    }

    fun toggleSpeak(id: String, text: String) {
        if (_speakingId.value == id) {
            stopSpeaking()
            return
        }
        stopPlayback()
        if (!ttsReady) return
        _speakingId.value = id
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stopSpeaking() {
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
        try {
            File(audioDir, fileName).delete()
        } catch (_: Exception) {
        }
    }

    fun release() {
        resetRecorder(deleteUnsavedFile = false)
        stopPlayback()
        stopSpeaking()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
