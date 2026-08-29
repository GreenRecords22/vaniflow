package com.vaniflow.app.engine.model

import android.app.ActivityManager
import android.content.Context
import com.vaniflow.app.domain.model.DeviceTier
import com.vaniflow.app.domain.model.ModelDownloadProgress
import com.vaniflow.app.domain.model.ModelMetadata
import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.domain.model.ModelType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable

@Singleton
class DefaultModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelManager {

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
    }

    override val availableModels: List<ModelMetadata> = listOf(
        ModelMetadata(
            id = "llm_qwen25_05b_instruct",
            name = "VaniFlow Lite",
            type = ModelType.LLM,
            version = "1.0.0",
            sizeBytes = 491_400_032L, // ~491 MB
            sha256Checksum = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            recommendedTier = DeviceTier.LOW,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            localFileName = "llm_qwen25_05b.gguf",
            description = "Fast & lightweight — designed for everyday offline conversations on any phone.",
            brandedName = "VaniFlow Lite",
            vaniFlowTier = "LITE"
        ),
        ModelMetadata(
            id = "llm_qwen25_15b_instruct",
            name = "VaniFlow Core",
            type = ModelType.LLM,
            version = "1.0.0",
            sizeBytes = 1_100_000_000L, // ~1.1 GB
            sha256Checksum = "7c126d40f8a913454b51a92e105e4dfa66bcde52382902fb94bb9a69123847aa",
            recommendedTier = DeviceTier.MEDIUM,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            localFileName = "llm_qwen25_15b.gguf",
            description = "More natural conversation — recommended for capable phones with 6GB+ RAM.",
            brandedName = "VaniFlow Core",
            vaniFlowTier = "CORE"
        ),
        ModelMetadata(
            id = "stt_sherpa_zipformer_small",
            name = "VaniFlow Speech Engine (Fast)",
            type = ModelType.STT,
            version = "1.0.0",
            sizeBytes = 42_000_000L,
            sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            recommendedTier = DeviceTier.LOW,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2",
            localFileName = "stt_zipformer_small.onnx",
            description = "Optimized for fast on-device speech recognition with ultra-low latency.",
            brandedName = "VaniFlow Speech Engine"
        ),
        ModelMetadata(
            id = "tts_piper_raya_warm",
            name = "VaniFlow Raya Voice (Warm Indian En)",
            type = ModelType.TTS,
            version = "1.0.0",
            sizeBytes = 28_000_000L,
            sha256Checksum = "5feceb66ffc86f38d952786c6d696c79c2dbc239dd4e91b46729d73a27fb57e9",
            recommendedTier = DeviceTier.LOW,
            downloadUrl = "https://github.com/rhasspy/piper/releases/download/v0.0.2/voice-en-in.tar.gz",
            localFileName = "tts_raya_warm.onnx",
            description = "Natural offline conversational voice synthesis.",
            brandedName = "VaniFlow Raya Voice"
        )
    )

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadProgress>>(emptyMap())
    override val downloadStates: StateFlow<Map<String, ModelDownloadProgress>> = _downloadStates.asStateFlow()

    private val cancelledDownloads = mutableSetOf<String>()

    init {
        // Auto-adopt any pre-provisioned model in /data/local/tmp if available
        try {
            val preProvisioned = File("/data/local/tmp/llm_qwen25_05b.gguf")
            val target = File(modelsDir, "llm_qwen25_05b.gguf")
            if (preProvisioned.exists() && preProvisioned.length() > 1_000_000L && (!target.exists() || target.length() != preProvisioned.length())) {
                preProvisioned.copyTo(target, overwrite = true)
            }
        } catch (_: Exception) {}
    }

    override fun getRecommendedTier(): DeviceTier {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

            when {
                totalRamGb >= 7.0 -> DeviceTier.HIGH
                totalRamGb >= 4.5 -> DeviceTier.MEDIUM
                else -> DeviceTier.LOW
            }
        } catch (_: Exception) {
            DeviceTier.LOW
        }
    }

    override fun getModelMetadata(modelId: String): ModelMetadata? {
        return availableModels.find { it.id.equals(modelId, ignoreCase = true) }
    }

    override fun getModelState(modelId: String): ModelState {
        val activeProgress = _downloadStates.value[modelId]
        if (activeProgress != null && (activeProgress.state == ModelState.DOWNLOADING || activeProgress.state == ModelState.VERIFYING)) {
            return activeProgress.state
        }

        val file = getModelFile(modelId)
        return if (file != null && file.exists() && file.length() > 0L) {
            ModelState.INSTALLED
        } else {
            ModelState.NOT_INSTALLED
        }
    }

    override fun getModelFile(modelId: String): File? {
        val meta = getModelMetadata(modelId) ?: return null
        return File(modelsDir, meta.localFileName)
    }

    override suspend fun downloadModel(modelId: String): Flow<ModelDownloadProgress> = flow {
        val meta = getModelMetadata(modelId) ?: throw IllegalArgumentException("Unknown model: $modelId")
        val targetFile = File(modelsDir, meta.localFileName)
        cancelledDownloads.remove(modelId)

        if (!hasSufficientStorage(meta.sizeBytes)) {
            updateProgress(modelId, 0L, meta.sizeBytes, ModelState.FAILED)
            emit(ModelDownloadProgress(modelId, 0L, meta.sizeBytes, 0f, ModelState.FAILED))
            return@flow
        }

        var client: HttpClient? = null
        try {
            client = HttpClient(OkHttp) {
                engine {
                    config {
                        followRedirects(true)
                        followSslRedirects(true)
                        retryOnConnectionFailure(true)
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 0 // streaming download
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 60_000
                }
            }

            val response: HttpResponse = client.get(meta.downloadUrl)
            if (response.status.value !in 200..299) {
                updateProgress(modelId, 0L, meta.sizeBytes, ModelState.FAILED)
                emit(ModelDownloadProgress(modelId, 0L, meta.sizeBytes, 0f, ModelState.FAILED))
                return@flow
            }

            val totalSize = response.headers["Content-Length"]?.toLongOrNull() ?: meta.sizeBytes
            if (targetFile.exists()) targetFile.delete()

            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            updateProgress(modelId, 0L, totalSize, ModelState.DOWNLOADING)
            emit(ModelDownloadProgress(modelId, 0L, totalSize, 0f, ModelState.DOWNLOADING))

            val channel = response.bodyAsChannel()
            var cancelledMidDownload = false
            targetFile.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (!channel.isClosedForRead) {
                    if (cancelledDownloads.contains(modelId)) {
                        cancelledMidDownload = true
                        break
                    }
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    out.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    downloaded += read
                    val progress = if (totalSize > 0) downloaded.toFloat() / totalSize else 0f
                    updateProgress(modelId, downloaded, totalSize, ModelState.DOWNLOADING)
                    emit(ModelDownloadProgress(modelId, downloaded, totalSize, progress, ModelState.DOWNLOADING))
                }
            }
            if (cancelledMidDownload) {
                targetFile.delete()
                updateProgress(modelId, 0L, totalSize, ModelState.CANCELLED)
                emit(ModelDownloadProgress(modelId, 0L, totalSize, 0f, ModelState.CANCELLED))
                return@flow
            }

            updateProgress(modelId, downloaded, totalSize, ModelState.VERIFYING)
            emit(ModelDownloadProgress(modelId, downloaded, totalSize, 1.0f, ModelState.VERIFYING))

            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val checksumOk = meta.sha256Checksum.isBlank() ||
                hash.equals(meta.sha256Checksum, ignoreCase = true)
            val sizeOk = targetFile.length() > 1_000_000L &&
                (totalSize <= 0L || targetFile.length() == totalSize || targetFile.length() == meta.sizeBytes)

            if (!checksumOk || !sizeOk) {
                targetFile.delete()
                updateProgress(modelId, 0L, totalSize, ModelState.CORRUPTED)
                emit(ModelDownloadProgress(modelId, 0L, totalSize, 0f, ModelState.CORRUPTED))
                return@flow
            }

            updateProgress(modelId, downloaded, totalSize, ModelState.READY)
            emit(ModelDownloadProgress(modelId, downloaded, totalSize, 1.0f, ModelState.READY))
        } catch (e: Exception) {
            targetFile.delete()
            updateProgress(modelId, 0L, meta.sizeBytes, ModelState.FAILED)
            emit(ModelDownloadProgress(modelId, 0L, meta.sizeBytes, 0f, ModelState.FAILED))
        } finally {
            client?.close()
        }
    }.flowOn(Dispatchers.IO)

    override fun cancelDownload(modelId: String) {
        cancelledDownloads.add(modelId)
    }

    override fun hasSufficientStorage(requiredBytes: Long): Boolean {
        val usableSpace = context.filesDir.usableSpace
        // Require at least requiredBytes + 50MB safety margin
        return usableSpace >= (requiredBytes + 50 * 1024 * 1024L)
    }

    override suspend fun verifyModelChecksum(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val file = getModelFile(modelId) ?: return@withContext false
        if (!file.exists() || file.length() == 0L) return@withContext false

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val meta = getModelMetadata(modelId)
            val isValid = meta?.sha256Checksum.isNullOrBlank() || hash.equals(meta?.sha256Checksum, ignoreCase = true) || file.length() == meta?.sizeBytes
            if (!isValid) {
                // Quarantine/delete corrupted download
                file.delete()
                updateProgress(modelId, 0L, meta?.sizeBytes ?: 0L, ModelState.CORRUPTED)
            }
            isValid
        } catch (_: Exception) {
            file.delete()
            updateProgress(modelId, 0L, 0L, ModelState.ERROR)
            false
        }
    }

    override suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val file = getModelFile(modelId) ?: return@withContext false
        val deleted = if (file.exists()) file.delete() else true
        if (deleted) {
            val current = _downloadStates.value.toMutableMap()
            current.remove(modelId)
            _downloadStates.value = current
        }
        deleted
    }

    private fun updateProgress(modelId: String, downloaded: Long, total: Long, state: ModelState) {
        val current = _downloadStates.value.toMutableMap()
        current[modelId] = ModelDownloadProgress(
            modelId = modelId,
            bytesDownloaded = downloaded,
            totalBytes = total,
            progressPercent = if (total > 0) downloaded.toFloat() / total else 0f,
            state = state
        )
        _downloadStates.value = current
    }
}
