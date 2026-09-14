package com.arn.scrobble.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.arn.scrobble.icons.Icons
import com.arn.scrobble.icons.Person
import com.arn.scrobble.pref.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

private data class DecodedGif(
    val frames: List<GifFrame>
)

private data class GifFrame(
    val bitmap: ImageBitmap,
    val durationMs: Long
)

private val gifCache = ConcurrentHashMap<String, DecodedGif>()

private fun isGifBytes(bytes: ByteArray): Boolean {
    return bytes.size >= 6 &&
            bytes[0] == 0x47.toByte() && // G
            bytes[1] == 0x49.toByte() && // I
            bytes[2] == 0x46.toByte() && // F
            bytes[3] == 0x38.toByte() && // 8
            (bytes[4] == 0x37.toByte() || bytes[4] == 0x39.toByte()) && // 7 or 9
            bytes[5] == 0x61.toByte()    // a
}

@Composable
private fun GifAvatarImage(
    avatarUrl: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    var decodedGif by remember(avatarUrl) { mutableStateOf(gifCache[avatarUrl]) }
    var loadFailed by remember(avatarUrl) { mutableStateOf(false) }

    LaunchedEffect(avatarUrl) {
        if (decodedGif != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = when {
                    avatarUrl.startsWith("http://", ignoreCase = true) ||
                            avatarUrl.startsWith("https://", ignoreCase = true) -> {
                        val connection = URI.create(avatarUrl).toURL().openConnection()
                        connection.connectTimeout = 10000
                        connection.readTimeout = 15000
                        connection.getInputStream().use { it.readBytes() }
                    }
                    avatarUrl.startsWith("file://", ignoreCase = true) -> {
                        File(URI.create(avatarUrl).path).readBytes()
                    }
                    else -> {
                        val f = File(avatarUrl)
                        if (f.exists()) f.readBytes() else null
                    }
                }
                if (bytes != null && isGifBytes(bytes)) {
                    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
                    val frameCount = codec.frameCount
                    if (frameCount > 0) {
                        val scratch = Bitmap().apply { allocPixels(codec.imageInfo) }
                        val frames = ArrayList<GifFrame>(frameCount)
                        for (i in 0 until frameCount) {
                            codec.readPixels(scratch, i)
                            val duration = codec.getFrameInfo(i).duration.coerceAtLeast(20).toLong()
                            frames.add(GifFrame(scratch.makeClone().asComposeImageBitmap(), duration))
                        }
                        scratch.close()
                        codec.close()
                        val result = DecodedGif(frames)
                        gifCache[avatarUrl] = result
                        decodedGif = result
                    } else {
                        codec.close()
                        loadFailed = true
                    }
                } else {
                    loadFailed = true
                }
            }.onFailure {
                loadFailed = true
            }
        }
    }

    val gif = decodedGif
    if (loadFailed) {
        AsyncImage(
            model = avatarUrl,
            error = placeholderImageVectorPainter(null, Icons.Person),
            placeholder = placeholderPainter(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else if (gif != null) {
        if (gif.frames.size <= 1) {
            Image(
                bitmap = gif.frames[0].bitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            var currentFrameIndex by remember(gif) { mutableStateOf(0) }
            LaunchedEffect(gif) {
                while (isActive && gif.frames.isNotEmpty()) {
                    val delayTime = gif.frames[currentFrameIndex].durationMs
                    delay(delayTime)
                    currentFrameIndex = (currentFrameIndex + 1) % gif.frames.size
                }
            }
            Image(
                bitmap = gif.frames[currentFrameIndex].bitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Crop,
            )
        }
    } else {
        Image(
            painter = placeholderPainter(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
actual fun AvatarImage(
    avatarUrl: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    val isGif = remember(avatarUrl) {
        avatarUrl.substringBefore('?').endsWith(".gif", ignoreCase = true) ||
                avatarUrl.contains(".gif", ignoreCase = true)
    }

    if (isGif) {
        GifAvatarImage(
            avatarUrl = avatarUrl,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = avatarUrl,
            error = placeholderImageVectorPainter(null, Icons.Person),
            placeholder = placeholderPainter(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
actual fun getActivityOrNull(): Any? {
    return null
}

@Composable
actual fun AppIcon(
    appItem: AppItem?,
    modifier: Modifier,
) {
    val name = appItem?.friendlyLabel?.ifEmpty { "*" } ?: "*"
    val initials = remember(name) {
        // if this is like a package name, use the last part
        if (' ' !in name && '.' in name)
            name.substringAfterLast('.')
                .take(1)
                .uppercase()
                .takeIf { it.isNotEmpty() }
        else
            null
    }
    AvatarOrInitials(
        avatarUrl = null,
        avatarName = name,
        initials = initials,
        textStyle = MaterialTheme.typography.titleSmall,
        modifier = modifier.clip(CircleShape),
    )
}

actual fun Modifier.testTagsAsResId() = this

@Composable
actual fun isImeVisible() = false

@Composable
actual fun ApplyWindowBlur(behind: Int, bg: Int) {
    val modalShownTracker = LocalModalShownTracker.current
    DisposableEffect(Unit) {
        modalShownTracker.value += 1
        onDispose {
            modalShownTracker.value -= 1
        }
    }
}