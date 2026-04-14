package com.drawing.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import kotlin.random.Random

// =============================================================================
// Datatyper
// =============================================================================

private data class DrawnPath(
    val path: Path, val color: Color, val strokeWidth: Float,
    val isErase: Boolean = false, val brushType: BrushType = BrushType.NORMAL
)
private data class EmojiStamp(val emoji: String, val x: Float, val y: Float, val size: Float = 80f)
private data class DrawnShape(
    val type: ShapeType, val start: Offset, val end: Offset,
    val color: Color, val strokeWidth: Float, val filled: Boolean
)
private data class SpraySession(val dots: List<Offset>, val color: Color, val dotRadius: Float)
private data class TextStamp(val text: String, val x: Float, val y: Float, val color: Color, val fontSize: Float)

private sealed class DrawAction {
    data class PathAction(val path: DrawnPath)                              : DrawAction()
    data class StampAction(val stamp: EmojiStamp)                          : DrawAction()
    data class ShapeAction(val shape: DrawnShape)                          : DrawAction()
    data class SprayAction(val session: SpraySession)                      : DrawAction()
    data class FillAction(val x: Int, val y: Int, val newColor: Color)    : DrawAction()
    data class TextAction(val stamp: TextStamp)                            : DrawAction()
    data class SymmetryPathAction(val path: DrawnPath, val mirror: DrawnPath) : DrawAction()
}

private data class Layer(
    val id: Int, val name: String,
    val actions: List<DrawAction> = emptyList(),
    val bitmap: android.graphics.Bitmap? = null,
    val visible: Boolean = true,
    val blendMode: PorterDuff.Mode = PorterDuff.Mode.SRC_OVER
)

private data class EmojiObject(
    val id: Int, val emoji: String,
    val x: Float, val y: Float, val size: Float = 80f
)

private enum class DrawMode { DRAW, SPRAY, SHAPE, FILL, TEXT, STAMP, EYEDROPPER, SELECTION, DODGE, BURN, SMUDGE, PARTICLE, ERASE }
private enum class ObjDragMode { MOVE, RESIZE }
private enum class ShapeType { LINE, RECT, CIRCLE, TRIANGLE, STAR, ARROW, HEXAGON }
private enum class SelState  { NONE, DRAWING, CAPTURED }
private enum class BrushType { NORMAL, NEON, MARKER, RAINBOW, STIPPLE, CHALK, WATERCOLOR, FLAT }
private enum class TextureMode { NONE, CANVAS, PAPER, KRAFT }

// =============================================================================
// Konstanter
// =============================================================================

private val kidColors = listOf(
    // Röda
    Color(0xFFFF1744), Color(0xFFFF4757), Color(0xFFFF6B81), Color(0xFFFF80AB), Color(0xFFFF4081),
    // Orange
    Color(0xFFFF6D00), Color(0xFFFF9F43), Color(0xFFFFB300), Color(0xFFFFDD59), Color(0xFFFFEA00),
    // Gröna
    Color(0xFF00C853), Color(0xFF0BE881), Color(0xFF69F0AE), Color(0xFF76FF03), Color(0xFF64DD17),
    // Cyan/Turkos
    Color(0xFF00BCD4), Color(0xFF00D2D3), Color(0xFF48DBFB), Color(0xFF18FFFF),
    // Blå
    Color(0xFF2979FF), Color(0xFF54A0FF), Color(0xFF40C4FF), Color(0xFF82B1FF),
    // Lila/Rosa
    Color(0xFF5F27CD), Color(0xFFAA00FF), Color(0xFFFF9FF3), Color(0xFFE040FB), Color(0xFFEAF0FC),
    // Bruna (hela spektrumet)
    Color(0xFF3E1C00), Color(0xFF5D2E0C), Color(0xFF7B3F00), Color(0xFF8B4513),
    Color(0xFFA0522D), Color(0xFFB5651D), Color(0xFFC68642), Color(0xFFD2691E),
    Color(0xFFCD853F), Color(0xFFDEB887), Color(0xFFD4A76A), Color(0xFFE8C99A),
    Color(0xFFF5DEB3), Color(0xFFFFF8DC),
    // Grå/Blågrå
    Color(0xFF8D6E63), Color(0xFFBCAAA4), Color(0xFFFFCCBC), Color(0xFFFFAB91),
    Color(0xFF90A4AE), Color(0xFF546E7A), Color(0xFF37474F), Color(0xFF1E272E),
    // Svart/Vit
    Color.Black, Color(0xFF1A1A1A), Color(0xFF333333), Color(0xFF666666),
    Color(0xFF999999), Color(0xFFCCCCCC), Color(0xFFEEEEEE), Color.White,
)
private val backgroundColors = listOf(
    Color.White, Color(0xFFFFFDE7), Color(0xFFFFF9C4), Color(0xFFE8F5E9),
    Color(0xFFE3F2FD), Color(0xFFFCE4EC), Color(0xFFF3E5F5), Color(0xFFE0F7FA),
    Color(0xFFFFF3E0), Color(0xFFEFEBE9), Color(0xFF1A1A2E), Color(0xFF0D1B2A),
)

private val skinToneColors = listOf(
    Color(0xFFFBEED2), Color(0xFFF5D5B8), Color(0xFFEEC19A), Color(0xFFE8A87C),
    Color(0xFFD4956A), Color(0xFFC68642), Color(0xFFB87333), Color(0xFFA0522D),
    Color(0xFF8B4513), Color(0xFF7B3B2B), Color(0xFF6B2D1E), Color(0xFF5C2E1A),
    Color(0xFF4A1E0E), Color(0xFF3B1208), Color(0xFF2C0D06),
)
private val planetColors = listOf(
    Color(0xFFB5936B), Color(0xFFE8C49A), Color(0xFFD4A84B),
    Color(0xFF6B93D6), Color(0xFF4CAF50), Color(0xFF1A6B3C),
    Color(0xFFC1440E), Color(0xFFAD7C4A), Color(0xFFE8D5A3),
    Color(0xFFE4D191), Color(0xFFAF9B6B), Color(0xFF7DE8E8),
    Color(0xFF4B70DD), Color(0xFF0D0D2B), Color(0xFFB5BED0),
)
private val landscapeColors = listOf(
    Color(0xFF87CEEB), Color(0xFF4682B4), Color(0xFF1E3A5F), Color(0xFF0D1B2A),
    Color(0xFF228B22), Color(0xFF90EE90), Color(0xFF6B8E23), Color(0xFF355E3B),
    Color(0xFF8B4513), Color(0xFFD2691E), Color(0xFFF5F5DC), Color(0xFFD4C5A9),
    Color(0xFFFF7F50), Color(0xFFFFD700), Color(0xFFFF4500),
)
private val holiColors = listOf(
    Color(0xFFFF1744), Color(0xFFFF4081), Color(0xFFFF6D00), Color(0xFFFFDD59),
    Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF00B0FF), Color(0xFF40C4FF),
    Color(0xFFE040FB), Color(0xFFEA80FC), Color(0xFFFF80AB), Color(0xFF64FFDA),
    Color(0xFFFF6E40), Color(0xFFCCFF90), Color(0xFFFFFF8D),
)
private val colorPalettes = mapOf(
    "Ljus"   to kidColors,
    "Hud"    to skinToneColors,
    "Planet" to planetColors,
    "Natur"  to landscapeColors,
    "Holi"   to holiColors,
)

private val rainbowColors = listOf(
    Color(0xFFFF1744), Color(0xFFFF6D00), Color(0xFFFFDD59),
    Color(0xFF00C853), Color(0xFF00D2D3), Color(0xFF2979FF),
    Color(0xFFAA00FF), Color(0xFFFF4081),
)

private val stampCategories = mapOf(
    "Fav"      to listOf("⭐","❤️","🌟","🎉","🔥","💎","🏆","✨","💫","🎵","👑","🌈","💯","🎊","🥇"),
    "Ansikten" to listOf("😀","😂","😍","🤩","😎","🥳","😱","🤔","😴","🤯","😅","🤣","😇","🥰","😜","🤪","😤","😭","🙄","🤬"),
    "Djur"     to listOf("🐱","🐶","🦄","🐸","🐠","🦋","🐧","🦊","🐺","🦁","🐨","🐼","🐝","🦜","🐙","🦈","🦒","🐘","🦅","🦋"),
    "Mat"      to listOf("🍦","🍓","🍉","🍕","🍔","🍩","🍰","🎂","🍫","🍪","🥐","🍣","🌮","🥗","🍱","🧁","🥪","🍟","🥤","🫐"),
    "Natur"    to listOf("🌸","🌺","🌻","🌙","☀️","⛅","🌈","🌊","🏔️","🌲","🍀","🌹","🌵","🍁","❄️","⚡","🌪️","🌴","🎋","🌾"),
    "Sport"    to listOf("⚽","🏀","🎾","🏈","⚾","🎱","🏓","🏸","🥊","🎯","🏋️","⛷️","🏄","🎳","🥋","🏇","🏹","🤿","🏊","🧗"),
    "Symboler" to listOf("❤️","💛","💚","💙","💜","🖤","🤍","💔","✅","❌","🔴","🟡","🟢","🔵","⭕","🔱","♾️","☯️","⚜️","🔰"),
    "Övrigt"   to listOf("🚀","🎈","🎨","🎭","🎪","💥","🎸","🎹","🎺","🎻","🎮","🕹️","🎲","🧩","💡","🔮","🗝️","⚙️","🛸","🎠"),
)

private val blendModes = listOf(
    PorterDuff.Mode.SRC_OVER  to "Normal",
    PorterDuff.Mode.MULTIPLY  to "Multiply",
    PorterDuff.Mode.SCREEN    to "Screen",
    PorterDuff.Mode.OVERLAY   to "Overlay",
    PorterDuff.Mode.DARKEN    to "Mörkna",
    PorterDuff.Mode.LIGHTEN   to "Ljusna",
)

private val toolList = listOf(
    DrawMode.DRAW       to "✏️",
    DrawMode.SPRAY      to "💨",
    DrawMode.SHAPE      to "🔷",
    DrawMode.FILL       to "🪣",
    DrawMode.TEXT       to "Aa",
    DrawMode.STAMP      to "⭐",
    DrawMode.EYEDROPPER to "🔍",
    DrawMode.SELECTION  to "⬚",
    DrawMode.DODGE      to "☀️",
    DrawMode.BURN       to "🌑",
    DrawMode.SMUDGE     to "👆",
    DrawMode.PARTICLE   to "✨",
    DrawMode.ERASE      to "🧹",
)

// =============================================================================
// Filterfunktioner
// =============================================================================

