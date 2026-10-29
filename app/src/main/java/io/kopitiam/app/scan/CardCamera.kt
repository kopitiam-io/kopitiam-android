package io.kopitiam.app.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Fixed dark palette for the camera screens, whatever the app theme. */
object CameraUi {
    val bg = Color(0xFF0E1412)
    val panel = Color(0xFF1E2A26)
    val text = Color(0xFFF4F1E9)
    val soft = Color(0xFFB9C4BF)
    val jade = Color(0xFF4FB99E)
    val button = Color(0xFF1F6F5C)
}

/**
 * Own camera for ID cards: a card-shaped guide, front then back. Each capture is
 * cropped to the guide. Returns one or two images via [onDone].
 */
@Composable
fun CardCamera(onDone: (List<Bitmap>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) {
        if (hasCamera && !granted) permission.launch(Manifest.permission.CAMERA)
    }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(CameraUi.bg)) {
        when {
            !hasCamera -> CameraMessage(
                "Scanning needs a camera",
                "This device has no camera available, so ID card scanning is disabled here.",
                null, null,
            )
            !granted -> CameraMessage(
                "Camera access is off",
                "Allow camera access for Kopitiam to scan ID cards.",
                if (asked) "Open Settings" else null,
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            else -> CardViewfinder(onDone)
        }
        IconButton(onClick = onClose, modifier = Modifier.safeDrawingPadding().padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.Filled.Close, "Close", tint = CameraUi.text)
        }
    }
}

@Composable
private fun CardViewfinder(onDone: (List<Bitmap>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.enableTorch(false)
            controller.unbind()
        }
    }
    val captured = remember { mutableStateListOf<Bitmap>() }
    var busy by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 24.dp.toPx() }
        val guideW = viewW - margin * 2
        val guideH = guideW / IdCardPdf.RATIO
        val guide = Rect(Offset(margin, viewH * 0.40f - guideH / 2f), Size(guideW, guideH))
        val step = if (captured.isEmpty()) "Front" else "Back"

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Dim outside the card frame and outline it.
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(16.dp.toPx())
            val hole = Path().apply { addRoundRect(RoundRect(guide, corner)) }
            clipPath(hole, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.5f))
            }
            drawRoundRect(
                CameraUi.jade, topLeft = guide.topLeft, size = guide.size, cornerRadius = corner,
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        Text(
            if (captured.isEmpty()) "Fit the front of the card in the frame" else "Now turn it over. Fit the back in the frame",
            color = CameraUi.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(0, (guide.top - 56.dp.toPx()).roundToInt()) }
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(50))
                .background(CameraUi.button)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Text(
            "$step ${captured.size + 1} of 2",
            color = CameraUi.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(0, (guide.bottom + 12.dp.toPx()).roundToInt()) },
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            failure?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, textAlign = TextAlign.Center) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    captured.lastOrNull()?.let {
                        Image(
                            it.asImageBitmap(), "Front captured", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp, 36.dp).clip(RoundedCornerShape(6.dp))
                                .border(1.dp, CameraUi.text.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                        )
                    }
                }
                Box(
                    Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(4.dp, CameraUi.jade, CircleShape)
                        .alpha(if (busy) 0.4f else 1f)
                        .clickable(enabled = !busy) {
                            busy = true
                            failure = null
                            controller.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        scope.launch {
                                            val card = withContext(Dispatchers.Default) {
                                                val full = upright(image)
                                                image.close()
                                                val cropped = cropToGuide(full, guide, viewW, viewH)
                                                if (cropped !== full) full.recycle()
                                                // Find the card's edges and straighten it; keep the guide crop if not found.
                                                val straight = straighten(cropped)
                                                if (straight != null) {
                                                    cropped.recycle()
                                                    straight
                                                } else {
                                                    cropped
                                                }
                                            }
                                            captured.add(card)
                                            busy = false
                                            if (captured.size == 2) onDone(captured.toList())
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        busy = false
                                        failure = "Couldn't take the photo. Try again."
                                    }
                                },
                            )
                        }
                        .semantics { contentDescription = "Capture ${step.lowercase()}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(CameraUi.text))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (captured.isNotEmpty()) {
                        Text(
                            "No back", color = CameraUi.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { onDone(captured.toList()) }
                                .padding(10.dp),
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = {
                torch = !torch
                controller.enableTorch(torch)
            },
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
        ) {
            Icon(
                if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                if (torch) "Turn light off" else "Turn light on",
                tint = CameraUi.text,
            )
        }
    }
}