private fun applyContrastFilter(bmp: android.graphics.Bitmap, contrast: Float) {
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = (((( px shr 16) and 0xFF) - 128) * contrast + 128).toInt().coerceIn(0, 255)
        val g = ((((px shr  8) and 0xFF) - 128) * contrast + 128).toInt().coerceIn(0, 255)
        val b = (((px and 0xFF) - 128) * contrast + 128).toInt().coerceIn(0, 255)
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun applyBrightnessFilter(bmp: android.graphics.Bitmap, brightness: Float) {
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val delta = ((brightness - 1f) * 255f).toInt()
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = (((px shr 16) and 0xFF) + delta).coerceIn(0, 255)
        val g = (((px shr  8) and 0xFF) + delta).coerceIn(0, 255)
        val b = ((px and 0xFF) + delta).coerceIn(0, 255)
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun applySaturationFilter(bmp: android.graphics.Bitmap, saturation: Float) {
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = (px shr 16) and 0xFF
        val g = (px shr  8) and 0xFF
        val b =  px and 0xFF
        val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        val nr = (gray + (r - gray) * saturation).toInt().coerceIn(0, 255)
        val ng = (gray + (g - gray) * saturation).toInt().coerceIn(0, 255)
        val nb = (gray + (b - gray) * saturation).toInt().coerceIn(0, 255)
        pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun applyBlurFilter(bmp: android.graphics.Bitmap, radius: Int): android.graphics.Bitmap {
    val factor = (radius + 1).toFloat()
    val sw = (bmp.width / factor).toInt().coerceAtLeast(1)
    val sh = (bmp.height / factor).toInt().coerceAtLeast(1)
    val small = android.graphics.Bitmap.createScaledBitmap(bmp, sw, sh, true)
    return android.graphics.Bitmap.createScaledBitmap(small, bmp.width, bmp.height, true)
}

private fun invertColorsFilter(bmp: android.graphics.Bitmap) {
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        pixels[i] = (a shl 24) or ((255 - ((px shr 16) and 0xFF)) shl 16) or
                    ((255 - ((px shr 8) and 0xFF)) shl 8) or (255 - (px and 0xFF))
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun toGrayscaleFilter(bmp: android.graphics.Bitmap) {
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val gray = (0.299f * ((px shr 16) and 0xFF) + 0.587f * ((px shr 8) and 0xFF) + 0.114f * (px and 0xFF)).toInt().coerceIn(0, 255)
        pixels[i] = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun sharpenFilter(bmp: android.graphics.Bitmap, strength: Float) {
    val w = bmp.width; val h = bmp.height
    val pixels = IntArray(w * h); bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val out = pixels.copyOf()
    val k = strength
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val c = pixels[y * w + x]; val a = (c shr 24) and 0xFF
        fun ch(px: Int, sh: Int) = (px shr sh) and 0xFF
        fun sharp(center: Int, n: Int, e: Int, s: Int, ww: Int): Int =
            ((1 + 4 * k) * center - k * (n + e + s + ww)).toInt().coerceIn(0, 255)
        val r = sharp(ch(c,16), ch(pixels[(y-1)*w+x],16), ch(pixels[y*w+x+1],16), ch(pixels[(y+1)*w+x],16), ch(pixels[y*w+x-1],16))
        val g = sharp(ch(c,8),  ch(pixels[(y-1)*w+x],8),  ch(pixels[y*w+x+1],8),  ch(pixels[(y+1)*w+x],8),  ch(pixels[y*w+x-1],8))
        val b = sharp(ch(c,0),  ch(pixels[(y-1)*w+x],0),  ch(pixels[y*w+x+1],0),  ch(pixels[(y+1)*w+x],0),  ch(pixels[y*w+x-1],0))
        out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(out, 0, w, 0, 0, w, h)
}

private fun noiseFilter(bmp: android.graphics.Bitmap, amount: Int) {
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val n = (Random.nextInt(amount * 2 + 1) - amount)
        val r = (((px shr 16) and 0xFF) + n).coerceIn(0, 255)
        val g = (((px shr 8) and 0xFF) + n).coerceIn(0, 255)
        val b = ((px and 0xFF) + n).coerceIn(0, 255)
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun vignetteFilter(bmp: android.graphics.Bitmap, strength: Float) {
    val w = bmp.width; val h = bmp.height
    val pixels = IntArray(w * h); bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val cx = w / 2f; val cy = h / 2f
    val maxDist = sqrt(cx * cx + cy * cy)
    for (y in 0 until h) for (x in 0 until w) {
        val dx = x - cx; val dy = y - cy
        val dist = sqrt(dx * dx + dy * dy) / maxDist
        val dark = (dist * dist * strength).coerceIn(0f, 1f)
        val px = pixels[y * w + x]; val a = (px shr 24) and 0xFF
        val r = (((px shr 16) and 0xFF) * (1f - dark)).toInt().coerceIn(0, 255)
        val g = (((px shr 8) and 0xFF) * (1f - dark)).toInt().coerceIn(0, 255)
        val b = ((px and 0xFF) * (1f - dark)).toInt().coerceIn(0, 255)
        pixels[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
}

private fun hueShiftFilter(bmp: android.graphics.Bitmap, shift: Float) {
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val hsv = FloatArray(3)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        android.graphics.Color.RGBToHSV((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF, hsv)
        hsv[0] = (hsv[0] + shift) % 360f
        val rgb = android.graphics.Color.HSVToColor(hsv)
        pixels[i] = (a shl 24) or (rgb and 0x00FFFFFF)
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun flipHorizontalLayer(bmp: android.graphics.Bitmap): android.graphics.Bitmap {
    val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f) }
    return android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

private fun flipVerticalLayer(bmp: android.graphics.Bitmap): android.graphics.Bitmap {
    val matrix = android.graphics.Matrix().apply { postScale(1f, -1f, bmp.width / 2f, bmp.height / 2f) }
    return android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

private fun rotateCWLayer(bmp: android.graphics.Bitmap): android.graphics.Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
    val rot = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    // Scale back to original dimensions
    return android.graphics.Bitmap.createScaledBitmap(rot, bmp.width, bmp.height, true)
}

private fun rotateCCWLayer(bmp: android.graphics.Bitmap): android.graphics.Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(-90f) }
    val rot = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    return android.graphics.Bitmap.createScaledBitmap(rot, bmp.width, bmp.height, true)
}

// =============================================================================
// DrawingScreen
// =============================================================================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingScreen() {
    val context = LocalContext.current

    // Lager
    val layers        = remember { mutableStateListOf(Layer(id = 0, name = "Lager 1")) }
    var nextLayerId   by remember { mutableIntStateOf(1) }
    var activeLayerId by remember { mutableIntStateOf(0) }
    var showLayers    by remember { mutableStateOf(false) }
    val globalHistory = remember { mutableStateListOf<Pair<Int, DrawAction>>() }
    val redoStack     = remember { mutableStateListOf<Pair<Int, DrawAction>>() }

    // Rityta
    var cachedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val livePoints    = remember { mutableListOf<Offset>() }
    val liveSprayDots = remember { mutableListOf<Offset>() }
    var renderTick    by remember { mutableIntStateOf(0) }
    var canvasWidth   by remember { mutableStateOf(0) }
    var canvasHeight  by remember { mutableStateOf(0) }

    // Verktyg
    var currentColor    by remember { mutableStateOf(kidColors[0]) }
    var brushSize       by remember { mutableFloatStateOf(16f) }
    var opacity         by remember { mutableFloatStateOf(1f) }
    var drawMode        by remember { mutableStateOf(DrawMode.DRAW) }
    var brushType       by remember { mutableStateOf(BrushType.NORMAL) }
    var symmetryH       by remember { mutableStateOf(false) }
    var selectedStamp   by remember { mutableStateOf(stampCategories["Fav"]!![0]) }
    var stampCategory   by remember { mutableStateOf("Fav") }
    var selectedShape   by remember { mutableStateOf(ShapeType.LINE) }
    var shapeFilled     by remember { mutableStateOf(false) }
    var backgroundColor by remember { mutableStateOf(Color.White) }
    var textFontSize    by remember { mutableFloatStateOf(72f) }
    var selectedPalette by remember { mutableStateOf("Ljus") }
    var textureMode     by remember { mutableStateOf(TextureMode.NONE) }

    // Emoji-objekt (interaktiva)
    val emojiObjects   = remember { mutableStateListOf<EmojiObject>() }
    var nextObjId      by remember { mutableIntStateOf(0) }
    var selectedObjId  by remember { mutableStateOf<Int?>(null) }
    var objDragMode    by remember { mutableStateOf<ObjDragMode?>(null) }
    var objDragPrev    by remember { mutableStateOf<Offset?>(null) }
    var guideX         by remember { mutableStateOf<Float?>(null) }
    var guideY         by remember { mutableStateOf<Float?>(null) }

    // Rutnät
    var showGrid        by remember { mutableStateOf(false) }
    var showGridPanel   by remember { mutableStateOf(false) }
    var toolbarExpanded by remember { mutableStateOf(true) }
    var gridSpacing   by remember { mutableIntStateOf(60) }

    // Formförhandsvisning
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd   by remember { mutableStateOf<Offset?>(null) }

    // Symmetri live-preview
    val liveMirrorPoints = remember { mutableListOf<Offset>() }

    // Markering (Selection)
    var selState      by remember { mutableStateOf(SelState.NONE) }
    var selRectStart  by remember { mutableStateOf(Offset.Zero) }
    var selRectEnd    by remember { mutableStateOf(Offset.Zero) }
    var selCapture    by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var selOrigin     by remember { mutableStateOf(Offset.Zero) }
    var selOffset     by remember { mutableStateOf(Offset.Zero) }
    var selDragPrev   by remember { mutableStateOf<Offset?>(null) }

    // Dialogar
    var showTextDialog     by remember { mutableStateOf(false) }
    var pendingTextPos     by remember { mutableStateOf(Offset.Zero) }
    var textInput          by remember { mutableStateOf("") }
    var showClearDialog    by remember { mutableStateOf(false) }
    var showExitDialog     by remember { mutableStateOf(false) }
    var showFilterDialog   by remember { mutableStateOf(false) }
    var showColorDialog    by remember { mutableStateOf(false) }

    // Filter-dialog värden
    var filterContrast    by remember { mutableFloatStateOf(1f) }
    var filterBrightness  by remember { mutableFloatStateOf(1f) }
    var filterSaturation  by remember { mutableFloatStateOf(1f) }
    var filterBlur        by remember { mutableFloatStateOf(1f) }
    var filterSharpen     by remember { mutableFloatStateOf(0f) }
    var filterNoise       by remember { mutableFloatStateOf(0f) }
    var filterVignette    by remember { mutableFloatStateOf(0f) }
    var filterHueShift    by remember { mutableFloatStateOf(0f) }

    // Anpassad färgväljare
    var customR by remember { mutableFloatStateOf(255f) }
    var customG by remember { mutableFloatStateOf(0f) }
    var customB by remember { mutableFloatStateOf(0f) }
    var customA by remember { mutableFloatStateOf(255f) }

    val activity = context as? androidx.activity.ComponentActivity

    // =========================================================================
    // Hjälpfunktioner
    // =========================================================================

    fun buildSmoothPath(pts: List<Offset>): Path {
        val p = Path(); if (pts.isEmpty()) return p
        p.moveTo(pts[0].x, pts[0].y)
        if (pts.size == 1) { p.lineTo(pts[0].x + 0.1f, pts[0].y + 0.1f); return p }
        for (i in 1 until pts.size - 1) {
            val mx = (pts[i].x + pts[i+1].x) / 2f; val my = (pts[i].y + pts[i+1].y) / 2f
            p.quadraticBezierTo(pts[i].x, pts[i].y, mx, my)
        }
        p.lineTo(pts.last().x, pts.last().y); return p
    }

    fun mirrorPoints(pts: List<Offset>, w: Float) = pts.map { Offset(w - it.x, it.y) }

    fun generateSprayDots(cx: Float, cy: Float, spread: Float) =
        (0 until 14).map {
            val a = Random.nextFloat() * 2f * PI.toFloat(); val r = Random.nextFloat() * spread
            Offset(cx + cos(a) * r, cy + sin(a) * r)
        }

    fun drawNeonPath(path: Path, color: Color, strokeWidth: Float, c: android.graphics.Canvas) {
        listOf(4f to 0.07f, 3f to 0.15f, 2f to 0.3f, 1.3f to 0.7f, 0.8f to 1f).forEach { (mult, alpha) ->
            val paint = AndroidPaint().apply {
                this.color = color.copy(alpha = alpha.toFloat()).toArgb()
                style = AndroidPaint.Style.STROKE
                this.strokeWidth = strokeWidth * mult
                strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
            }
            c.drawPath(path.asAndroidPath(), paint)
        }
        val centerPaint = AndroidPaint().apply {
            this.color = Color.White.copy(alpha = 0.9f).toArgb()
            style = AndroidPaint.Style.STROKE
            this.strokeWidth = strokeWidth * 0.35f
            strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
        }
        c.drawPath(path.asAndroidPath(), centerPaint)
    }

    fun drawPathOnCanvas(dp: DrawnPath, c: android.graphics.Canvas) {
        when (dp.brushType) {
            BrushType.NEON -> drawNeonPath(dp.path, dp.color, dp.strokeWidth, c)
            BrushType.MARKER -> {
                val paint = AndroidPaint().apply {
                    color = dp.color.toArgb()
                    this.alpha = (0.5f * dp.color.alpha * 255).toInt().coerceIn(0, 255)
                    style = AndroidPaint.Style.STROKE; strokeWidth = dp.strokeWidth
                    strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                }
                c.drawPath(dp.path.asAndroidPath(), paint)
            }
            BrushType.NORMAL, BrushType.RAINBOW -> {
                val paint = AndroidPaint().apply {
                    if (dp.isErase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    else color = dp.color.toArgb()
                    style = AndroidPaint.Style.STROKE; strokeWidth = dp.strokeWidth
                    strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                }
                c.drawPath(dp.path.asAndroidPath(), paint)
            }
            BrushType.FLAT -> {
                val paint = AndroidPaint().apply {
                    color = dp.color.toArgb()
                    style = AndroidPaint.Style.STROKE; strokeWidth = dp.strokeWidth * 2.2f
                    strokeCap = AndroidPaint.Cap.SQUARE; strokeJoin = AndroidPaint.Join.MITER; isAntiAlias = true
                }
                c.drawPath(dp.path.asAndroidPath(), paint)
            }
            BrushType.CHALK -> {
                val rng = Random(dp.color.hashCode().toLong() xor dp.strokeWidth.toLong())
                repeat(5) { layer ->
                    val paint = AndroidPaint().apply {
                        color = dp.color.copy(alpha = (0.18f + layer * 0.08f)).toArgb()
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = dp.strokeWidth * (0.6f + layer * 0.12f)
                        strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                    }
                    val matrix = android.graphics.Matrix().apply {
                        setTranslate(
                            (rng.nextFloat() - 0.5f) * dp.strokeWidth * 0.35f,
                            (rng.nextFloat() - 0.5f) * dp.strokeWidth * 0.35f
                        )
                    }
                    val shifted = android.graphics.Path(dp.path.asAndroidPath()).also { it.transform(matrix) }
                    c.drawPath(shifted, paint)
                }
            }
            BrushType.WATERCOLOR -> {
                repeat(7) { layer ->
                    val paint = AndroidPaint().apply {
                        color = dp.color.copy(alpha = 0.055f + layer * 0.01f).toArgb()
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = dp.strokeWidth * (1.1f + layer * 0.25f)
                        strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                    }
                    c.drawPath(dp.path.asAndroidPath(), paint)
                }
            }
            BrushType.STIPPLE -> {
                val dotPaint = AndroidPaint().apply {
                    color = dp.color.toArgb(); style = AndroidPaint.Style.FILL; isAntiAlias = true
                }
                val measure = android.graphics.PathMeasure(dp.path.asAndroidPath(), false)
                val pos = FloatArray(2); val tan = FloatArray(2)
                val dotR = dp.strokeWidth * 0.22f
                val spacing = dp.strokeWidth * 0.75f
                var dist = 0f
                val rng = Random(42)
                while (dist <= measure.length) {
                    measure.getPosTan(dist, pos, tan)
                    val jx = (rng.nextFloat() - 0.5f) * dp.strokeWidth * 0.45f
                    val jy = (rng.nextFloat() - 0.5f) * dp.strokeWidth * 0.45f
                    c.drawCircle(pos[0] + jx, pos[1] + jy, dotR + rng.nextFloat() * dotR, dotPaint)
                    dist += spacing
                }
            }
        }
    }

    fun buildStarPath(cx: Float, cy: Float, outerR: Float): android.graphics.Path {
        val innerR = outerR * 0.382f
        val starPath = android.graphics.Path()
        for (i in 0 until 10) {
            val angle = Math.toRadians((i * 36.0 - 90.0))
            val r = if (i % 2 == 0) outerR else innerR
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        return starPath
    }

    fun buildTrianglePath(start: Offset, end: Offset): android.graphics.Path {
        val left   = min(start.x, end.x)
        val right  = max(start.x, end.x)
        val top    = min(start.y, end.y)
        val bottom = max(start.y, end.y)
        val path = android.graphics.Path()
        path.moveTo((left + right) / 2f, top)
        path.lineTo(right, bottom)
        path.lineTo(left, bottom)
        path.close()
        return path
    }

    fun drawShapeOnCanvas(shape: DrawnShape, c: android.graphics.Canvas) {
        val paint = AndroidPaint().apply {
            color = shape.color.toArgb()
            style = if (shape.filled) AndroidPaint.Style.FILL_AND_STROKE else AndroidPaint.Style.STROKE
            strokeWidth = shape.strokeWidth; strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
        }
        when (shape.type) {
            ShapeType.LINE   -> c.drawLine(shape.start.x, shape.start.y, shape.end.x, shape.end.y, paint)
            ShapeType.RECT   -> c.drawRect(min(shape.start.x, shape.end.x), min(shape.start.y, shape.end.y),
                                           max(shape.start.x, shape.end.x), max(shape.start.y, shape.end.y), paint)
            ShapeType.CIRCLE -> {
                val dx = shape.end.x - shape.start.x; val dy = shape.end.y - shape.start.y
                c.drawCircle(shape.start.x, shape.start.y, sqrt(dx*dx + dy*dy), paint)
            }
            ShapeType.TRIANGLE -> c.drawPath(buildTrianglePath(shape.start, shape.end), paint)
            ShapeType.STAR -> {
                val dx = shape.end.x - shape.start.x; val dy = shape.end.y - shape.start.y
                c.drawPath(buildStarPath(shape.start.x, shape.start.y, sqrt(dx*dx + dy*dy)), paint)
            }
            ShapeType.ARROW -> {
                val ex = shape.end.x; val ey = shape.end.y
                val sx = shape.start.x; val sy = shape.start.y
                val ang = atan2(ey - sy, ex - sx)
                val hw = shape.strokeWidth * 2.5f
                val hp = hw * 2.5f
                val bx = ex - cos(ang) * hp; val by = ey - sin(ang) * hp
                val arrowPath = android.graphics.Path().apply {
                    moveTo(sx, sy); lineTo(bx, by)
                    moveTo(ex, ey)
                    lineTo(bx + cos(ang + PI.toFloat()/2f) * hw, by + sin(ang + PI.toFloat()/2f) * hw)
                    lineTo(ex, ey)
                    lineTo(bx + cos(ang - PI.toFloat()/2f) * hw, by + sin(ang - PI.toFloat()/2f) * hw)
                }
                c.drawPath(arrowPath, paint)
            }
            ShapeType.HEXAGON -> {
                val cx = shape.start.x; val cy = shape.start.y
                val dx = shape.end.x - cx; val dy = shape.end.y - cy
                val r = sqrt(dx*dx + dy*dy)
                val hexPath = android.graphics.Path().apply {
                    for (i in 0 until 6) {
                        val a = Math.toRadians(60.0 * i - 30.0)
                        val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                c.drawPath(hexPath, paint)
            }
        }
    }

    fun replayAction(action: DrawAction, bmp: android.graphics.Bitmap, c: android.graphics.Canvas) {
        when (action) {
            is DrawAction.PathAction -> drawPathOnCanvas(action.path, c)
            is DrawAction.SymmetryPathAction -> {
                drawPathOnCanvas(action.path, c)
                drawPathOnCanvas(action.mirror, c)
            }
            is DrawAction.ShapeAction -> drawShapeOnCanvas(action.shape, c)
            is DrawAction.SprayAction -> {
                val paint = AndroidPaint().apply { color = action.session.color.toArgb(); style = AndroidPaint.Style.FILL; isAntiAlias = true }
                action.session.dots.forEach { c.drawCircle(it.x, it.y, action.session.dotRadius, paint) }
            }
            is DrawAction.StampAction -> {
                val paint = AndroidPaint().apply { textSize = action.stamp.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true }
                c.drawText(action.stamp.emoji, action.stamp.x, action.stamp.y, paint)
            }
            is DrawAction.FillAction  -> floodFill(bmp, action.x, action.y, action.newColor.toArgb())
            is DrawAction.TextAction  -> {
                val paint = AndroidPaint().apply { color = action.stamp.color.toArgb(); textSize = action.stamp.fontSize; isAntiAlias = true }
                c.drawText(action.stamp.text, action.stamp.x, action.stamp.y, paint)
            }
        }
    }

    fun newLayerBmp() = android.graphics.Bitmap.createBitmap(
        canvasWidth.coerceAtLeast(1), canvasHeight.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)

    fun ensureLayerBmp(idx: Int) {
        if (canvasWidth == 0 || layers[idx].bitmap != null) return
        layers[idx] = layers[idx].copy(bitmap = newLayerBmp())
    }

    fun rerenderLayer(idx: Int) {
        val w = canvasWidth; val h = canvasHeight; if (w == 0 || h == 0) return
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        layers[idx].actions.forEach { replayAction(it, bmp, c) }
        layers[idx] = layers[idx].copy(bitmap = bmp)
    }

    fun compositeAllLayers() {
        val w = canvasWidth; val h = canvasHeight; if (w == 0 || h == 0) return
        val composite = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(composite)
        c.drawColor(backgroundColor.toArgb())
        layers.forEach { layer ->
            if (layer.visible) layer.bitmap?.let {
                val paint = AndroidPaint().apply { xfermode = PorterDuffXfermode(layer.blendMode) }
                c.drawBitmap(it, 0f, 0f, paint)
            }
        }
        cachedBitmap = composite; renderTick++
    }

    fun addActionToActiveLayer(action: DrawAction, drawFn: (android.graphics.Bitmap, android.graphics.Canvas) -> Unit) {
        val idx = layers.indexOfFirst { it.id == activeLayerId }; if (idx < 0) return
        ensureLayerBmp(idx)
        val bmp = layers[idx].bitmap ?: return
        drawFn(bmp, android.graphics.Canvas(bmp))
        layers[idx] = layers[idx].copy(actions = layers[idx].actions + action)
        globalHistory.add(activeLayerId to action)
        redoStack.clear()
        compositeAllLayers()
    }

    // -- Markeringshjälp --

    fun commitSelection() {
        val cap = selCapture ?: run { selState = SelState.NONE; return }
        val idx = layers.indexOfFirst { it.id == activeLayerId }
        if (idx >= 0) {
            val bmp = layers[idx].bitmap ?: return
            android.graphics.Canvas(bmp).drawBitmap(cap, selOrigin.x + selOffset.x, selOrigin.y + selOffset.y, null)
        }
        selCapture = null; selState = SelState.NONE; compositeAllLayers()
    }

    fun cancelSelection() {
        val cap = selCapture ?: run { selState = SelState.NONE; return }
        val idx = layers.indexOfFirst { it.id == activeLayerId }
        if (idx >= 0) {
            val bmp = layers[idx].bitmap ?: return
            android.graphics.Canvas(bmp).drawBitmap(cap, selOrigin.x, selOrigin.y, null)
        }
        selCapture = null; selState = SelState.NONE; compositeAllLayers()
    }

    fun doCapture() {
        val idx = layers.indexOfFirst { it.id == activeLayerId }; if (idx < 0) return
        ensureLayerBmp(idx)
        val layerBmp = layers[idx].bitmap ?: return
        val l = min(selRectStart.x, selRectEnd.x).toInt().coerceIn(0, layerBmp.width - 1)
        val t = min(selRectStart.y, selRectEnd.y).toInt().coerceIn(0, layerBmp.height - 1)
        val r = max(selRectStart.x, selRectEnd.x).toInt().coerceIn(l+1, layerBmp.width)
        val b = max(selRectStart.y, selRectEnd.y).toInt().coerceIn(t+1, layerBmp.height)
        val w = r - l; val h = b - t; if (w < 5 || h < 5) return
        selCapture = android.graphics.Bitmap.createBitmap(layerBmp, l, t, w, h)
        selOrigin = Offset(l.toFloat(), t.toFloat()); selOffset = Offset.Zero
        val clearPaint = AndroidPaint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        android.graphics.Canvas(layerBmp).drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), clearPaint)
        selState = SelState.CAPTURED; compositeAllLayers()
    }

    // Effektiv färg med opacitet
    fun effectiveColor(): Color = currentColor.copy(alpha = currentColor.alpha * opacity)

    LaunchedEffect(backgroundColor) { compositeAllLayers() }

    // =========================================================================
    // Dialogar
    // =========================================================================

    BackHandler(enabled = globalHistory.isNotEmpty()) { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Avsluta utan att spara?", fontWeight = FontWeight.Bold) },
            text  = { Text("Din ritning är inte sparad.") },
            confirmButton = {
                Button(onClick = { activity?.finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                ) { Text("Avsluta") }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Avbryt") } }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Rensa allt?", fontWeight = FontWeight.Bold) },
            text  = { Text("Alla lager och ritningar raderas.") },
            confirmButton = {
                Button(onClick = {
                    selCapture = null; selState = SelState.NONE
                    layers.indices.forEach { i ->
                        layers[i] = layers[i].copy(actions = emptyList(), bitmap = newLayerBmp())
                    }
                    globalHistory.clear(); redoStack.clear(); compositeAllLayers(); showClearDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                ) { Text("Rensa") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Avbryt") } }
        )
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false; textInput = "" },
            title = { Text("Skriv text", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = textInput, onValueChange = { textInput = it },
                        label = { Text("Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Storlek: ${textFontSize.toInt()} px", fontSize = 12.sp, color = Color.Gray)
                    Slider(value = textFontSize, onValueChange = { textFontSize = it }, valueRange = 24f..200f)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (textInput.isNotBlank()) {
                        val stamp = TextStamp(textInput, pendingTextPos.x, pendingTextPos.y, currentColor, textFontSize)
                        addActionToActiveLayer(DrawAction.TextAction(stamp)) { _, c ->
                            val paint = AndroidPaint().apply { color = stamp.color.toArgb(); textSize = stamp.fontSize; isAntiAlias = true }
                            c.drawText(stamp.text, stamp.x, stamp.y, paint)
                        }
                    }
                    showTextDialog = false; textInput = ""
                }) { Text("Lägg till") }
            },
            dismissButton = { TextButton(onClick = { showTextDialog = false; textInput = "" }) { Text("Avbryt") } }
        )
    }

    // Filter-dialog
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Bildfilter", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Kontrast: ${String.format("%.2f", filterContrast)}×", fontSize = 13.sp)
                    Slider(value = filterContrast, onValueChange = { filterContrast = it }, valueRange = 0.1f..3f)

                    Text("Ljusstyrka: ${String.format("%.2f", filterBrightness)}×", fontSize = 13.sp)
                    Slider(value = filterBrightness, onValueChange = { filterBrightness = it }, valueRange = 0f..2f)

                    Text("Mättnad: ${String.format("%.2f", filterSaturation)}×", fontSize = 13.sp)
                    Slider(value = filterSaturation, onValueChange = { filterSaturation = it }, valueRange = 0f..3f)

                    Text("Oskärpa: ${filterBlur.toInt()}", fontSize = 13.sp)
                    Slider(value = filterBlur, onValueChange = { filterBlur = it }, valueRange = 1f..20f)

                    Text("Skärpa: ${String.format("%.2f", filterSharpen)}", fontSize = 13.sp)
                    Slider(value = filterSharpen, onValueChange = { filterSharpen = it }, valueRange = 0f..3f)

                    Text("Brus: ${filterNoise.toInt()}", fontSize = 13.sp)
                    Slider(value = filterNoise, onValueChange = { filterNoise = it }, valueRange = 0f..60f)

                    Text("Vinjett: ${String.format("%.2f", filterVignette)}", fontSize = 13.sp)
                    Slider(value = filterVignette, onValueChange = { filterVignette = it }, valueRange = 0f..3f)

                    Text("Färgton: ${filterHueShift.toInt()}°", fontSize = 13.sp)
                    Slider(value = filterHueShift, onValueChange = { filterHueShift = it }, valueRange = -180f..180f)

                    Divider()
                    Text("Engångsfilter", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val idx = layers.indexOfFirst { it.id == activeLayerId }
                                if (idx >= 0) { layers[idx].bitmap?.let { invertColorsFilter(it) }; compositeAllLayers() }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Invertera", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = {
                                val idx = layers.indexOfFirst { it.id == activeLayerId }
                                if (idx >= 0) { layers[idx].bitmap?.let { toGrayscaleFilter(it) }; compositeAllLayers() }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Gråskala", fontSize = 12.sp) }
                    }
                    Divider()
                    Text("Transformera lager", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) layers[idx].bitmap?.let { bmp ->
                                val flipped = flipHorizontalLayer(bmp)
                                android.graphics.Canvas(bmp).apply { drawColor(0, PorterDuff.Mode.CLEAR); drawBitmap(flipped, 0f, 0f, null) }
                                compositeAllLayers()
                            }
                        }, modifier = Modifier.weight(1f)) { Text("⟺ H", fontSize = 12.sp) }
                        OutlinedButton(onClick = {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) layers[idx].bitmap?.let { bmp ->
                                val flipped = flipVerticalLayer(bmp)
                                android.graphics.Canvas(bmp).apply { drawColor(0, PorterDuff.Mode.CLEAR); drawBitmap(flipped, 0f, 0f, null) }
                                compositeAllLayers()
                            }
                        }, modifier = Modifier.weight(1f)) { Text("⟺ V", fontSize = 12.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) layers[idx].bitmap?.let { bmp ->
                                val rot = rotateCWLayer(bmp)
                                android.graphics.Canvas(bmp).apply { drawColor(0, PorterDuff.Mode.CLEAR); drawBitmap(rot, 0f, 0f, null) }
                                compositeAllLayers()
                            }
                        }, modifier = Modifier.weight(1f)) { Text("↻ 90°", fontSize = 12.sp) }
                        OutlinedButton(onClick = {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) layers[idx].bitmap?.let { bmp ->
                                val rot = rotateCCWLayer(bmp)
                                android.graphics.Canvas(bmp).apply { drawColor(0, PorterDuff.Mode.CLEAR); drawBitmap(rot, 0f, 0f, null) }
                                compositeAllLayers()
                            }
                        }, modifier = Modifier.weight(1f)) { Text("↺ 90°", fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val idx = layers.indexOfFirst { it.id == activeLayerId }
                    if (idx >= 0) {
                        val bmp = layers[idx].bitmap
                        if (bmp != null) {
                            if (filterContrast != 1f) applyContrastFilter(bmp, filterContrast)
                            if (filterBrightness != 1f) applyBrightnessFilter(bmp, filterBrightness)
                            if (filterSaturation != 1f) applySaturationFilter(bmp, filterSaturation)
                            if (filterBlur > 1f) {
                                val blurred = applyBlurFilter(bmp, filterBlur.toInt())
                                android.graphics.Canvas(bmp).drawBitmap(blurred, 0f, 0f, null)
                            }
                            if (filterSharpen > 0f) sharpenFilter(bmp, filterSharpen)
                            if (filterNoise > 0f) noiseFilter(bmp, filterNoise.toInt())
                            if (filterVignette > 0f) vignetteFilter(bmp, filterVignette)
                            if (filterHueShift != 0f) hueShiftFilter(bmp, filterHueShift)
                            compositeAllLayers()
                        }
                    }
                    filterContrast = 1f; filterBrightness = 1f; filterSaturation = 1f; filterBlur = 1f
                    filterSharpen = 0f; filterNoise = 0f; filterVignette = 0f; filterHueShift = 0f
                    showFilterDialog = false
                }) { Text("Tillämpa") }
            },
            dismissButton = {
                TextButton(onClick = {
                    filterContrast = 1f; filterBrightness = 1f; filterSaturation = 1f; filterBlur = 1f
                    filterSharpen = 0f; filterNoise = 0f; filterVignette = 0f; filterHueShift = 0f
                    showFilterDialog = false
                }) { Text("Avbryt") }
            }
        )
    }

    // Anpassad färg-dialog
    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Anpassad färg", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(customR.toInt(), customG.toInt(), customB.toInt(), customA.toInt()))
                    )
                    Text("R: ${customR.toInt()}", fontSize = 12.sp)
                    Slider(value = customR, onValueChange = { customR = it }, valueRange = 0f..255f)
                    Text("G: ${customG.toInt()}", fontSize = 12.sp)
                    Slider(value = customG, onValueChange = { customG = it }, valueRange = 0f..255f)
                    Text("B: ${customB.toInt()}", fontSize = 12.sp)
                    Slider(value = customB, onValueChange = { customB = it }, valueRange = 0f..255f)
                    Text("A: ${customA.toInt()}", fontSize = 12.sp)
                    Slider(value = customA, onValueChange = { customA = it }, valueRange = 0f..255f)
                }
            },
            confirmButton = {
                Button(onClick = {
                    currentColor = Color(customR.toInt(), customG.toInt(), customB.toInt(), customA.toInt())
                    showColorDialog = false
                }) { Text("Välj") }
            },
            dismissButton = { TextButton(onClick = { showColorDialog = false }) { Text("Avbryt") } }
        )
    }

    // =========================================================================
    // Layout
    // =========================================================================

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {

        // Toprad
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF16213E))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rita", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                // Undo
                TopBtn("Undo", Color(0xFF54A0FF)) {
                    if (selState == SelState.CAPTURED) { cancelSelection(); return@TopBtn }
                    val last = globalHistory.removeLastOrNull() ?: return@TopBtn
                    val (layerId, action) = last
                    val idx = layers.indexOfFirst { it.id == layerId }
                    if (idx >= 0) {
                        layers[idx] = layers[idx].copy(actions = layers[idx].actions.dropLast(1))
                        rerenderLayer(idx); compositeAllLayers()
                    }
                    redoStack.add(layerId to action)
                }
                // Redo
                TopBtn("Redo", Color(0xFF2979FF)) {
                    val redoItem = redoStack.removeLastOrNull() ?: return@TopBtn
                    val (layerId, action) = redoItem
                    val idx = layers.indexOfFirst { it.id == layerId }
                    if (idx >= 0) {
                        ensureLayerBmp(idx)
                        val bmp = layers[idx].bitmap ?: return@TopBtn
                        val c = android.graphics.Canvas(bmp)
                        replayAction(action, bmp, c)
                        layers[idx] = layers[idx].copy(actions = layers[idx].actions + action)
                        compositeAllLayers()
                    }
                    globalHistory.add(layerId to action)
                }
                TopBtn("Filter", Color(0xFF546E7A)) { showFilterDialog = true }
                TopBtn("Grid", if (showGrid) Color(0xFFFF9F43) else Color(0xFF546E7A)) {
                    if (!showGrid) { showGrid = true; showGridPanel = true }
                    else showGridPanel = !showGridPanel
                }
                TopBtn("Spara", Color(0xFF0BE881)) {
                    val base = cachedBitmap
                    if (base != null && emojiObjects.isNotEmpty()) {
                        val merged = android.graphics.Bitmap.createBitmap(base.width, base.height, android.graphics.Bitmap.Config.ARGB_8888)
                        val mc = android.graphics.Canvas(merged)
                        mc.drawBitmap(base, 0f, 0f, null)
                        emojiObjects.forEach { obj ->
                            mc.drawText(obj.emoji, obj.x, obj.y + obj.size / 3f,
                                AndroidPaint().apply { textSize = obj.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true })
                        }
                        saveToGallery(context, merged)
                    } else {
                        saveToGallery(context, base)
                    }
                }
                TopBtn("Rensa", Color(0xFFFF9F43)) { showClearDialog = true }
                TopBtn("Lager", if (showLayers) Color(0xFFFF9F43) else Color(0xFF546E7A)) { showLayers = !showLayers }
            }
        }

        // Mitten: Verktygspanel + Canvas + Lagerpanel
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

            // Vänster verktygspanel
            Column(
                modifier = Modifier.width(56.dp).fillMaxHeight().background(Color(0xFF0F3460))
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                toolList.forEach { (mode, icon) ->
                    val active = drawMode == mode
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color(0xFF5F27CD) else Color(0xFF1A4080))
                            .clickable {
                                if (selState == SelState.CAPTURED) commitSelection()
                                drawMode = mode
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon.length > 2) Text(icon, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else Color(0xFFAAAAAA))
                        else Text(icon, fontSize = 20.sp)
                    }
                }
            }

            // Canvas
            val baseModifier = Modifier.weight(1f).fillMaxHeight().background(backgroundColor)
                .onSizeChanged { size ->
                    val changed = canvasWidth != size.width || canvasHeight != size.height
                    canvasWidth = size.width; canvasHeight = size.height
                    if (changed) {
                        layers.indices.forEach { i ->
                            val old = layers[i].bitmap
                            val newBmp = android.graphics.Bitmap.createBitmap(size.width, size.height, android.graphics.Bitmap.Config.ARGB_8888)
                            if (old != null) android.graphics.Canvas(newBmp).drawBitmap(old, null,
                                android.graphics.RectF(0f, 0f, size.width.toFloat(), size.height.toFloat()), null)
                            layers[i] = layers[i].copy(bitmap = newBmp)
                        }
                        compositeAllLayers()
                    }
                }

            val gestureModifier = baseModifier.pointerInteropFilter { event ->
                when (drawMode) {

                    DrawMode.STAMP -> {
                        val SNAP = 14f
                        val HANDLE_R = 28f

                        fun objHalfHit(obj: EmojiObject, x: Float, y: Float): Boolean {
                            val half = obj.size / 2f + 10f
                            return x in (obj.x - half)..(obj.x + half) && y in (obj.y - half)..(obj.y + half)
                        }

                        fun computeGuides(obj: EmojiObject) {
                            val cx = canvasWidth / 2f; val cy = canvasHeight / 2f
                            var gx: Float? = null; var gy: Float? = null
                            if (abs(obj.x - cx) < SNAP) gx = cx
                            if (abs(obj.y - cy) < SNAP) gy = cy
                            emojiObjects.forEach { other ->
                                if (other.id != obj.id) {
                                    if (abs(obj.x - other.x) < SNAP) gx = other.x
                                    if (abs(obj.y - other.y) < SNAP) gy = other.y
                                    if (abs(obj.x - (other.x + other.size / 2f)) < SNAP) gx = other.x + other.size / 2f
                                    if (abs(obj.y - (other.y + other.size / 2f)) < SNAP) gy = other.y + other.size / 2f
                                }
                            }
                            guideX = gx; guideY = gy
                        }

                        val curSelObj = selectedObjId?.let { id -> emojiObjects.firstOrNull { it.id == id } }

                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                // Check resize handle on selected object first
                                if (curSelObj != null) {
                                    val half = curSelObj.size / 2f
                                    val hx = curSelObj.x + half; val hy = curSelObj.y + half
                                    val dist = sqrt((event.x - hx) * (event.x - hx) + (event.y - hy) * (event.y - hy))
                                    if (dist < HANDLE_R) {
                                        objDragMode = ObjDragMode.RESIZE
                                        objDragPrev = Offset(event.x, event.y)
                                        renderTick++
                                        return@pointerInteropFilter true
                                    }
                                }
                                // Check hit on any object (last = topmost)
                                val hit = emojiObjects.lastOrNull { objHalfHit(it, event.x, event.y) }
                                if (hit != null) {
                                    selectedObjId = hit.id
                                    objDragMode = ObjDragMode.MOVE
                                    objDragPrev = Offset(event.x, event.y)
                                } else {
                                    selectedObjId = null
                                    objDragMode = null
                                    objDragPrev = null
                                }
                                renderTick++
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val prev = objDragPrev
                                val selIdx = selectedObjId?.let { id -> emojiObjects.indexOfFirst { it.id == id } } ?: -1
                                if (prev != null && selIdx >= 0) {
                                    val obj = emojiObjects[selIdx]
                                    when (objDragMode) {
                                        ObjDragMode.MOVE -> {
                                            var nx = obj.x + (event.x - prev.x)
                                            var ny = obj.y + (event.y - prev.y)
                                            val temp = obj.copy(x = nx, y = ny)
                                            computeGuides(temp)
                                            if (guideX != null && abs(nx - guideX!!) < SNAP) nx = guideX!!
                                            if (guideY != null && abs(ny - guideY!!) < SNAP) ny = guideY!!
                                            emojiObjects[selIdx] = obj.copy(x = nx, y = ny)
                                        }
                                        ObjDragMode.RESIZE -> {
                                            val delta = (event.x - prev.x + event.y - prev.y) / 2f
                                            emojiObjects[selIdx] = obj.copy(size = (obj.size + delta * 1.4f).coerceIn(20f, 500f))
                                        }
                                        null -> {}
                                    }
                                    objDragPrev = Offset(event.x, event.y)
                                }
                                renderTick++
                            }
                            MotionEvent.ACTION_UP -> {
                                if (objDragMode == null && selectedObjId == null) {
                                    // Place new emoji object at tap position
                                    val newObj = EmojiObject(nextObjId++, selectedStamp, event.x, event.y)
                                    emojiObjects.add(newObj)
                                    selectedObjId = newObj.id
                                }
                                objDragMode = null
                                guideX = null; guideY = null
                                renderTick++
                            }
                        }; true
                    }

                    DrawMode.FILL -> {
                        if (event.action == MotionEvent.ACTION_UP) {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) {
                                ensureLayerBmp(idx)
                                val bmp = layers[idx].bitmap ?: return@pointerInteropFilter true
                                val cx = event.x.toInt().coerceIn(0, bmp.width-1)
                                val cy = event.y.toInt().coerceIn(0, bmp.height-1)
                                addActionToActiveLayer(DrawAction.FillAction(cx, cy, currentColor)) { b, _ -> floodFill(b, cx, cy, currentColor.toArgb()) }
                            }
                        }; true
                    }

                    DrawMode.TEXT -> {
                        if (event.action == MotionEvent.ACTION_UP) { pendingTextPos = Offset(event.x, event.y); showTextDialog = true }; true
                    }

                    DrawMode.EYEDROPPER -> {
                        if (event.action == MotionEvent.ACTION_UP) {
                            val bmp = cachedBitmap
                            if (bmp != null) {
                                val px = event.x.toInt().coerceIn(0, bmp.width-1)
                                val py = event.y.toInt().coerceIn(0, bmp.height-1)
                                currentColor = Color(bmp.getPixel(px, py)); drawMode = DrawMode.DRAW
                            }
                        }; true
                    }

                    DrawMode.SELECTION -> {
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                if (selState == SelState.CAPTURED) {
                                    selDragPrev = Offset(event.x, event.y)
                                } else {
                                    selRectStart = Offset(event.x, event.y); selRectEnd = selRectStart
                                    selState = SelState.DRAWING
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (selState == SelState.DRAWING) {
                                    selRectEnd = Offset(event.x, event.y)
                                } else if (selState == SelState.CAPTURED) {
                                    val prev = selDragPrev
                                    if (prev != null) selOffset += Offset(event.x - prev.x, event.y - prev.y)
                                    selDragPrev = Offset(event.x, event.y)
                                }
                                renderTick++
                            }
                            MotionEvent.ACTION_UP -> {
                                if (selState == SelState.DRAWING) doCapture()
                                else selDragPrev = null
                            }
                        }; true
                    }

                    DrawMode.SHAPE -> {
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> { shapeStart = Offset(event.x, event.y); shapeEnd = shapeStart }
                            MotionEvent.ACTION_MOVE -> { shapeEnd = Offset(event.x, event.y); renderTick++ }
                            MotionEvent.ACTION_UP   -> {
                                val s = shapeStart
                                if (s != null) {
                                    val shape = DrawnShape(selectedShape, s, Offset(event.x, event.y), effectiveColor(), brushSize, shapeFilled)
                                    addActionToActiveLayer(DrawAction.ShapeAction(shape)) { _, c -> drawShapeOnCanvas(shape, c) }
                                }
                                shapeStart = null; shapeEnd = null; renderTick++
                            }
                        }; true
                    }

                    DrawMode.SPRAY -> {
                        val idx = layers.indexOfFirst { it.id == activeLayerId }
                        fun sprayAt(x: Float, y: Float) {
                            if (idx < 0) return; ensureLayerBmp(idx)
                            val bmp = layers[idx].bitmap ?: return
                            val dots = generateSprayDots(x, y, 35f); liveSprayDots.addAll(dots)
                            val c = android.graphics.Canvas(bmp)
                            val paint = AndroidPaint().apply { color = effectiveColor().toArgb(); style = AndroidPaint.Style.FILL; isAntiAlias = true }
                            dots.forEach { c.drawCircle(it.x, it.y, 3f, paint) }
                        }
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> { liveSprayDots.clear(); sprayAt(event.x, event.y); compositeAllLayers() }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) sprayAt(event.getHistoricalX(i), event.getHistoricalY(i))
                                sprayAt(event.x, event.y); compositeAllLayers()
                            }
                            MotionEvent.ACTION_UP   -> {
                                if (liveSprayDots.isNotEmpty() && idx >= 0) {
                                    val session = SpraySession(liveSprayDots.toList(), effectiveColor(), 3f)
                                    layers[idx] = layers[idx].copy(actions = layers[idx].actions + DrawAction.SprayAction(session))
                                    globalHistory.add(activeLayerId to DrawAction.SprayAction(session))
                                    redoStack.clear()
                                }
                                liveSprayDots.clear()
                            }
                        }; true
                    }

                    DrawMode.PARTICLE -> {
                        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) {
                                ensureLayerBmp(idx)
                                val bmp = layers[idx].bitmap ?: return@pointerInteropFilter true
                                val c = android.graphics.Canvas(bmp)
                                val numParticles = 28
                                val burst = (0 until numParticles).map {
                                    val angle = it * (2f * PI.toFloat() / numParticles) + Random.nextFloat() * 0.4f
                                    val r = (0.3f + Random.nextFloat() * 0.7f) * brushSize * 2.5f
                                    Offset(event.x + cos(angle) * r, event.y + sin(angle) * r)
                                }
                                // Inner glow
                                val innerBurst = (0 until numParticles / 2).map {
                                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                                    val r = Random.nextFloat() * brushSize * 0.8f
                                    Offset(event.x + cos(angle) * r, event.y + sin(angle) * r)
                                }
                                val allDots = burst + innerBurst
                                val col = if (brushType == BrushType.RAINBOW) rainbowColors.random() else effectiveColor()
                                allDots.forEachIndexed { i, dot ->
                                    val dotSize = if (i < numParticles) brushSize * (0.08f + Random.nextFloat() * 0.18f)
                                                  else brushSize * 0.25f
                                    val paint = AndroidPaint().apply {
                                        color = col.copy(alpha = 0.5f + Random.nextFloat() * 0.5f).toArgb()
                                        style = AndroidPaint.Style.FILL; isAntiAlias = true
                                    }
                                    c.drawCircle(dot.x, dot.y, dotSize, paint)
                                }
                                // Spara som spray-action för undo
                                val session = SpraySession(allDots, col, brushSize * 0.12f)
                                layers[idx] = layers[idx].copy(actions = layers[idx].actions + DrawAction.SprayAction(session))
                                globalHistory.add(activeLayerId to DrawAction.SprayAction(session))
                                redoStack.clear()
                                compositeAllLayers()
                            }
                        }; true
                    }

                    DrawMode.DODGE, DrawMode.BURN -> {
                        val col = if (drawMode == DrawMode.DODGE) Color(1f, 1f, 1f, 0.14f) else Color(0f, 0f, 0f, 0.14f)
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                livePoints.clear()
                                livePoints.add(Offset(event.x, event.y)); renderTick++
                            }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) livePoints.add(Offset(event.getHistoricalX(i), event.getHistoricalY(i)))
                                livePoints.add(Offset(event.x, event.y)); renderTick++
                            }
                            MotionEvent.ACTION_UP -> {
                                if (livePoints.size >= 2) {
                                    val dp = DrawnPath(buildSmoothPath(livePoints), col, brushSize * 2.5f, false, BrushType.NORMAL)
                                    addActionToActiveLayer(DrawAction.PathAction(dp)) { _, c -> drawPathOnCanvas(dp, c) }
                                }
                                livePoints.clear(); renderTick++
                            }
                        }; true
                    }

                    DrawMode.SMUDGE -> {
                        val idx = layers.indexOfFirst { it.id == activeLayerId }
                        fun smudgeAt(x: Float, y: Float) {
                            if (idx < 0) return; ensureLayerBmp(idx)
                            val bmp = layers[idx].bitmap ?: return
                            val r = brushSize.toInt().coerceAtLeast(4)
                            val left   = (x - r).toInt().coerceIn(0, bmp.width - 1)
                            val top    = (y - r).toInt().coerceIn(0, bmp.height - 1)
                            val right  = (x + r).toInt().coerceIn(left + 1, bmp.width)
                            val bottom = (y + r).toInt().coerceIn(top + 1, bmp.height)
                            val w = right - left; val h = bottom - top; if (w < 2 || h < 2) return
                            val sub = android.graphics.Bitmap.createBitmap(bmp, left, top, w, h)
                            val small = android.graphics.Bitmap.createScaledBitmap(sub, (w / 2).coerceAtLeast(1), (h / 2).coerceAtLeast(1), true)
                            val back  = android.graphics.Bitmap.createScaledBitmap(small, w, h, true)
                            android.graphics.Canvas(bmp).drawBitmap(back, left.toFloat(), top.toFloat(), null)
                        }
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> { smudgeAt(event.x, event.y); compositeAllLayers() }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) smudgeAt(event.getHistoricalX(i), event.getHistoricalY(i))
                                smudgeAt(event.x, event.y); compositeAllLayers()
                            }
                            MotionEvent.ACTION_UP -> {}
                        }; true
                    }

                    DrawMode.DRAW, DrawMode.ERASE -> {
                        val isErase = drawMode == DrawMode.ERASE
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                livePoints.clear(); liveMirrorPoints.clear()
                                livePoints.add(Offset(event.x, event.y))
                                if (symmetryH && !isErase) liveMirrorPoints.add(Offset(canvasWidth - event.x, event.y))
                                renderTick++
                            }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) {
                                    livePoints.add(Offset(event.getHistoricalX(i), event.getHistoricalY(i)))
                                    if (symmetryH && !isErase) liveMirrorPoints.add(Offset(canvasWidth - event.getHistoricalX(i), event.getHistoricalY(i)))
                                }
                                livePoints.add(Offset(event.x, event.y))
                                if (symmetryH && !isErase) liveMirrorPoints.add(Offset(canvasWidth - event.x, event.y))
                                renderTick++
                            }
                            MotionEvent.ACTION_UP   -> {
                                if (livePoints.size >= 2) {
                                    val sw = if (isErase) brushSize * 2 else brushSize
                                    val col = when {
                                        isErase -> backgroundColor
                                        brushType == BrushType.RAINBOW -> rainbowColors.random()
                                        else -> effectiveColor()
                                    }
                                    val dp = DrawnPath(buildSmoothPath(livePoints), col, sw, isErase, if (isErase) BrushType.NORMAL else brushType)

                                    if (symmetryH && !isErase && liveMirrorPoints.size >= 2) {
                                        val mirrorDp = DrawnPath(buildSmoothPath(liveMirrorPoints), col, sw, false, brushType)
                                        val symAction = DrawAction.SymmetryPathAction(dp, mirrorDp)
                                        addActionToActiveLayer(symAction) { _, c ->
                                            drawPathOnCanvas(dp, c)
                                            drawPathOnCanvas(mirrorDp, c)
                                        }
                                    } else {
                                        addActionToActiveLayer(DrawAction.PathAction(dp)) { _, c ->
                                            drawPathOnCanvas(dp, c)
                                        }
                                    }
                                }
                                livePoints.clear(); liveMirrorPoints.clear(); renderTick++
                            }
                        }; true
                    }
                }
            }

            Canvas(modifier = gestureModifier) {
                @Suppress("UNUSED_EXPRESSION") renderTick
                cachedBitmap?.let { drawImage(it.asImageBitmap()) } ?: drawRect(color = backgroundColor)

                // Canvas-textur overlay
                if (textureMode != TextureMode.NONE) {
                    val txC = drawContext.canvas.nativeCanvas
                    val txPaint = AndroidPaint().apply { style = AndroidPaint.Style.FILL; isAntiAlias = true }
                    when (textureMode) {
                        TextureMode.CANVAS -> {
                            txPaint.color = android.graphics.Color.argb(22, 60, 40, 20)
                            var tx = 0f; while (tx < size.width) {
                                var ty = 0f; while (ty < size.height) {
                                    txC.drawCircle(tx, ty, 1.1f, txPaint); ty += 6f
                                }; tx += 6f
                            }
                        }
                        TextureMode.PAPER -> {
                            txPaint.color = android.graphics.Color.argb(18, 80, 70, 50)
                            val rng = Random(7)
                            repeat(3000) {
                                txC.drawCircle(rng.nextFloat() * size.width, rng.nextFloat() * size.height, 0.9f, txPaint)
                            }
                        }
                        TextureMode.KRAFT -> {
                            txPaint.color = android.graphics.Color.argb(30, 100, 60, 20)
                            val rng = Random(13)
                            repeat(2000) {
                                val x = rng.nextFloat() * size.width; val y = rng.nextFloat() * size.height
                                txC.drawLine(x, y, x + rng.nextFloat() * 4f - 2f, y + rng.nextFloat() * 4f - 2f, txPaint)
                            }
                        }
                        else -> {}
                    }
                }

                // Live streck
                if (livePoints.size >= 2 && (drawMode == DrawMode.DRAW || drawMode == DrawMode.ERASE)) {
                    val sw = if (drawMode == DrawMode.ERASE) brushSize * 2 else brushSize
                    val col = if (drawMode == DrawMode.ERASE) backgroundColor else effectiveColor()
                    val livePath = buildSmoothPath(livePoints)
                    when (brushType) {
                        BrushType.NEON -> {
                            listOf(4f to 0.07f, 3f to 0.15f, 2f to 0.3f, 1.3f to 0.7f, 0.8f to 1f).forEach { (mult, alpha) ->
                                drawPath(livePath, col.copy(alpha = alpha), style = Stroke(sw * mult, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            drawPath(livePath, Color.White.copy(alpha = 0.9f), style = Stroke(sw * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        BrushType.MARKER -> drawPath(livePath, col.copy(alpha = 0.5f * col.alpha), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        BrushType.FLAT -> drawPath(livePath, col, style = Stroke(sw * 2.2f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
                        BrushType.WATERCOLOR -> drawPath(livePath, col.copy(alpha = 0.12f), style = Stroke(sw * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        else -> drawPath(livePath, col, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    // Spegeldrag live preview
                    if (symmetryH && drawMode == DrawMode.DRAW && liveMirrorPoints.size >= 2) {
                        val mirrorPath = buildSmoothPath(liveMirrorPoints)
                        when (brushType) {
                            BrushType.NEON -> {
                                listOf(4f to 0.07f, 3f to 0.15f, 2f to 0.3f, 1.3f to 0.7f, 0.8f to 1f).forEach { (mult, alpha) ->
                                    drawPath(mirrorPath, col.copy(alpha = alpha), style = Stroke(sw * mult, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                                drawPath(mirrorPath, Color.White.copy(alpha = 0.9f), style = Stroke(sw * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            BrushType.MARKER -> drawPath(mirrorPath, col.copy(alpha = 0.5f * col.alpha), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            BrushType.FLAT -> drawPath(mirrorPath, col, style = Stroke(sw * 2.2f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
                            else -> drawPath(mirrorPath, col, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }

                // Form-förhandsvisning
                val s = shapeStart; val e = shapeEnd
                if (drawMode == DrawMode.SHAPE && s != null && e != null) {
                    val style: androidx.compose.ui.graphics.drawscope.DrawStyle =
                        if (shapeFilled) Fill else Stroke(brushSize, cap = StrokeCap.Round)
                    val col = effectiveColor()
                    when (selectedShape) {
                        ShapeType.LINE     -> drawLine(col, s, e, brushSize, StrokeCap.Round)
                        ShapeType.RECT     -> drawRect(col, topLeft = Offset(min(s.x,e.x), min(s.y,e.y)),
                            size = Size(abs(e.x-s.x), abs(e.y-s.y)), style = style)
                        ShapeType.CIRCLE   -> { val dx=e.x-s.x; val dy=e.y-s.y; drawCircle(col, sqrt(dx*dx+dy*dy), s, style=style) }
                        ShapeType.TRIANGLE -> {
                            val tPath = Path().apply {
                                val left = min(s.x, e.x); val right = max(s.x, e.x)
                                val top  = min(s.y, e.y); val bottom = max(s.y, e.y)
                                moveTo((left+right)/2f, top); lineTo(right, bottom); lineTo(left, bottom); close()
                            }
                            drawPath(tPath, col, style = style)
                        }
                        ShapeType.STAR -> {
                            val dx=e.x-s.x; val dy=e.y-s.y; val outerR=sqrt(dx*dx+dy*dy)
                            val innerR = outerR * 0.382f
                            val starPath = Path().apply {
                                for (i in 0 until 10) {
                                    val angle = Math.toRadians((i * 36.0 - 90.0))
                                    val r = if (i % 2 == 0) outerR else innerR
                                    val x = s.x + r * cos(angle).toFloat(); val y = s.y + r * sin(angle).toFloat()
                                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                                }
                                close()
                            }
                            drawPath(starPath, col, style = style)
                        }
                        ShapeType.ARROW -> {
                            val ang = atan2(e.y - s.y, e.x - s.x)
                            val hw = brushSize * 2.5f; val hp = hw * 2.5f
                            val bx = e.x - cos(ang) * hp; val by = e.y - sin(ang) * hp
                            val arrowPath = Path().apply {
                                moveTo(s.x, s.y); lineTo(bx, by)
                                moveTo(e.x, e.y)
                                lineTo(bx + cos(ang + PI.toFloat()/2f)*hw, by + sin(ang + PI.toFloat()/2f)*hw)
                                lineTo(e.x, e.y)
                                lineTo(bx + cos(ang - PI.toFloat()/2f)*hw, by + sin(ang - PI.toFloat()/2f)*hw)
                            }
                            drawPath(arrowPath, col, style = Stroke(brushSize, cap = StrokeCap.Round))
                        }
                        ShapeType.HEXAGON -> {
                            val dx=e.x-s.x; val dy=e.y-s.y; val r=sqrt(dx*dx+dy*dy)
                            val hexPath = Path().apply {
                                for (i in 0 until 6) {
                                    val a = Math.toRadians(60.0 * i - 30.0)
                                    val x = s.x + r * cos(a).toFloat(); val y = s.y + r * sin(a).toFloat()
                                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                                }
                                close()
                            }
                            drawPath(hexPath, col, style = style)
                        }
                    }
                }

                // Markeringsrektangel (ritas)
                if (drawMode == DrawMode.SELECTION && selState == SelState.DRAWING) {
                    val l = min(selRectStart.x, selRectEnd.x); val t = min(selRectStart.y, selRectEnd.y)
                    val w = abs(selRectEnd.x - selRectStart.x); val h = abs(selRectEnd.y - selRectStart.y)
                    drawRect(Color(0x33FFFFFF), topLeft = Offset(l, t), size = Size(w, h))
                    drawRect(Color.White, topLeft = Offset(l, t), size = Size(w, h),
                        style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)))
                }

                // Markering (infångad, kan flyttas)
                if (selState == SelState.CAPTURED) {
                    val cap = selCapture
                    if (cap != null) {
                        val ox = selOrigin.x + selOffset.x; val oy = selOrigin.y + selOffset.y
                        drawImage(cap.asImageBitmap(), topLeft = Offset(ox, oy))
                        drawRect(Color.White, topLeft = Offset(ox, oy),
                            size = Size(cap.width.toFloat(), cap.height.toFloat()),
                            style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)))
                    }
                }

                // Ögondroppe-markör
                if (drawMode == DrawMode.EYEDROPPER) {
                    val cx = size.width / 2; val cy = size.height / 2
                    drawCircle(Color.White, 24f, Offset(cx, cy), style = Stroke(2f))
                    drawLine(Color.White, Offset(cx - 30f, cy), Offset(cx + 30f, cy), 1.5f)
                    drawLine(Color.White, Offset(cx, cy - 30f), Offset(cx, cy + 30f), 1.5f)
                }

                // Symmetri-linje
                if (symmetryH && drawMode == DrawMode.DRAW) {
                    val cx = size.width / 2f
                    drawLine(Color.White.copy(alpha = 0.3f), Offset(cx, 0f), Offset(cx, size.height), 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f))
                }

                // Rutnät
                if (showGrid) {
                    val gridColor = Color.Gray.copy(alpha = 0.35f)
                    var x = 0f
                    while (x < size.width) { drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f); x += gridSpacing }
                    var y = 0f
                    while (y < size.height) { drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f); y += gridSpacing }
                }

                // Emoji-objekt (interaktiva, renderas ovanpå allt)
                @Suppress("UNUSED_EXPRESSION") renderTick
                val nativeC = drawContext.canvas.nativeCanvas
                emojiObjects.forEach { obj ->
                    val tp = AndroidPaint().apply {
                        textSize = obj.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true
                    }
                    nativeC.drawText(obj.emoji, obj.x, obj.y + obj.size / 3f, tp)
                }
                // Markerings-UI för valt objekt
                emojiObjects.forEach { obj ->
                    if (obj.id == selectedObjId) {
                        val half = obj.size / 2f
                        drawRect(Color.White, topLeft = Offset(obj.x - half, obj.y - half),
                            size = Size(obj.size, obj.size),
                            style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)))
                        // Resize-handtag (nere till höger)
                        drawCircle(Color(0xFF5F27CD), 18f, Offset(obj.x + half, obj.y + half))
                        drawCircle(Color.White, 18f, Offset(obj.x + half, obj.y + half), style = Stroke(2f))
                        // Storleksindikator
                        val sp = AndroidPaint().apply {
                            color = android.graphics.Color.WHITE; textSize = 24f
                            textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true
                        }
                        nativeC.drawText("↔", obj.x + half, obj.y + half + 8f, sp)
                    }
                }

                // Smart guides (visas vid drag av objekt)
                guideX?.let { gx ->
                    drawLine(Color(0xFF00D2D3).copy(alpha = 0.9f), Offset(gx, 0f), Offset(gx, size.height), 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f))
                }
                guideY?.let { gy ->
                    drawLine(Color(0xFF00D2D3).copy(alpha = 0.9f), Offset(0f, gy), Offset(size.width, gy), 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f))
                }
            }

            // Lagerpanel (höger, slide-in)
            AnimatedVisibility(visible = showLayers,
                enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
                Column(
                    modifier = Modifier.width(168.dp).fillMaxHeight().background(Color(0xFF16213E)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Lager", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Button(onClick = {
                            val newId = nextLayerId++
                            val bmp = if (canvasWidth > 0 && canvasHeight > 0) newLayerBmp() else null
                            layers.add(Layer(id = newId, name = "Lager ${layers.size + 1}", bitmap = bmp))
                            activeLayerId = newId
                        }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+") }
                        Button(onClick = {
                            if (layers.size > 1) {
                                val idx = layers.indexOfFirst { it.id == activeLayerId }
                                if (idx >= 0) {
                                    layers.removeAt(idx); globalHistory.removeAll { it.first == activeLayerId }
                                    activeLayerId = layers.last().id; compositeAllLayers()
                                }
                            }
                        }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                        ) { Text("-") }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(layers.reversed()) { _, layer ->
                            val isActive = layer.id == activeLayerId
                            val blendLabel = blendModes.firstOrNull { it.first == layer.blendMode }?.second ?: "Normal"
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) Color(0xFF5F27CD) else Color(0xFF0F3460))
                                    .clickable { activeLayerId = layer.id }.padding(5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(modifier = Modifier.size(36.dp, 26.dp).clip(RoundedCornerShape(3.dp)).background(Color.White)) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            layer.bitmap?.let { drawImage(it.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
                                        }
                                    }
                                    Text(layer.name, fontSize = 10.sp, color = Color.White, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(if (layer.visible) "O" else "X", fontSize = 14.sp, color = Color.White,
                                        modifier = Modifier.clickable {
                                            val idx = layers.indexOfFirst { it.id == layer.id }
                                            if (idx >= 0) { layers[idx] = layers[idx].copy(visible = !layers[idx].visible); compositeAllLayers() }
                                        })
                                }
                                // Blend mode (tap to cycle)
                                Text(
                                    blendLabel,
                                    fontSize = 9.sp,
                                    color = Color(0xFFAAAAAA),
                                    modifier = Modifier.clickable {
                                        val idx = layers.indexOfFirst { it.id == layer.id }
                                        if (idx >= 0) {
                                            val curIdx = blendModes.indexOfFirst { it.first == layer.blendMode }
                                            val next = blendModes[(curIdx + 1) % blendModes.size].first
                                            layers[idx] = layers[idx].copy(blendMode = next)
                                            compositeAllLayers()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Kontextuellt verktygsfält
        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF16213E))) {
            // Handlerad — alltid synlig, klicka för att expandera/kollapsera
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { toolbarExpanded = !toolbarExpanded }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(currentColor).border(1.5.dp, Color.White, CircleShape))
                    ColorRow(colorPalettes[selectedPalette] ?: kidColors, currentColor) { currentColor = it }
                }
                Text(if (toolbarExpanded) "▼" else "▲", fontSize = 11.sp, color = Color(0xFFAAAAAA))
            }
            AnimatedVisibility(visible = toolbarExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            when (drawMode) {

                DrawMode.DRAW, DrawMode.ERASE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        // Palett-väljare + färgrad
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Palett-chips
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                colorPalettes.keys.forEach { pal ->
                                    val selP = selectedPalette == pal
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                            .background(if (selP) Color(0xFF5F27CD) else Color(0xFF0F3460))
                                            .clickable { selectedPalette = pal }.padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) { Text(pal, fontSize = 9.sp, color = Color.White) }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ColorRow(colorPalettes[selectedPalette] ?: kidColors, currentColor) { currentColor = it }
                            Box(
                                modifier = Modifier.size(30.dp).clip(CircleShape)
                                    .background(Color(0xFF546E7A)).clickable {
                                        customR = (currentColor.red * 255).toInt().toFloat()
                                        customG = (currentColor.green * 255).toInt().toFloat()
                                        customB = (currentColor.blue * 255).toInt().toFloat()
                                        customA = (currentColor.alpha * 255).toInt().toFloat()
                                        showColorDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Storlek: ${brushSize.toInt()} px", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                                Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 2f..80f,
                                    modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = Color(0xFF5F27CD), activeTrackColor = Color(0xFF5F27CD)))
                                Text("Opacitet: ${(opacity * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9F43), activeTrackColor = Color(0xFFFF9F43)))
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                BgPicker(backgroundColors, backgroundColor) { backgroundColor = it }
                                if (drawMode == DrawMode.DRAW) {
                                    // Penseltyper rad 1
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        listOf(BrushType.NORMAL to "Pen", BrushType.NEON to "Neo", BrushType.MARKER to "Mrk", BrushType.RAINBOW to "RGB").forEach { (bt, lbl) ->
                                            val sel = brushType == bt
                                            Box(modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                                .background(if (sel) Color(0xFF5F27CD) else Color(0xFF0F3460))
                                                .clickable { brushType = bt }.padding(horizontal = 5.dp, vertical = 3.dp)
                                            ) { Text(lbl, fontSize = 9.sp, color = Color.White) }
                                        }
                                    }
                                    // Penseltyper rad 2
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        listOf(BrushType.STIPPLE to "Stip", BrushType.CHALK to "Krita", BrushType.WATERCOLOR to "Akv", BrushType.FLAT to "Flat").forEach { (bt, lbl) ->
                                            val sel = brushType == bt
                                            Box(modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                                .background(if (sel) Color(0xFF5F27CD) else Color(0xFF0F3460))
                                                .clickable { brushType = bt }.padding(horizontal = 5.dp, vertical = 3.dp)
                                            ) { Text(lbl, fontSize = 9.sp, color = Color.White) }
                                        }
                                    }
                                    // Symmetri + Textur
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Box(modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                            .background(if (symmetryH) Color(0xFFFF9F43) else Color(0xFF0F3460))
                                            .clickable { symmetryH = !symmetryH }.padding(horizontal = 5.dp, vertical = 3.dp)
                                        ) { Text("H-Sym", fontSize = 9.sp, color = Color.White) }
                                        listOf(TextureMode.NONE to "Ingen", TextureMode.CANVAS to "Canvas", TextureMode.PAPER to "Papper", TextureMode.KRAFT to "Kraft").forEach { (tm, lbl) ->
                                            val sel = textureMode == tm
                                            Box(modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                                .background(if (sel) Color(0xFFFF9F43) else Color(0xFF0F3460))
                                                .clickable { textureMode = tm; renderTick++ }.padding(horizontal = 5.dp, vertical = 3.dp)
                                            ) { Text(lbl, fontSize = 9.sp, color = Color.White) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                DrawMode.SPRAY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ColorRow(colorPalettes[selectedPalette] ?: kidColors, currentColor) { currentColor = it }
                            Box(
                                modifier = Modifier.size(30.dp).clip(CircleShape)
                                    .background(Color(0xFF546E7A)).clickable {
                                        customR = (currentColor.red * 255).toInt().toFloat()
                                        customG = (currentColor.green * 255).toInt().toFloat()
                                        customB = (currentColor.blue * 255).toInt().toFloat()
                                        customA = (currentColor.alpha * 255).toInt().toFloat()
                                        showColorDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                        Text("Spridning: ${brushSize.toInt()} px", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                        Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 10f..80f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF5F27CD), activeTrackColor = Color(0xFF5F27CD)))
                        Text("Opacitet: ${(opacity * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9F43), activeTrackColor = Color(0xFFFF9F43)))
                    }
                }

                DrawMode.SHAPE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ColorRow(colorPalettes[selectedPalette] ?: kidColors, currentColor) { currentColor = it }
                            Box(
                                modifier = Modifier.size(30.dp).clip(CircleShape)
                                    .background(Color(0xFF546E7A)).clickable {
                                        customR = (currentColor.red * 255).toInt().toFloat()
                                        customG = (currentColor.green * 255).toInt().toFloat()
                                        customB = (currentColor.blue * 255).toInt().toFloat()
                                        customA = (currentColor.alpha * 255).toInt().toFloat()
                                        showColorDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(
                                    ShapeType.LINE     to "/",
                                    ShapeType.RECT     to "[]",
                                    ShapeType.CIRCLE   to "O",
                                    ShapeType.TRIANGLE to "^",
                                    ShapeType.STAR     to "★",
                                    ShapeType.ARROW    to "→",
                                    ShapeType.HEXAGON  to "⬡"
                                ).forEach { (type, icon) ->
                                    val sel = selectedShape == type
                                    Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Color(0xFF5F27CD) else Color(0xFF0F3460)).clickable { selectedShape = type },
                                        contentAlignment = Alignment.Center) { Text(icon, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                                Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (shapeFilled) Color(0xFFFF9F43) else Color(0xFF0F3460)).clickable { shapeFilled = !shapeFilled },
                                    contentAlignment = Alignment.Center) { Text("Fill", fontSize = 11.sp, color = Color.White) }
                            }
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text("Tjocklek: ${brushSize.toInt()} px", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                                Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 2f..40f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF5F27CD), activeTrackColor = Color(0xFF5F27CD)))
                            }
                        }
                    }
                }

                DrawMode.FILL -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Välj färg och tryck på ytan", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            colorPalettes.keys.forEach { pal ->
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedPalette == pal) Color(0xFF5F27CD) else Color(0xFF0F3460))
                                    .clickable { selectedPalette = pal }.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) { Text(pal, fontSize = 9.sp, color = Color.White) }
                            }
                        }
                        ColorRow(colorPalettes[selectedPalette] ?: kidColors, currentColor) { currentColor = it }
                    }
                }

                DrawMode.TEXT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tryck på canvas för att placera text", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                        ColorRow(colorPalettes[selectedPalette] ?: kidColors, currentColor) { currentColor = it }
                    }
                }

                DrawMode.PARTICLE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ColorRow(colorPalettes[selectedPalette] ?: kidColors, currentColor) { currentColor = it }
                            Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF546E7A)).clickable {
                                customR = (currentColor.red * 255).toInt().toFloat()
                                customG = (currentColor.green * 255).toInt().toFloat()
                                customB = (currentColor.blue * 255).toInt().toFloat()
                                customA = (currentColor.alpha * 255).toInt().toFloat()
                                showColorDialog = true
                            }, contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Spridning: ${brushSize.toInt()} px", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                                Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 8f..100f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFFDD59), activeTrackColor = Color(0xFFFFDD59)))
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (brushType == BrushType.RAINBOW) Color(0xFFFF9F43) else Color(0xFF0F3460))
                                .clickable { brushType = if (brushType == BrushType.RAINBOW) BrushType.NORMAL else BrushType.RAINBOW }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text("Regnbåge", fontSize = 10.sp, color = Color.White) }
                        }
                    }
                }

                DrawMode.STAMP -> {
                    val selObj = selectedObjId?.let { id -> emojiObjects.firstOrNull { it.id == id } }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selObj != null) {
                            // Objekt markerat — visa kontroller
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selObj.emoji, fontSize = 30.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Storlek: ${selObj.size.toInt()} px", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                                    Slider(
                                        value = selObj.size,
                                        onValueChange = { ns ->
                                            val idx = emojiObjects.indexOfFirst { it.id == selObj.id }
                                            if (idx >= 0) { emojiObjects[idx] = emojiObjects[idx].copy(size = ns); renderTick++ }
                                        },
                                        valueRange = 20f..400f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF5F27CD), activeTrackColor = Color(0xFF5F27CD))
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Button(
                                        onClick = {
                                            val copy = selObj.copy(id = nextObjId++, x = selObj.x + 55f, y = selObj.y + 55f)
                                            emojiObjects.add(copy); selectedObjId = copy.id; renderTick++
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) { Text("Dupliera", fontSize = 10.sp) }
                                    Button(
                                        onClick = {
                                            // Dupliera till nytt lager
                                            val newLId = nextLayerId++
                                            val newBmp = if (canvasWidth > 0 && canvasHeight > 0)
                                                android.graphics.Bitmap.createBitmap(canvasWidth, canvasHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                            else null
                                            newBmp?.let { b ->
                                                android.graphics.Canvas(b).drawText(
                                                    selObj.emoji, selObj.x, selObj.y + selObj.size / 3f,
                                                    AndroidPaint().apply { textSize = selObj.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true }
                                                )
                                            }
                                            layers.add(Layer(id = newLId, name = selObj.emoji, bitmap = newBmp))
                                            activeLayerId = newLId; compositeAllLayers()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                                    ) { Text("Nytt lager", fontSize = 10.sp) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        val idx = layers.indexOfFirst { it.id == activeLayerId }
                                        if (idx >= 0) {
                                            ensureLayerBmp(idx)
                                            layers[idx].bitmap?.let { bmp ->
                                                android.graphics.Canvas(bmp).drawText(
                                                    selObj.emoji, selObj.x, selObj.y + selObj.size / 3f,
                                                    AndroidPaint().apply { textSize = selObj.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true }
                                                )
                                            }
                                            emojiObjects.removeAll { it.id == selObj.id }
                                            selectedObjId = null; compositeAllLayers()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BE881))
                                ) { Text("Baka in", fontSize = 11.sp) }
                                OutlinedButton(
                                    onClick = { emojiObjects.removeAll { it.id == selObj.id }; selectedObjId = null; renderTick++ },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text("Ta bort", fontSize = 11.sp) }
                                TextButton(onClick = { selectedObjId = null; renderTick++ }) {
                                    Text("Avmarkera", fontSize = 11.sp)
                                }
                            }
                        } else {
                            // Inget objekt valt — visa emoji-picker
                            if (emojiObjects.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${emojiObjects.size} objekt på canvas", fontSize = 11.sp,
                                        color = Color(0xFFAAAAAA), modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = {
                                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                                            if (idx >= 0) {
                                                ensureLayerBmp(idx)
                                                layers[idx].bitmap?.let { bmp ->
                                                    val c = android.graphics.Canvas(bmp)
                                                    emojiObjects.forEach { obj ->
                                                        c.drawText(obj.emoji, obj.x, obj.y + obj.size / 3f,
                                                            AndroidPaint().apply { textSize = obj.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true })
                                                    }
                                                }
                                                emojiObjects.clear(); selectedObjId = null; compositeAllLayers()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BE881))
                                    ) { Text("Baka in alla", fontSize = 11.sp) }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                stampCategories.keys.forEach { cat ->
                                    val sel = stampCategory == cat
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .background(if (sel) Color(0xFF5F27CD) else Color(0xFF0F3460))
                                            .clickable { stampCategory = cat }.padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) { Text(cat, fontSize = 11.sp, color = Color.White) }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                (stampCategories[stampCategory] ?: emptyList()).forEach { emoji ->
                                    val sel = selectedStamp == emoji
                                    Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) Color(0xFF5F27CD) else Color(0xFF0F3460))
                                        .then(if (sel) Modifier.border(2.dp, Color(0xFFFF9F43), RoundedCornerShape(10.dp)) else Modifier)
                                        .clickable { selectedStamp = emoji; selectedObjId = null },
                                        contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
                                }
                            }
                        }
                    }
                }

                DrawMode.EYEDROPPER -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(currentColor).border(2.dp, Color.White, CircleShape))
                        Text("Tryck på canvas för att välja färg", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                }

                DrawMode.SELECTION -> {
                    when (selState) {
                        SelState.NONE     -> Text("Rita en rektangel för att markera ett område", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                        SelState.DRAWING  -> Text("Slapp for att fanga markeringen", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                        SelState.CAPTURED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Dra for att flytta", fontSize = 12.sp, color = Color(0xFFAAAAAA), modifier = Modifier.weight(1f))
                            Button(onClick = { commitSelection() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BE881))) { Text("Bekrafta") }
                            OutlinedButton(onClick = { cancelSelection() }) { Text("Avbryt") }
                        }
                    }
                }

                DrawMode.DODGE, DrawMode.BURN -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (drawMode == DrawMode.DODGE) "Dodge — ljusgör penseldrag" else "Burn — mörkgör penseldrag",
                            fontSize = 11.sp, color = Color(0xFFAAAAAA)
                        )
                        Text("Storlek: ${brushSize.toInt()} px", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                        Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 4f..80f,
                            colors = SliderDefaults.colors(
                                thumbColor = if (drawMode == DrawMode.DODGE) Color(0xFFFFDD59) else Color(0xFF546E7A),
                                activeTrackColor = if (drawMode == DrawMode.DODGE) Color(0xFFFFDD59) else Color(0xFF546E7A)))
                    }
                }

                DrawMode.SMUDGE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Smudge — sudda ut/blanda pixlar", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                        Text("Penselstorlek: ${brushSize.toInt()} px", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                        Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 4f..60f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF48DBFB), activeTrackColor = Color(0xFF48DBFB)))
                    }
                }

            }

            // Rutnätspanel (visas när showGridPanel är true)
            if (showGridPanel) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // On/off-toggle för grid (oberoende av panel)
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (showGrid) Color(0xFFFF9F43) else Color(0xFF546E7A))
                            .clickable { showGrid = !showGrid }.padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (showGrid) "På" else "Av", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }

                    // Storleksknappar
                    listOf(40 to "S", 60 to "M", 100 to "L").forEach { (size, lbl) ->
                        val sel = gridSpacing == size
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (sel) Color(0xFFFF9F43) else Color(0xFF0F3460))
                                .clickable { gridSpacing = size }.padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(lbl, fontSize = 11.sp, color = Color.White) }
                    }
                }
            }
        }
        } // AnimatedVisibility
        } // Column toolbar
    }
}

// =============================================================================
// UI-komponenter
// =============================================================================

@Composable
private fun ColorRow(colors: List<Color>, current: Color, onPick: (Color) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        colors.forEach { color ->
            val sel = current == color
            Box(modifier = Modifier.size(if (sel) 36.dp else 30.dp).shadow(if (sel) 4.dp else 1.dp, CircleShape)
                .background(color, CircleShape)
                .then(if (sel) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                .clickable { onPick(color) })
        }
    }
}

@Composable
private fun BgPicker(colors: List<Color>, current: Color, onPick: (Color) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("BG", fontSize = 11.sp, color = Color(0xFFAAAAAA))
        colors.forEach { bgColor ->
            val sel = current == bgColor
            Box(modifier = Modifier.size(if (sel) 26.dp else 20.dp).shadow(if (sel) 3.dp else 1.dp, CircleShape)
                .background(bgColor, CircleShape)
                .border(if (sel) 2.dp else 0.5.dp, if (sel) Color.White else Color.Gray, CircleShape)
                .clickable { onPick(bgColor) })
        }
    }
}

@Composable
private fun TopBtn(label: String, color: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// =============================================================================
// Flood fill
// =============================================================================

private fun floodFill(bitmap: android.graphics.Bitmap, startX: Int, startY: Int, newColor: Int, tolerance: Int = 25) {
    val w = bitmap.width; val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val targetColor = pixels[startY * w + startX]
    if (targetColor == newColor) return
    fun dist(c1: Int, c2: Int) = abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)) +
            abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)) + abs((c1 and 0xFF) - (c2 and 0xFF))
    val queue = ArrayDeque<Int>()
    queue.add(startY * w + startX); pixels[startY * w + startX] = newColor
    val dirs = intArrayOf(-1, 1, -w, w)
    while (queue.isNotEmpty()) {
        val idx = queue.removeFirst()
        for (d in dirs) {
            val nIdx = idx + d; if (nIdx < 0 || nIdx >= pixels.size) continue
            if (d == -1 && idx % w == 0) continue; if (d == 1 && idx % w == w-1) continue
            if (pixels[nIdx] != newColor && dist(pixels[nIdx], targetColor) <= tolerance) {
                pixels[nIdx] = newColor; queue.add(nIdx)
            }
        }
    }
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
}

// =============================================================================
// Spara
// =============================================================================

private fun saveToGallery(context: Context, bitmap: android.graphics.Bitmap?) {
    if (bitmap == null) { Toast.makeText(context, "Rita nagot forst!", Toast.LENGTH_SHORT).show(); return }
    try {
        val filename = "Rita_${System.currentTimeMillis()}.png"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rita")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { s -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, s) }
            Toast.makeText(context, "Sparad i Bilder/Rita!", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, "Kunde inte spara", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Fel vid sparande", Toast.LENGTH_SHORT).show()
    }
}