/** ImageProxy to an upright bitmap (applies the sensor rotation). */
private fun upright(image: ImageProxy): Bitmap {
    val bmp = image.toBitmap()
    val degrees = image.imageInfo.rotationDegrees
    if (degrees == 0) return bmp
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    if (rotated !== bmp) bmp.recycle()
    return rotated
}

/**
 * Crop the photo to the on-screen guide. The preview is FILL_CENTER, so map the
 * guide back through the same scale and centring offset, with a little slack.
 */
private fun cropToGuide(photo: Bitmap, guide: Rect, viewW: Float, viewH: Float): Bitmap {
    val iw = photo.width.toFloat(); val ih = photo.height.toFloat()
    if (iw <= 0f || ih <= 0f || viewW <= 0f || viewH <= 0f) return photo
    val scale = max(viewW / iw, viewH / ih)
    val ox = (iw * scale - viewW) / 2f
    val oy = (ih * scale - viewH) / 2f
    // Enough slack that the card's real edges sit inside the crop for straightening.
    val slackX = guide.width * 0.08f
    val slackY = guide.height * 0.08f
    val left = ((guide.left - slackX + ox) / scale).coerceIn(0f, iw - 1f)
    val top = ((guide.top - slackY + oy) / scale).coerceIn(0f, ih - 1f)
    val right = ((guide.right + slackX + ox) / scale).coerceIn(left + 1f, iw)
    val bottom = ((guide.bottom + slackY + oy) / scale).coerceIn(top + 1f, ih)
    return Bitmap.createBitmap(
        photo, left.toInt(), top.toInt(),
        (right - left).toInt().coerceAtLeast(1), (bottom - top).toInt().coerceAtLeast(1),
    )
}

/**
 * Detect the card's four corners on a downscaled copy, then warp the full-size
 * crop into a flat card-shaped image. Returns null when no card is found.
 */
private fun straighten(src: Bitmap): Bitmap? {
    if (src.width < 64 || src.height < 64) return null
    val sw = 400
    val sh = (sw.toFloat() * src.height / src.width).roundToInt().coerceAtLeast(32)
    val small = Bitmap.createScaledBitmap(src, sw, sh, true)
    val pixels = IntArray(sw * sh)
    small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
    if (small !== src) small.recycle()
    val gray = FloatArray(pixels.size) { i ->
        val p = pixels[i]
        0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
    }
    val quad = CardEdges.detect(gray, sw, sh)?.scaled(src.width.toFloat() / sw) ?: return null

    val topLen = hypot(quad.trX - quad.tlX, quad.trY - quad.tlY)
    val bottomLen = hypot(quad.brX - quad.blX, quad.brY - quad.blY)
    val outW = max(topLen, bottomLen).roundToInt().coerceIn(200, 2000)
    val outH = (outW / IdCardPdf.RATIO).roundToInt()
    val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
    val matrix = Matrix()
    if (!matrix.setPolyToPoly(quad.toArray(), 0, dst, 0, 4)) return null
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    AndroidCanvas(out).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

@Composable
private fun CameraMessage(title: String, body: String, action: String?, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.NoPhotography, null, tint = CameraUi.jade, modifier = Modifier.size(48.dp))
        Text(title, color = CameraUi.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(body, color = CameraUi.soft, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                action, color = CameraUi.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(CameraUi.button)
                    .clickable(onClick = onAction).padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}
