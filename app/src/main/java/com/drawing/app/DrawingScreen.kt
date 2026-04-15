package com.drawing.app

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
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
private data class ShapeObject(
    val id: Int,
    val type: ShapeType,
    val start: Offset,
    val end: Offset,
    val color: Color,
    val strokeWidth: Float,
    val filled: Boolean
)
private data class TextObject(
    val id: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val color: Color,
    val fontSize: Float,
    val style: TextStyleOption
)
private data class SpraySession(val dots: List<Offset>, val color: Color, val dotRadius: Float)
private enum class TextStyleOption { NORMAL, BOLD, ITALIC, MONO }
private data class TextStamp(val text: String, val x: Float, val y: Float, val color: Color, val fontSize: Float, val style: TextStyleOption = TextStyleOption.NORMAL)

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

private enum class DrawMode { DRAW, SPRAY, SHAPE, FILL, TEXT, STAMP, EYEDROPPER, SELECTION, DODGE, BURN, SMUDGE, PARTICLE, ERASE, ANIMATION, HAND }
private enum class ObjDragMode { MOVE, RESIZE }
private enum class ShapeType { LINE, RECT, CIRCLE, TRIANGLE, STAR, ARROW, HEXAGON, PENTAGON }
private enum class SelState  { NONE, DRAWING, CAPTURED }
private enum class BrushType { NORMAL, NEON, MARKER, RAINBOW, STIPPLE, CHALK, WATERCOLOR, FLAT, PENCIL, OIL, SPLATTER, CALLIGRAPHY }
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

private data class PsTool(val mode: DrawMode, val icon: String, val label: String)
private val toolList = listOf(
    PsTool(DrawMode.HAND,       "✋",  "Hand"),
    PsTool(DrawMode.SELECTION,  "⬚",  "Markera"),
    PsTool(DrawMode.DRAW,       "✏",  "Pensel"),
    PsTool(DrawMode.ERASE,      "◻",  "Sudda"),
    PsTool(DrawMode.SPRAY,      "◎",  "Spray"),
    PsTool(DrawMode.SMUDGE,     "〰",  "Smeta"),
    PsTool(DrawMode.SHAPE,      "△",  "Form"),
    PsTool(DrawMode.FILL,       "◪",  "Fyll"),
    PsTool(DrawMode.TEXT,       "T",  "Text"),
    PsTool(DrawMode.STAMP,      "⊛",  "Stämpel"),
    PsTool(DrawMode.EYEDROPPER, "⊙",  "Pipett"),
    PsTool(DrawMode.PARTICLE,   "⁂",  "Partikel"),
    PsTool(DrawMode.DODGE,      "◑",  "Ljusa"),
    PsTool(DrawMode.BURN,       "◐",  "Mörkna"),
    PsTool(DrawMode.ANIMATION,  "▶",  "Animera"),
)

private data class BrushSubTool(val type: BrushType, val icon: String, val label: String)
private val brushSubTools = listOf(
    BrushSubTool(BrushType.NORMAL,      "✏",  "Normal"),
    BrushSubTool(BrushType.PENCIL,      "✐",  "Blyerts"),
    BrushSubTool(BrushType.MARKER,      "▊",  "Markör"),
    BrushSubTool(BrushType.FLAT,        "▬",  "Flat"),
    BrushSubTool(BrushType.CALLIGRAPHY, "∫",  "Kalligrafi"),
    BrushSubTool(BrushType.CHALK,       "≈",  "Krita"),
    BrushSubTool(BrushType.WATERCOLOR,  "◌",  "Akvarell"),
    BrushSubTool(BrushType.OIL,         "⊞",  "Olja"),
    BrushSubTool(BrushType.STIPPLE,     "⠿",  "Prickar"),
    BrushSubTool(BrushType.SPLATTER,    "⁕",  "Stänk"),
    BrushSubTool(BrushType.NEON,        "◉",  "Neon"),
    BrushSubTool(BrushType.RAINBOW,     "◈",  "Regnbåge"),
)

private data class ShapeSubTool(val type: ShapeType, val icon: String, val label: String)
private val shapeSubTools = listOf(
    ShapeSubTool(ShapeType.LINE,      "╱",  "Linje"),
    ShapeSubTool(ShapeType.RECT,      "▭",  "Rektangel"),
    ShapeSubTool(ShapeType.CIRCLE,    "○",  "Cirkel"),
    ShapeSubTool(ShapeType.TRIANGLE,  "△",  "Triangel"),
    ShapeSubTool(ShapeType.STAR,      "★",  "Stjärna"),
    ShapeSubTool(ShapeType.ARROW,     "→",  "Pil"),
    ShapeSubTool(ShapeType.HEXAGON,   "⬡",  "Hexagon"),
    ShapeSubTool(ShapeType.PENTAGON,  "⬠",  "Pentagon"),
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

private fun highlightsFilter(bmp: android.graphics.Bitmap, amount: Float) {
    // amount -1..1: negativ = mörkare ljuspartier, positiv = ljusare
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val mask = ((lum - 0.5f) * 2f).coerceAtLeast(0f)
        val delta = (amount * mask * 100f).toInt()
        pixels[i] = (a shl 24) or ((r + delta).coerceIn(0,255) shl 16) or ((g + delta).coerceIn(0,255) shl 8) or (b + delta).coerceIn(0,255)
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun shadowsFilter(bmp: android.graphics.Bitmap, amount: Float) {
    // amount -1..1: negativ = mörkare skuggor, positiv = ljusare
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val mask = ((0.5f - lum) * 2f).coerceAtLeast(0f)
        val delta = (amount * mask * 100f).toInt()
        pixels[i] = (a shl 24) or ((r + delta).coerceIn(0,255) shl 16) or ((g + delta).coerceIn(0,255) shl 8) or (b + delta).coerceIn(0,255)
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun clarityFilter(bmp: android.graphics.Bitmap, amount: Float) {
    // Midtone-kontrast: förstärker detaljer i mellantonerna
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val mask = 1f - abs(lum * 2f - 1f) // max vid lum=0.5, noll vid 0 och 1
        fun adj(v: Int): Int {
            val n = v / 255f
            return ((0.5f + (n - 0.5f) * (1f + amount * mask * 1.5f)) * 255f).toInt().coerceIn(0, 255)
        }
        pixels[i] = (a shl 24) or (adj(r) shl 16) or (adj(g) shl 8) or adj(b)
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun textureFilter(bmp: android.graphics.Bitmap, amount: Float) {
    // Findetaljförstärkning (positiv) eller mjukgörning (negativ) via unsharp mask
    if (amount == 0f) return
    val w = bmp.width; val h = bmp.height
    val pixels = IntArray(w * h); bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val out = pixels.copyOf()
    // Begränsa styrkevärdet så artefakter inte uppstår
    val k = (amount * 0.3f).coerceIn(-0.3f, 0.3f)
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val c = pixels[y * w + x]; val a = (c shr 24) and 0xFF
        fun ch(px: Int, sh: Int) = (px shr sh) and 0xFF
        fun tex(sh: Int): Int {
            val center = ch(c, sh)
            // Medelvärde av 8 grannar (lågpassad version)
            val avg = (ch(pixels[(y-1)*w+x-1], sh) + ch(pixels[(y-1)*w+x], sh) + ch(pixels[(y-1)*w+x+1], sh) +
                       ch(pixels[y*w+x-1], sh) + ch(pixels[y*w+x+1], sh) +
                       ch(pixels[(y+1)*w+x-1], sh) + ch(pixels[(y+1)*w+x], sh) + ch(pixels[(y+1)*w+x+1], sh)) / 8
            // Unsharp mask: center + k*(center - avg)  →  positiv = skärper, negativ = mjukar
            return (center + k * (center - avg)).toInt().coerceIn(0, 255)
        }
        out[y * w + x] = (a shl 24) or (tex(16) shl 16) or (tex(8) shl 8) or tex(0)
    }
    bmp.setPixels(out, 0, w, 0, 0, w, h)
}

private fun temperatureFilter(bmp: android.graphics.Bitmap, amount: Float) {
    // amount -1 (kallt/blått) till +1 (varmt/orange)
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val delta = (amount * 40f).toInt()
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = ((px shr 16) and 0xFF + delta).coerceIn(0, 255)
        val g = (px shr 8) and 0xFF
        val b = ((px and 0xFF) - delta).coerceIn(0, 255)
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun tintFilter(bmp: android.graphics.Bitmap, amount: Float) {
    // amount -1 (grön) till +1 (magenta)
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val delta = (amount * 30f).toInt()
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = ((px shr 16) and 0xFF + delta).coerceIn(0, 255)
        val g = ((px shr 8) and 0xFF - delta).coerceIn(0, 255)
        val b = ((px and 0xFF) + delta).coerceIn(0, 255)
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

private fun fadeFilter(bmp: android.graphics.Bitmap, amount: Float) {
    // Film fade: lyfter svarta, minskar kontrast lätt
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val lift = (amount * 55f).toInt()
    val compress = 1f - amount * 0.25f
    for (i in pixels.indices) {
        val px = pixels[i]; val a = (px shr 24) and 0xFF
        val r = (((px shr 16) and 0xFF) * compress + lift).toInt().coerceIn(0, 255)
        val g = (((px shr 8) and 0xFF) * compress + lift).toInt().coerceIn(0, 255)
        val b = ((px and 0xFF) * compress + lift).toInt().coerceIn(0, 255)
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
}

// =============================================================================
// Justeringsdefinitioner (chip-lista för Adjustments-baren)
// =============================================================================
private data class AdjDef(val label: String, val key: String, val range: ClosedFloatingPointRange<Float>)
private val adjDefs = listOf(
    AdjDef("Textur",     "texture",     -1f..1f),
    AdjDef("Klarhet",    "clarity",     -1f..1f),
    AdjDef("Skärpa",     "sharpen",      0f..1f),
    AdjDef("Kontrast",   "contrast",    -1f..1f),
    AdjDef("Exponering", "brightness",  -1f..1f),
    AdjDef("Ljuspartier","highlights",  -1f..1f),
    AdjDef("Skuggor",    "shadows",     -1f..1f),
    AdjDef("Mättnad",    "saturation",  -1f..1f),
    AdjDef("Temp.",      "temperature", -1f..1f),
    AdjDef("Nyans",      "tint",        -1f..1f),
    AdjDef("Vinjett",    "vignette",     0f..1f),
    AdjDef("Fade",       "fade",         0f..1f),
)

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
// Crop / Rotate / Transform-hjälpfunktioner
// =============================================================================

private fun cropBitmap(bmp: android.graphics.Bitmap, l: Int, t: Int, r: Int, b: Int): android.graphics.Bitmap {
    val cl = l.coerceIn(0, bmp.width - 1); val ct = t.coerceIn(0, bmp.height - 1)
    val cr = r.coerceIn(cl + 1, bmp.width); val cb = b.coerceIn(ct + 1, bmp.height)
    return android.graphics.Bitmap.createBitmap(bmp, cl, ct, cr - cl, cb - ct)
}

private fun cropToAspect(bmp: android.graphics.Bitmap, targetW: Int, targetH: Int): android.graphics.Bitmap {
    val srcRatio = bmp.width.toFloat() / bmp.height.toFloat()
    val dstRatio = targetW.toFloat() / targetH.toFloat()
    val (cl, ct, cr, cb) = if (srcRatio > dstRatio) {
        val newW = (bmp.height * dstRatio).toInt()
        val ox = (bmp.width - newW) / 2
        listOf(ox, 0, ox + newW, bmp.height)
    } else {
        val newH = (bmp.width / dstRatio).toInt()
        val oy = (bmp.height - newH) / 2
        listOf(0, oy, bmp.width, oy + newH)
    }
    return cropBitmap(bmp, cl, ct, cr, cb)
}

private fun straightenBitmap(bmp: android.graphics.Bitmap, degrees: Float): android.graphics.Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(degrees, bmp.width / 2f, bmp.height / 2f) }
    val rotated = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    // Beskär till original-dimensioner, centrat
    val cl = ((rotated.width - bmp.width) / 2f).coerceAtLeast(0f).toInt()
    val ct = ((rotated.height - bmp.height) / 2f).coerceAtLeast(0f).toInt()
    val cw = bmp.width.coerceAtMost(rotated.width - cl)
    val ch = bmp.height.coerceAtMost(rotated.height - ct)
    val cropped = android.graphics.Bitmap.createBitmap(rotated, cl, ct, cw, ch)
    return android.graphics.Bitmap.createScaledBitmap(cropped, bmp.width, bmp.height, true)
}

private fun skewBitmap(bmp: android.graphics.Bitmap, skewX: Float, skewY: Float): android.graphics.Bitmap {
    val out = android.graphics.Bitmap.createBitmap(bmp.width, bmp.height, android.graphics.Bitmap.Config.ARGB_8888)
    val matrix = android.graphics.Matrix().apply { setSkew(skewX, skewY, bmp.width / 2f, bmp.height / 2f) }
    android.graphics.Canvas(out).drawBitmap(bmp, matrix, null)
    return out
}

private data class AspectPreset(val label: String, val w: Int, val h: Int)
private val aspectPresets = listOf(
    AspectPreset("Free", 0, 0),
    AspectPreset("1:1", 1, 1),
    AspectPreset("4:3", 4, 3),
    AspectPreset("16:9", 16, 9),
    AspectPreset("9:16", 9, 16),
    AspectPreset("3:2", 3, 2),
    AspectPreset("2:3", 2, 3),
    AspectPreset("IG Post", 1, 1),
    AspectPreset("IG Story", 9, 16),
    AspectPreset("IG Land.", 1910, 1000),
    AspectPreset("FB Post", 1200, 630),
    AspectPreset("FB Cover", 820, 312),
    AspectPreset("FB Story", 1080, 1920),
    AspectPreset("TW Post", 1600, 900),
    AspectPreset("TW Header", 1500, 500),
    AspectPreset("TT Video", 1080, 1920),
    AspectPreset("YT Thumb", 1280, 720),
)

// =============================================================================
// DrawingScreen
// =============================================================================

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
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
    var secondaryColor  by remember { mutableStateOf(Color.White) }
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

    // Text-objekt (eget lager, rörliga)
    val textObjects      = remember { mutableStateListOf<TextObject>() }
    var nextTextId       by remember { mutableIntStateOf(0) }
    var selectedTextId   by remember { mutableStateOf<Int?>(null) }
    var textDragOrigin   by remember { mutableStateOf(Offset.Zero) }
    var textOrigPos      by remember { mutableStateOf(Offset.Zero) }

    // Universell selektion — vilket objekt-typ är valt
    // "shape", "text", "emoji" eller null (bitmap-selektion)
    var selObjType       by remember { mutableStateOf<String?>(null) }

    // Rutnät
    var showGrid        by remember { mutableStateOf(false) }
    var showGridPanel   by remember { mutableStateOf(false) }
    var toolbarExpanded  by remember { mutableStateOf(false) }
    var sidebarVisible   by remember { mutableStateOf(true) }
    var topBarVisible    by remember { mutableStateOf(true) }
    var showStampPicker  by remember { mutableStateOf(false) }
    var gridSpacing     by remember { mutableIntStateOf(60) }

    // Zoom & pan
    var zoomScale      by remember { mutableFloatStateOf(1f) }
    var zoomOffX       by remember { mutableFloatStateOf(0f) }
    var zoomOffY       by remember { mutableFloatStateOf(0f) }
    var prevPinchDist  by remember { mutableFloatStateOf(0f) }
    var prevPinchMidX  by remember { mutableFloatStateOf(0f) }
    var prevPinchMidY  by remember { mutableFloatStateOf(0f) }

    // Import bild
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Spara/ladda projekt
    var showSaveProjectDialog by remember { mutableStateOf(false) }
    var showLoadProjectDialog by remember { mutableStateOf(false) }
    var projectName           by remember { mutableStateOf("") }
    var savedProjects         by remember { mutableStateOf(listOf<String>()) }

    // Export format
    var showExportDialog by remember { mutableStateOf(false) }
    var exportBitmap     by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Text-stilar
    var textStyle by remember { mutableStateOf(TextStyleOption.NORMAL) }

    // Animering
    val animFrames  = remember { mutableStateListOf<android.graphics.Bitmap>() }
    var animFrame   by remember { mutableIntStateOf(0) }
    var animPlaying by remember { mutableStateOf(false) }
    var animFps     by remember { mutableIntStateOf(8) }
    var showOnion   by remember { mutableStateOf(true) }

    // Formförhandsvisning (vid ritning)
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd   by remember { mutableStateOf<Offset?>(null) }

    // Vektor-form-objekt (eget lager, alltid redigerbara)
    val shapeObjects       = remember { mutableStateListOf<ShapeObject>() }
    var nextShapeId        by remember { mutableIntStateOf(0) }
    var selectedShapeId    by remember { mutableStateOf<Int?>(null) }
    var shapeDragOrigin    by remember { mutableStateOf(Offset.Zero) }
    var shapeOrigStart     by remember { mutableStateOf(Offset.Zero) }
    var shapeOrigEnd       by remember { mutableStateOf(Offset.Zero) }
    var shapeResizeCorner  by remember { mutableStateOf<Int?>(null) } // 0=TL,1=TR,2=BR,3=BL

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
    var showCropDialog     by remember { mutableStateOf(false) }
    var cropTab            by remember { mutableIntStateOf(0) }       // 0=Crop 1=Rotera 2=Transform
    var selectedAspect     by remember { mutableIntStateOf(0) }
    // Crop handles i canvas-koordinater (0..1 normaliserade)
    var cropL              by remember { mutableFloatStateOf(0f) }
    var cropT              by remember { mutableFloatStateOf(0f) }
    var cropR              by remember { mutableFloatStateOf(1f) }
    var cropB              by remember { mutableFloatStateOf(1f) }
    // vilket handtag dras: "L","T","R","B","TL","TR","BR","BL" eller null
    var cropDragHandle     by remember { mutableStateOf<String?>(null) }
    // Live-preview för Rotera/Transform
    var straightenDeg      by remember { mutableFloatStateOf(0f) }
    var skewH              by remember { mutableFloatStateOf(0f) }
    var skewV              by remember { mutableFloatStateOf(0f) }
    // Sparade originallager för att kunna återställa under crop-session
    var cropOrigLayers     by remember { mutableStateOf<List<android.graphics.Bitmap?>>(emptyList()) }
    var showColorDialog    by remember { mutableStateOf(false) }
    var openMenu           by remember { mutableStateOf<String?>(null) }
    var cursorPos          by remember { mutableStateOf<Offset?>(null) }
    var toolFlyout         by remember { mutableStateOf<DrawMode?>(null) }
    var editingBgColor     by remember { mutableStateOf(false) }
    var editingCanvasBg    by remember { mutableStateOf(false) }

    // Filter-dialog värden
    var filterContrast    by remember { mutableFloatStateOf(1f) }
    var filterBrightness  by remember { mutableFloatStateOf(1f) }
    var filterSaturation  by remember { mutableFloatStateOf(1f) }
    var filterBlur        by remember { mutableFloatStateOf(1f) }
    var filterSharpen     by remember { mutableFloatStateOf(0f) }
    var filterNoise       by remember { mutableFloatStateOf(0f) }
    var filterVignette    by remember { mutableFloatStateOf(0f) }
    var filterHueShift    by remember { mutableFloatStateOf(0f) }

    // Justeringar (Adjustments) bottom-bar
    var showAdjustBar        by remember { mutableStateOf(false) }
    var adjOrigBitmap        by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var adjPreviewComposite  by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var adjSelectedChip      by remember { mutableIntStateOf(0) }
    var adjTick              by remember { mutableIntStateOf(0) }
    val adjValues            = remember { mutableStateMapOf(
        "texture" to 0f, "clarity" to 0f, "sharpen" to 0f,
        "contrast" to 0f, "brightness" to 0f, "highlights" to 0f,
        "shadows" to 0f, "saturation" to 0f, "temperature" to 0f,
        "tint" to 0f, "vignette" to 0f, "fade" to 0f
    ) }



    val activity = context as? androidx.activity.ComponentActivity

    // Bildimport-launcher (måste vara på composable-toppnivå)
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingImportUri = uri
    }

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
            BrushType.PENCIL -> {
                // Blyerts: tunn med kornig graphit-struktur (flera offsetlager med låg opacitet)
                val rng = Random(dp.path.hashCode().toLong())
                val grainLayers = 6
                repeat(grainLayers) { layer ->
                    val paint = AndroidPaint().apply {
                        color = dp.color.copy(alpha = 0.12f + layer * 0.06f).toArgb()
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = dp.strokeWidth * (0.35f + layer * 0.08f)
                        strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                    }
                    val matrix = android.graphics.Matrix().apply {
                        setTranslate(
                            (rng.nextFloat() - 0.5f) * dp.strokeWidth * 0.25f,
                            (rng.nextFloat() - 0.5f) * dp.strokeWidth * 0.25f
                        )
                    }
                    val shifted = android.graphics.Path(dp.path.asAndroidPath()).also { it.transform(matrix) }
                    c.drawPath(shifted, paint)
                }
                // Extra kärn-linje för skärpa
                val corePaint = AndroidPaint().apply {
                    color = dp.color.copy(alpha = 0.55f).toArgb()
                    style = AndroidPaint.Style.STROKE
                    strokeWidth = dp.strokeWidth * 0.28f
                    strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                }
                c.drawPath(dp.path.asAndroidPath(), corePaint)
            }
            BrushType.OIL -> {
                // Oljefärg: tjocka penseldrag med synliga borststreck
                val rng = Random(dp.color.toArgb().toLong())
                val bristleCount = 7
                val measure = android.graphics.PathMeasure(dp.path.asAndroidPath(), false)
                // Bas-lager
                val basePaint = AndroidPaint().apply {
                    color = dp.color.copy(alpha = 0.75f).toArgb()
                    style = AndroidPaint.Style.STROKE
                    strokeWidth = dp.strokeWidth * 1.1f
                    strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                }
                c.drawPath(dp.path.asAndroidPath(), basePaint)
                // Borststreck ovanpå
                repeat(bristleCount) { b ->
                    val offset = (b - bristleCount / 2f) * dp.strokeWidth * 0.18f
                    val paint = AndroidPaint().apply {
                        color = dp.color.copy(
                            alpha = 0.35f + rng.nextFloat() * 0.3f,
                            red = (dp.color.red * (0.88f + rng.nextFloat() * 0.24f)).coerceIn(0f, 1f),
                            green = (dp.color.green * (0.88f + rng.nextFloat() * 0.24f)).coerceIn(0f, 1f),
                            blue = (dp.color.blue * (0.88f + rng.nextFloat() * 0.24f)).coerceIn(0f, 1f)
                        ).toArgb()
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = dp.strokeWidth * (0.08f + rng.nextFloat() * 0.12f)
                        strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                    }
                    val matrix = android.graphics.Matrix().apply { setTranslate(offset, offset * 0.4f) }
                    val shifted = android.graphics.Path(dp.path.asAndroidPath()).also { it.transform(matrix) }
                    c.drawPath(shifted, paint)
                }
            }
            BrushType.SPLATTER -> {
                // Stänk: slumpmässiga droppar längs och runt strecket
                val measure = android.graphics.PathMeasure(dp.path.asAndroidPath(), false)
                val pos = FloatArray(2); val tan = FloatArray(2)
                val rng = Random(dp.strokeWidth.toLong() xor dp.color.toArgb().toLong())
                val spacing = dp.strokeWidth * 0.5f
                var dist = 0f
                val dropPaint = AndroidPaint().apply {
                    color = dp.color.toArgb(); style = AndroidPaint.Style.FILL; isAntiAlias = true
                }
                while (dist <= measure.length) {
                    measure.getPosTan(dist, pos, tan)
                    // Huvud-droppe
                    val mainR = dp.strokeWidth * (0.15f + rng.nextFloat() * 0.25f)
                    dropPaint.alpha = (180 + rng.nextInt(75))
                    c.drawCircle(pos[0], pos[1], mainR, dropPaint)
                    // 2-5 satel-droppar runt
                    val satelliteCount = 2 + rng.nextInt(4)
                    repeat(satelliteCount) {
                        val angle = rng.nextFloat() * 2f * PI.toFloat()
                        val dist2 = dp.strokeWidth * (0.3f + rng.nextFloat() * 1.2f)
                        val sx = pos[0] + cos(angle) * dist2
                        val sy = pos[1] + sin(angle) * dist2
                        val sr = mainR * (0.15f + rng.nextFloat() * 0.55f)
                        dropPaint.alpha = (80 + rng.nextInt(120))
                        c.drawCircle(sx, sy, sr, dropPaint)
                    }
                    dist += spacing + rng.nextFloat() * spacing
                }
            }
            BrushType.CALLIGRAPHY -> {
                // Kalligrafi: vinklad flat pensel — smalare vid horisontell rörelse, bredare vid vertikal
                val measure = android.graphics.PathMeasure(dp.path.asAndroidPath(), false)
                val pos = FloatArray(2); val tan = FloatArray(2)
                val paint = AndroidPaint().apply {
                    color = dp.color.copy(alpha = dp.color.alpha * 0.92f).toArgb()
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
                }
                val segLen = (dp.strokeWidth * 0.4f).coerceAtLeast(1f)
                var d = segLen
                val halfW = dp.strokeWidth * 1.5f
                while (d <= measure.length) {
                    measure.getPosTan(d, pos, tan)
                    // Tangens-vinkeln avgör bredden — kalligrafisk effekt vid 45° vinkel
                    val angle = atan2(tan[1], tan[0])
                    val calWidth = (halfW * abs(sin(angle - PI.toFloat() / 4f)) * 2f + dp.strokeWidth * 0.15f)
                    paint.strokeWidth = calWidth.coerceAtLeast(1f)
                    val prevD = (d - segLen).coerceAtLeast(0f)
                    measure.getPosTan(prevD, pos, tan)
                    val x0 = pos[0]; val y0 = pos[1]
                    measure.getPosTan(d, pos, tan)
                    c.drawLine(x0, y0, pos[0], pos[1], paint)
                    d += segLen
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

    fun buildPentagonPath(start: Offset, end: Offset): android.graphics.Path {
        val cx = (start.x + end.x) / 2f; val cy = (start.y + end.y) / 2f
        val r = min(abs(end.x - start.x), abs(end.y - start.y)) / 2f
        val path = android.graphics.Path()
        for (i in 0 until 5) {
            val angle = Math.toRadians((i * 72.0 - 90.0))
            val x = cx + r * cos(angle).toFloat(); val y = cy + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); return path
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
            ShapeType.PENTAGON -> c.drawPath(buildPentagonPath(shape.start, shape.end), paint)
        }
    }

    fun shapeObjHit(obj: ShapeObject, pt: Offset, margin: Float = 30f): Boolean {
        val minX = min(obj.start.x, obj.end.x) - margin
        val maxX = max(obj.start.x, obj.end.x) + margin
        val minY = min(obj.start.y, obj.end.y) - margin
        val maxY = max(obj.start.y, obj.end.y) + margin
        return pt.x in minX..maxX && pt.y in minY..maxY
    }
    // Returnerar 0=TL,1=TR,2=BR,3=BL om pt är nära ett hörn-handtag, annars null
    fun shapeCornerHit(obj: ShapeObject, pt: Offset, radius: Float = 28f): Int? {
        val corners = listOf(
            Offset(min(obj.start.x, obj.end.x), min(obj.start.y, obj.end.y)),
            Offset(max(obj.start.x, obj.end.x), min(obj.start.y, obj.end.y)),
            Offset(max(obj.start.x, obj.end.x), max(obj.start.y, obj.end.y)),
            Offset(min(obj.start.x, obj.end.x), max(obj.start.y, obj.end.y))
        )
        return corners.indexOfFirst { (it - pt).getDistance() <= radius }.takeIf { it >= 0 }
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
                val paint = AndroidPaint().apply {
                    color = action.stamp.color.toArgb(); textSize = action.stamp.fontSize; isAntiAlias = true
                    typeface = when (action.stamp.style) {
                        TextStyleOption.BOLD   -> android.graphics.Typeface.DEFAULT_BOLD
                        TextStyleOption.ITALIC -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                        TextStyleOption.MONO   -> android.graphics.Typeface.MONOSPACE
                        TextStyleOption.NORMAL -> android.graphics.Typeface.DEFAULT
                    }
                }
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

    fun compositeWithObjects(): android.graphics.Bitmap? {
        val base = cachedBitmap ?: return null
        val out = android.graphics.Bitmap.createBitmap(base.width, base.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawBitmap(base, 0f, 0f, null)
        // Vektor-former
        shapeObjects.forEach { obj ->
            val paint = AndroidPaint().apply {
                color = obj.color.toArgb()
                style = if (obj.filled) AndroidPaint.Style.FILL_AND_STROKE else AndroidPaint.Style.STROKE
                strokeWidth = obj.strokeWidth; strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
            }
            drawShapeOnCanvas(obj.let { DrawnShape(it.type, it.start, it.end, it.color, it.strokeWidth, it.filled) }, c)
        }
        // Text-objekt
        textObjects.forEach { t ->
            val paint = AndroidPaint().apply {
                color = t.color.toArgb(); textSize = t.fontSize; isAntiAlias = true
                typeface = when (t.style) {
                    TextStyleOption.BOLD -> android.graphics.Typeface.DEFAULT_BOLD
                    TextStyleOption.ITALIC -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                    TextStyleOption.MONO -> android.graphics.Typeface.MONOSPACE
                    TextStyleOption.NORMAL -> android.graphics.Typeface.DEFAULT
                }
            }
            c.drawText(t.text, t.x, t.y, paint)
        }
        // Emoji-objekt
        emojiObjects.forEach { obj ->
            c.drawText(obj.emoji, obj.x, obj.y + obj.size / 3f,
                AndroidPaint().apply { textSize = obj.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true })
        }
        return out
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

    // -- Crop/Transform hjälp --

    fun applyToAllLayers(fn: (android.graphics.Bitmap) -> android.graphics.Bitmap) {
        layers.indices.forEach { i ->
            val bmp = layers[i].bitmap ?: return@forEach
            val out = fn(bmp)
            val newBmp = android.graphics.Bitmap.createBitmap(bmp.width, bmp.height, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(newBmp).drawBitmap(out, 0f, 0f, null)
            layers[i] = layers[i].copy(bitmap = newBmp)
        }
        compositeAllLayers()
    }

    fun applyCrop() {
        applyToAllLayers { bmp ->
            cropBitmap(bmp,
                (cropL * bmp.width).toInt(), (cropT * bmp.height).toInt(),
                (cropR * bmp.width).toInt(), (cropB * bmp.height).toInt())
                .let { android.graphics.Bitmap.createScaledBitmap(it, bmp.width, bmp.height, true) }
        }
        cropL = 0f; cropT = 0f; cropR = 1f; cropB = 1f
    }

    fun restoreOrigLayers() {
        cropOrigLayers.forEachIndexed { i, origBmp ->
            if (i < layers.size && origBmp != null) {
                val copy = android.graphics.Bitmap.createBitmap(origBmp)
                layers[i] = layers[i].copy(bitmap = copy)
            }
        }
        compositeAllLayers()
        cropL = 0f; cropT = 0f; cropR = 1f; cropB = 1f
        straightenDeg = 0f; skewH = 0f; skewV = 0f
        renderTick++
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

    // Projekt-funktioner
    fun saveProject(name: String) {
        if (name.isBlank()) return
        val dir = java.io.File(context.filesDir, "projects/$name").also { it.mkdirs() }
        layers.forEachIndexed { i, layer ->
            layer.bitmap?.let { bmp ->
                java.io.FileOutputStream(java.io.File(dir, "layer_$i.png")).use {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        java.io.File(dir, "manifest.txt").writeText(
            layers.mapIndexed { i, l -> "$i|${l.name}|${l.visible}|${l.blendMode.name}" }.joinToString("\n")
        )
        Toast.makeText(context, "Projekt sparat: $name", Toast.LENGTH_SHORT).show()
    }
    fun loadProject(name: String) {
        val dir = java.io.File(context.filesDir, "projects/$name"); if (!dir.exists()) return
        val manifest = try { java.io.File(dir, "manifest.txt").readLines() } catch (e: Exception) { return }
        val newLayers = manifest.mapIndexedNotNull { i, line ->
            val parts = line.split("|"); if (parts.size < 4) return@mapIndexedNotNull null
            val bmpFile = java.io.File(dir, "layer_$i.png")
            val bmp = if (bmpFile.exists()) BitmapFactory.decodeFile(bmpFile.absolutePath)?.copy(android.graphics.Bitmap.Config.ARGB_8888, true) else null
            Layer(id = i, name = parts[1], bitmap = bmp, visible = parts[2] == "true",
                blendMode = try { PorterDuff.Mode.valueOf(parts[3]) } catch (e: Exception) { PorterDuff.Mode.SRC_OVER })
        }
        if (newLayers.isNotEmpty()) {
            layers.clear(); layers.addAll(newLayers)
            nextLayerId = newLayers.size; activeLayerId = newLayers.last().id
            compositeAllLayers()
        }
    }
    fun listProjects(): List<String> = java.io.File(context.filesDir, "projects").let {
        if (it.exists()) it.list()?.sorted() ?: emptyList() else emptyList()
    }

    // Animeringsloop
    LaunchedEffect(animPlaying, animFps) {
        while (animPlaying) {
            kotlinx.coroutines.delay(1000L / animFps)
            if (animFrames.isNotEmpty()) animFrame = (animFrame + 1) % animFrames.size
        }
    }

    // Import bild när canvas är redo
    LaunchedEffect(pendingImportUri, canvasWidth, canvasHeight) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        if (canvasWidth == 0 || canvasHeight == 0) return@LaunchedEffect
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (src != null) {
            val scaled = android.graphics.Bitmap.createScaledBitmap(src, canvasWidth, canvasHeight, true)
                .copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val newId = nextLayerId++
            layers.add(Layer(id = newId, name = "Importerad", bitmap = scaled))
            activeLayerId = newId; compositeAllLayers()
        }
        pendingImportUri = null
    }

    LaunchedEffect(backgroundColor) { compositeAllLayers() }

    // Spara kopior av alla lager när crop-dialogen öppnas (för Återställ original)
    LaunchedEffect(showCropDialog) {
        if (showCropDialog) {
            cropOrigLayers = layers.map { it.bitmap?.let { b -> android.graphics.Bitmap.createBitmap(b) } }
        }
    }

    // Spara aktivt lager när adjustments öppnas
    LaunchedEffect(showAdjustBar) {
        if (showAdjustBar) {
            val idx = layers.indexOfFirst { it.id == activeLayerId }
            adjOrigBitmap = if (idx >= 0) layers[idx].bitmap?.let { android.graphics.Bitmap.createBitmap(it) } else null
            adjPreviewComposite = null
        } else {
            adjOrigBitmap = null; adjPreviewComposite = null
        }
    }

    // Live preview för Adjustments — beräknas asynkront vid slider-ändring
    LaunchedEffect(adjTick) {
        if (!showAdjustBar) return@LaunchedEffect
        val orig = adjOrigBitmap ?: return@LaunchedEffect
        kotlinx.coroutines.delay(30)
        // Läs alla värden på main-tråden
        val vTexture     = adjValues["texture"]     ?: 0f
        val vClarity     = adjValues["clarity"]     ?: 0f
        val vSharpen     = adjValues["sharpen"]     ?: 0f
        val vContrast    = adjValues["contrast"]    ?: 0f
        val vBrightness  = adjValues["brightness"]  ?: 0f
        val vHighlights  = adjValues["highlights"]  ?: 0f
        val vShadows     = adjValues["shadows"]     ?: 0f
        val vSaturation  = adjValues["saturation"]  ?: 0f
        val vTemperature = adjValues["temperature"] ?: 0f
        val vTint        = adjValues["tint"]        ?: 0f
        val vVignette    = adjValues["vignette"]    ?: 0f
        val vFade        = adjValues["fade"]        ?: 0f
        val w = canvasWidth; val h = canvasHeight
        if (w == 0 || h == 0) return@LaunchedEffect
        val bgArgb = backgroundColor.toArgb()
        val activeId = activeLayerId
        val layerSnap = layers.map { Triple(it.id, it.visible, it.blendMode) }
        // Pixel-beräkning på bakgrundstråd
        val adjBmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            android.graphics.Bitmap.createBitmap(orig).also { bmp ->
                if (vTemperature != 0f) temperatureFilter(bmp, vTemperature)
                if (vTint != 0f) tintFilter(bmp, vTint)
                if (vContrast != 0f) applyContrastFilter(bmp, (1f + vContrast * 1.5f).coerceAtLeast(0.1f))
                if (vBrightness != 0f) applyBrightnessFilter(bmp, 1f + vBrightness)
                if (vHighlights != 0f) highlightsFilter(bmp, vHighlights)
                if (vShadows != 0f) shadowsFilter(bmp, vShadows)
                if (vSaturation != 0f) applySaturationFilter(bmp, (1f + vSaturation * 2f).coerceAtLeast(0f))
                if (vClarity != 0f) clarityFilter(bmp, vClarity)
                if (vTexture != 0f) textureFilter(bmp, vTexture)
                if (vSharpen > 0f) sharpenFilter(bmp, vSharpen * 2f)
                if (vVignette > 0f) vignetteFilter(bmp, vVignette * 2f)
                if (vFade > 0f) fadeFilter(bmp, vFade)
            }
        }
        // Compositering på main-tråd (säker åtkomst till layers)
        val composite = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(composite).also { c ->
            c.drawColor(bgArgb)
            layerSnap.forEach { (id, visible, blendMode) ->
                if (!visible) return@forEach
                val bmp = if (id == activeId) adjBmp else layers.firstOrNull { it.id == id }?.bitmap
                bmp?.let { c.drawBitmap(it, 0f, 0f, AndroidPaint().apply { xfermode = PorterDuffXfermode(blendMode) }) }
            }
        }
        adjPreviewComposite = composite
        renderTick++
    }

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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(TextStyleOption.NORMAL to "Normal", TextStyleOption.BOLD to "B",
                               TextStyleOption.ITALIC to "I", TextStyleOption.MONO to "Mono").forEach { (s, lbl) ->
                            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (textStyle == s) Color(0xFF5F27CD) else Color(0xFF546E7A))
                                .clickable { textStyle = s }.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text(lbl, fontSize = 12.sp, color = Color.White) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (textInput.isNotBlank()) {
                        val tObj = TextObject(nextTextId++, textInput, pendingTextPos.x, pendingTextPos.y, currentColor, textFontSize, textStyle)
                        textObjects.add(tObj)
                        selectedTextId = tObj.id
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

    // HSV-färgväljare
    if (showColorDialog) {
        val initColor = if (editingBgColor) secondaryColor else currentColor
        HsvColorPickerDialog(
            initialColor = initColor,
            title = if (editingBgColor) "Sekundärfärg" else "Ritfärg",
            onColorSelected = { chosen ->
                if (editingBgColor) secondaryColor = chosen else currentColor = chosen
            },
            onDismiss = { showColorDialog = false }
        )
    }

    // Canvas-bakgrundsfärgväljare (via Image-menyn)
    if (editingCanvasBg) {
        HsvColorPickerDialog(
            initialColor = backgroundColor,
            title = "Bakgrundsfärg",
            onColorSelected = { chosen -> backgroundColor = chosen },
            onDismiss = { editingCanvasBg = false }
        )
    }

    // Export-dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportera som...") },
            text  = { Text("Välj filformat för att spara ritningen.") },
            confirmButton = {
                Button(onClick = { saveToGallery(context, exportBitmap); showExportDialog = false }) { Text("PNG") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showExportDialog = false }) { Text("Avbryt") }
                    Button(onClick = { saveToGalleryJpg(context, exportBitmap); showExportDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF546E7A))
                    ) { Text("JPG") }
                }
            }
        )
    }

    // Spara-projekt-dialog
    if (showSaveProjectDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProjectDialog = false },
            title = { Text("Spara projekt") },
            text = {
                OutlinedTextField(value = projectName, onValueChange = { projectName = it },
                    label = { Text("Projektnamn") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { saveProject(projectName); showSaveProjectDialog = false }) { Text("Spara") }
            },
            dismissButton = { TextButton(onClick = { showSaveProjectDialog = false }) { Text("Avbryt") } }
        )
    }

    // Öppna-projekt-dialog
    if (showLoadProjectDialog) {
        AlertDialog(
            onDismissRequest = { showLoadProjectDialog = false },
            title = { Text("Öppna projekt") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (savedProjects.isEmpty()) Text("Inga sparade projekt", color = Color.Gray)
                    else savedProjects.forEach { name ->
                        TextButton(onClick = { loadProject(name); showLoadProjectDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLoadProjectDialog = false }) { Text("Stäng") } }
        )
    }

    // =========================================================================
    // Layout
    // =========================================================================

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {

        // Toprad
        AnimatedVisibility(visible = topBarVisible, enter = expandVertically(), exit = shrinkVertically()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E))
                .padding(horizontal = 4.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- File ----
            Box {
                Box(
                    modifier = Modifier
                        .clickable { openMenu = if (openMenu == "file") null else "file" }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) { Text("File", fontSize = 13.sp, color = if (openMenu == "file") Color(0xFF54A0FF) else Color(0xFFD4D4D4)) }
                DropdownMenu(
                    expanded = openMenu == "file",
                    onDismissRequest = { openMenu = null },
                    modifier = Modifier.background(Color(0xFF2D2D2D))
                ) {
                    MenuItemRow("Importera bild") { imageLauncher.launch("image/*"); openMenu = null }
                    Divider(color = Color(0xFF444444), thickness = 0.5.dp)
                    MenuItemRow("Spara PNG") {
                        exportBitmap = compositeWithObjects() ?: cachedBitmap
                        showExportDialog = true; openMenu = null
                    }
                    MenuItemRow("Spara JPG") {
                        exportBitmap = compositeWithObjects() ?: cachedBitmap
                        if (exportBitmap != null) { saveToGalleryJpg(context, exportBitmap) }; openMenu = null
                    }
                    Divider(color = Color(0xFF444444), thickness = 0.5.dp)
                    MenuItemRow("Spara projekt") { projectName = ""; showSaveProjectDialog = true; openMenu = null }
                    MenuItemRow("Öppna projekt") { savedProjects = listProjects(); showLoadProjectDialog = true; openMenu = null }
                    Divider(color = Color(0xFF444444), thickness = 0.5.dp)
                    MenuItemRow("Rensa canvas", danger = true) { showClearDialog = true; openMenu = null }
                }
            }

            // ---- Edit ----
            Box {
                Box(
                    modifier = Modifier
                        .clickable { openMenu = if (openMenu == "edit") null else "edit" }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) { Text("Edit", fontSize = 13.sp, color = if (openMenu == "edit") Color(0xFF54A0FF) else Color(0xFFD4D4D4)) }
                DropdownMenu(
                    expanded = openMenu == "edit",
                    onDismissRequest = { openMenu = null },
                    modifier = Modifier.background(Color(0xFF2D2D2D))
                ) {
                    MenuItemRow("Ångra  (Undo)") {
                        if (selState == SelState.CAPTURED) { cancelSelection() }
                        else {
                            val last = globalHistory.removeLastOrNull()
                            if (last != null) {
                                val (layerId, action) = last
                                val idx = layers.indexOfFirst { it.id == layerId }
                                if (idx >= 0) { layers[idx] = layers[idx].copy(actions = layers[idx].actions.dropLast(1)); rerenderLayer(idx); compositeAllLayers() }
                                redoStack.add(layerId to action)
                            }
                        }
                        openMenu = null
                    }
                    MenuItemRow("Gör om (Redo)") {
                        val redoItem = redoStack.removeLastOrNull()
                        if (redoItem != null) {
                            val (layerId, action) = redoItem
                            val idx = layers.indexOfFirst { it.id == layerId }
                            if (idx >= 0) {
                                ensureLayerBmp(idx)
                                val bmp = layers[idx].bitmap
                                if (bmp != null) { val c = android.graphics.Canvas(bmp); replayAction(action, bmp, c); layers[idx] = layers[idx].copy(actions = layers[idx].actions + action); compositeAllLayers() }
                            }
                            globalHistory.add(layerId to action)
                        }
                        openMenu = null
                    }
                }
            }

            // ---- Image ----
            Box {
                Box(
                    modifier = Modifier
                        .clickable { openMenu = if (openMenu == "image") null else "image" }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) { Text("Image", fontSize = 13.sp, color = if (openMenu == "image") Color(0xFF54A0FF) else Color(0xFFD4D4D4)) }
                DropdownMenu(
                    expanded = openMenu == "image",
                    onDismissRequest = { openMenu = null },
                    modifier = Modifier.background(Color(0xFF2D2D2D))
                ) {
                    MenuItemRow("Filter / Justera") { showFilterDialog = true; openMenu = null }
                    MenuItemRow("Justeringar") { showAdjustBar = true; openMenu = null }
                    MenuItemRow("Crop / Rotera / Skeva") { showCropDialog = true; openMenu = null }
                    MenuItemRow("Bakgrundsfärg...") { editingCanvasBg = true; openMenu = null }
                    MenuItemRow(if (showGrid) "Dölj rutnät" else "Visa rutnät") {
                        showGrid = !showGrid
                        if (showGrid) showGridPanel = true
                        renderTick++; openMenu = null
                    }
                    MenuItemRow(if (zoomScale != 1f) "Återställ zoom" else "Zoom 1:1") {
                        zoomScale = 1f; zoomOffX = 0f; zoomOffY = 0f; renderTick++; openMenu = null
                    }
                }
            }

            // ---- Layer ----
            Box {
                Box(
                    modifier = Modifier
                        .clickable { openMenu = if (openMenu == "layer") null else "layer" }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) { Text("Layer", fontSize = 13.sp, color = if (openMenu == "layer") Color(0xFF54A0FF) else Color(0xFFD4D4D4)) }
                DropdownMenu(
                    expanded = openMenu == "layer",
                    onDismissRequest = { openMenu = null },
                    modifier = Modifier.background(Color(0xFF2D2D2D))
                ) {
                    MenuItemRow(if (showLayers) "Dölj lagerpanel" else "Visa lagerpanel") {
                        showLayers = !showLayers; openMenu = null
                    }
                }
            }
        }
        } // AnimatedVisibility topBar

        // PS-liknande options bar (under menyrad, alltid synlig)
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF2C2C2C))
                .padding(horizontal = 10.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (drawMode) {
                DrawMode.DRAW -> {
                    OSizeSlider("Storlek", brushSize, 2f..80f, Color(0xFF5F27CD)) { brushSize = it }
                    OVertDivider()
                    OOpacitySlider(opacity, currentColor) { opacity = it }
                    OVertDivider()
                    OToggle("Symmetri", symmetryH) { symmetryH = it }
                    OVertDivider()
                    listOf(TextureMode.NONE to "Ingen", TextureMode.CANVAS to "Canvas", TextureMode.PAPER to "Papper", TextureMode.KRAFT to "Kraft").forEach { (tm, lbl) ->
                        OChip(lbl, textureMode == tm) { textureMode = tm; renderTick++ }
                    }
                }
                DrawMode.ERASE -> {
                    OSizeSlider("Storlek", brushSize, 2f..80f, Color(0xFF5F27CD)) { brushSize = it }
                }
                DrawMode.SPRAY -> {
                    OSizeSlider("Spridning", brushSize, 10f..80f, Color(0xFF5F27CD)) { brushSize = it }
                    OVertDivider()
                    OOpacitySlider(opacity, currentColor) { opacity = it }
                }
                DrawMode.SHAPE -> {
                    OSizeSlider("Tjocklek", brushSize, 2f..40f, Color(0xFF5F27CD)) { brushSize = it }
                    OVertDivider()
                    OToggle("Ifylld", shapeFilled) { shapeFilled = it }
                }
                DrawMode.FILL -> {
                    Text("Tryck på ytan för att fylla", fontSize = 12.sp, color = Color(0xFF888888))
                }
                DrawMode.TEXT -> {
                    val selTxt = selectedTextId?.let { id -> textObjects.firstOrNull { it.id == id } }
                    if (selTxt != null) {
                        Text("\"${selTxt.text.take(12)}${if(selTxt.text.length>12)"…" else ""}\"", fontSize = 12.sp, color = Color.White)
                        OVertDivider()
                        OSizeSlider("Storlek", selTxt.fontSize, 16f..200f, Color(0xFF5F27CD)) { ns ->
                            val idx = textObjects.indexOfFirst { it.id == selTxt.id }
                            if (idx >= 0) textObjects[idx] = textObjects[idx].copy(fontSize = ns)
                        }
                        OVertDivider()
                        OActionBtn("Baka in", Color(0xFF0BE881)) {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) {
                                ensureLayerBmp(idx)
                                layers[idx].bitmap?.let { bmp ->
                                    val c = android.graphics.Canvas(bmp)
                                    val p = AndroidPaint().apply {
                                        color = selTxt.color.toArgb(); textSize = selTxt.fontSize; isAntiAlias = true
                                        typeface = when (selTxt.style) {
                                            TextStyleOption.BOLD -> android.graphics.Typeface.DEFAULT_BOLD
                                            TextStyleOption.ITALIC -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                                            TextStyleOption.MONO -> android.graphics.Typeface.MONOSPACE
                                            TextStyleOption.NORMAL -> android.graphics.Typeface.DEFAULT
                                        }
                                    }
                                    c.drawText(selTxt.text, selTxt.x, selTxt.y, p)
                                }
                                textObjects.removeAll { it.id == selTxt.id }; selectedTextId = null; compositeAllLayers()
                            }
                        }
                        OActionBtn("Ta bort", Color(0xFFFF4757)) { textObjects.removeAll { it.id == selTxt.id }; selectedTextId = null }
                    } else {
                        OSizeSlider("Storlek", textFontSize, 16f..200f, Color(0xFF5F27CD)) { textFontSize = it }
                        OVertDivider()
                        listOf(TextStyleOption.NORMAL to "Normal", TextStyleOption.BOLD to "Fet", TextStyleOption.ITALIC to "Kursiv", TextStyleOption.MONO to "Mono").forEach { (ts, lbl) ->
                            OChip(lbl, textStyle == ts) { textStyle = ts }
                        }
                        if (textObjects.isNotEmpty()) {
                            OVertDivider()
                            OActionBtn("Baka in alla", Color(0xFF0BE881)) {
                                val idx = layers.indexOfFirst { it.id == activeLayerId }
                                if (idx >= 0) {
                                    ensureLayerBmp(idx)
                                    layers[idx].bitmap?.let { bmp ->
                                        val c = android.graphics.Canvas(bmp)
                                        textObjects.forEach { t ->
                                            val p = AndroidPaint().apply {
                                                color = t.color.toArgb(); textSize = t.fontSize; isAntiAlias = true
                                                typeface = when (t.style) {
                                                    TextStyleOption.BOLD -> android.graphics.Typeface.DEFAULT_BOLD
                                                    TextStyleOption.ITALIC -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                                                    TextStyleOption.MONO -> android.graphics.Typeface.MONOSPACE
                                                    TextStyleOption.NORMAL -> android.graphics.Typeface.DEFAULT
                                                }
                                            }
                                            c.drawText(t.text, t.x, t.y, p)
                                        }
                                    }
                                    textObjects.clear(); selectedTextId = null; compositeAllLayers()
                                }
                            }
                        }
                    }
                }
                DrawMode.STAMP -> {
                    val selObj = selectedObjId?.let { id -> emojiObjects.firstOrNull { it.id == id } }
                    if (selObj != null) {
                        Text(selObj.emoji, fontSize = 20.sp)
                        OSizeSlider("Storlek", selObj.size, 20f..400f, Color(0xFF5F27CD)) { ns ->
                            val idx = emojiObjects.indexOfFirst { it.id == selObj.id }
                            if (idx >= 0) { emojiObjects[idx] = emojiObjects[idx].copy(size = ns); renderTick++ }
                        }
                        OActionBtn("Dupliera") { val copy = selObj.copy(id = nextObjId++, x = selObj.x+55f, y = selObj.y+55f); emojiObjects.add(copy); selectedObjId = copy.id; renderTick++ }
                        OActionBtn("Baka in", Color(0xFF0BE881)) {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) { ensureLayerBmp(idx); layers[idx].bitmap?.let { bmp -> android.graphics.Canvas(bmp).drawText(selObj.emoji, selObj.x, selObj.y + selObj.size/3f, AndroidPaint().apply { textSize = selObj.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true }) }; emojiObjects.removeAll { it.id == selObj.id }; selectedObjId = null; compositeAllLayers() }
                        }
                        OActionBtn("Ta bort", Color(0xFFFF4757)) { emojiObjects.removeAll { it.id == selObj.id }; selectedObjId = null; renderTick++ }
                    } else {
                        OActionBtn(if (showStampPicker) "Dölj stämplar" else "Välj stämpel") { showStampPicker = !showStampPicker }
                        if (emojiObjects.isNotEmpty()) OActionBtn("Baka in alla", Color(0xFF0BE881)) {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) { ensureLayerBmp(idx); layers[idx].bitmap?.let { bmp -> val c = android.graphics.Canvas(bmp); emojiObjects.forEach { obj -> c.drawText(obj.emoji, obj.x, obj.y+obj.size/3f, AndroidPaint().apply { textSize = obj.size; textAlign = AndroidPaint.Align.CENTER; isAntiAlias = true }) } }; emojiObjects.clear(); selectedObjId = null; compositeAllLayers() }
                        }
                    }
                }
                DrawMode.EYEDROPPER -> {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(currentColor).border(1.5.dp, Color.White, CircleShape))
                    Text("Tryck på canvas för att sampla färg", fontSize = 12.sp, color = Color(0xFF888888))
                }
                DrawMode.SELECTION -> {
                    when (selState) {
                        SelState.NONE     -> Text("Rita markering på canvas", fontSize = 12.sp, color = Color(0xFF888888))
                        SelState.DRAWING  -> Text("Lyft fingret för att fånga", fontSize = 12.sp, color = Color(0xFF888888))
                        SelState.CAPTURED -> {
                            Text("Dra för att flytta", fontSize = 12.sp, color = Color(0xFF888888))
                            OActionBtn("Bekräfta", Color(0xFF0BE881)) { commitSelection() }
                            OActionBtn("Avbryt") { cancelSelection() }
                        }
                    }
                }
                DrawMode.DODGE -> {
                    OSizeSlider("Storlek", brushSize, 4f..80f, Color(0xFFFFDD59)) { brushSize = it }
                    OVertDivider()
                    Text("Ljusgör penseldrag", fontSize = 11.sp, color = Color(0xFF666666))
                }
                DrawMode.BURN -> {
                    OSizeSlider("Storlek", brushSize, 4f..80f, Color(0xFFFF6B6B)) { brushSize = it }
                    OVertDivider()
                    Text("Mörkgör penseldrag", fontSize = 11.sp, color = Color(0xFF666666))
                }
                DrawMode.SMUDGE -> {
                    OSizeSlider("Storlek", brushSize, 4f..60f, Color(0xFF48DBFB)) { brushSize = it }
                    OVertDivider()
                    Text("Blanda och sudda pixlar", fontSize = 11.sp, color = Color(0xFF666666))
                }
                DrawMode.PARTICLE -> {
                    OSizeSlider("Spridning", brushSize, 8f..100f, Color(0xFFFFDD59)) { brushSize = it }
                    OVertDivider()
                    OToggle("Regnbåge", brushType == BrushType.RAINBOW) { brushType = if (it) BrushType.RAINBOW else BrushType.NORMAL }
                }
                DrawMode.HAND -> {
                    Text("Zoom: ${(zoomScale * 100).toInt()}%", fontSize = 12.sp, color = Color.White)
                    OVertDivider()
                    OActionBtn("Återställ") { zoomScale = 1f; zoomOffX = 0f; zoomOffY = 0f; renderTick++ }
                    OActionBtn("Centrera") {
                        zoomOffX = 0f; zoomOffY = 0f; renderTick++
                    }
                }
                DrawMode.ANIMATION -> {
                    Text("Frame ${animFrame+1}/${animFrames.size.coerceAtLeast(1)}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    OActionBtn("◀") { if (animFrames.isNotEmpty()) { animFrame = (animFrame-1+animFrames.size)%animFrames.size; renderTick++ } }
                    OActionBtn("▶") { if (animFrames.isNotEmpty()) { animFrame = (animFrame+1)%animFrames.size; renderTick++ } }
                    OVertDivider()
                    OActionBtn(if (animPlaying) "⏸ Pausa" else "▶ Spela", if (animPlaying) Color(0xFFFF9F43) else Color(0xFF0BE881)) { animPlaying = !animPlaying }
                    OActionBtn("+ Frame") { animFrames.add(android.graphics.Bitmap.createBitmap(canvasWidth.coerceAtLeast(1), canvasHeight.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)); animFrame = animFrames.size-1; renderTick++ }
                    OVertDivider()
                    OToggle("Onion", showOnion) { showOnion = it }
                    OSizeSlider("FPS", animFps.toFloat(), 4f..24f, Color(0xFF5F27CD)) { animFps = it.toInt() }
                }
            }
        }

        // Mitten: Verktygspanel + Canvas + Lagerpanel
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

            // Vänster verktygspanel (Photoshop-stil)
            AnimatedVisibility(visible = sidebarVisible,
                enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
            Column(
                modifier = Modifier.width(72.dp).fillMaxHeight().background(Color(0xFF323232))
            ) {
                // Ångra / Gör om — snabbknappar
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E))
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF3A3A3A))
                            .clickable {
                                if (selState == SelState.CAPTURED) { cancelSelection() }
                                else {
                                    val last = globalHistory.removeLastOrNull()
                                    if (last != null) {
                                        val (lid, act) = last
                                        val idx = layers.indexOfFirst { it.id == lid }
                                        if (idx >= 0) { layers[idx] = layers[idx].copy(actions = layers[idx].actions.dropLast(1)); rerenderLayer(idx); compositeAllLayers() }
                                        redoStack.add(lid to act)
                                    }
                                }
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("↩", fontSize = 16.sp, color = Color(0xFFCCCCCC)) }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF3A3A3A))
                            .clickable {
                                val redoItem = redoStack.removeLastOrNull()
                                if (redoItem != null) {
                                    val (lid, act) = redoItem
                                    val idx = layers.indexOfFirst { it.id == lid }
                                    if (idx >= 0) {
                                        ensureLayerBmp(idx)
                                        val bmp = layers[idx].bitmap
                                        if (bmp != null) { val c = android.graphics.Canvas(bmp); replayAction(act, bmp, c); layers[idx] = layers[idx].copy(actions = layers[idx].actions + act); compositeAllLayers() }
                                    }
                                    globalHistory.add(lid to act)
                                }
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("↪", fontSize = 16.sp, color = Color(0xFFCCCCCC)) }
                }

                // Verktygsrutnät (2 kolumner)
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 3.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    toolList.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            pair.forEach { tool ->
                                val active = drawMode == tool.mode
                                val hasSubTools = tool.mode == DrawMode.DRAW || tool.mode == DrawMode.SHAPE
                                // Ikon som visas — pensel visar valt brushType, form visar valt shapeType
                                val displayIcon = when (tool.mode) {
                                    DrawMode.DRAW  -> brushSubTools.find { it.type == brushType }?.icon ?: tool.icon
                                    DrawMode.SHAPE -> shapeSubTools.find { it.type == selectedShape }?.icon ?: tool.icon
                                    else -> tool.icon
                                }
                                val displayLabel = when (tool.mode) {
                                    DrawMode.DRAW  -> brushSubTools.find { it.type == brushType }?.label ?: tool.label
                                    DrawMode.SHAPE -> shapeSubTools.find { it.type == selectedShape }?.label ?: tool.label
                                    else -> tool.label
                                }
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (active) Color(0xFF1473E6) else Color(0xFF4A4A4A))
                                            .border(
                                                width = if (active) 1.5.dp else 0.5.dp,
                                                color = if (active) Color(0xFF75B9FF) else Color(0xFF2A2A2A),
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if (selState == SelState.CAPTURED) commitSelection()
                                                    if (tool.mode != DrawMode.SHAPE) selectedShapeId = null
                                                    drawMode = tool.mode
                                                    toolFlyout = null
                                                },
                                                onLongClick = { toolFlyout = tool.mode }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                displayIcon,
                                                fontSize = 17.sp,
                                                color = if (active) Color.White else Color(0xFFBBBBBB)
                                            )
                                            Text(
                                                displayLabel,
                                                fontSize = 6.sp,
                                                color = if (active) Color(0xFFCCEEFF) else Color(0xFF888888),
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                        // PS-liknande solid triangel nere till höger = har sub-verktyg
                                        if (hasSubTools) {
                                            val triColor = if (active) Color.White else Color(0xFFAAAAAA)
                                            Canvas(modifier = Modifier.align(Alignment.BottomEnd).size(10.dp)) {
                                                val path = androidx.compose.ui.graphics.Path().apply {
                                                    moveTo(size.width, 0f)
                                                    lineTo(size.width, size.height)
                                                    lineTo(0f, size.height)
                                                    close()
                                                }
                                                drawPath(path, triColor)
                                            }
                                        }
                                    }
                                    // Sub-verktyg flyout (bara för DRAW och SHAPE)
                                    if (hasSubTools) {
                                        DropdownMenu(
                                            expanded = toolFlyout == tool.mode,
                                            onDismissRequest = { toolFlyout = null },
                                            modifier = Modifier.background(Color(0xFF1E1E1E)).width(160.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().background(Color(0xFF2D2D2D))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(tool.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF31A8FF))
                                            }
                                            HorizontalDivider(color = Color(0xFF444444), thickness = 0.5.dp)
                                            when (tool.mode) {
                                                DrawMode.DRAW -> brushSubTools.forEach { sub ->
                                                    val selBrush = brushType == sub.type
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                                Text(sub.icon, fontSize = 16.sp, color = if (selBrush) Color(0xFF31A8FF) else Color(0xFFCCCCCC))
                                                                Text(sub.label, fontSize = 13.sp, color = if (selBrush) Color(0xFF31A8FF) else Color(0xFFCCCCCC),
                                                                    fontWeight = if (selBrush) FontWeight.Bold else FontWeight.Normal)
                                                            }
                                                        },
                                                        onClick = { brushType = sub.type; drawMode = DrawMode.DRAW; toolFlyout = null },
                                                        modifier = Modifier.background(if (selBrush) Color(0xFF253040) else Color(0xFF1E1E1E))
                                                    )
                                                }
                                                DrawMode.SHAPE -> shapeSubTools.forEach { sub ->
                                                    val selShape = selectedShape == sub.type
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                                Text(sub.icon, fontSize = 16.sp, color = if (selShape) Color(0xFF31A8FF) else Color(0xFFCCCCCC))
                                                                Text(sub.label, fontSize = 13.sp, color = if (selShape) Color(0xFF31A8FF) else Color(0xFFCCCCCC),
                                                                    fontWeight = if (selShape) FontWeight.Bold else FontWeight.Normal)
                                                            }
                                                        },
                                                        onClick = { selectedShape = sub.type; drawMode = DrawMode.SHAPE; toolFlyout = null },
                                                        modifier = Modifier.background(if (selShape) Color(0xFF253040) else Color(0xFF1E1E1E))
                                                    )
                                                }
                                                else -> {}
                                            }
                                        }
                                    } else {
                                        // Tooltip-popup för enkla verktyg
                                        if (toolFlyout == tool.mode) {
                                            LaunchedEffect(tool.mode) {
                                                delay(1500)
                                                if (toolFlyout == tool.mode) toolFlyout = null
                                            }
                                            Popup(
                                                alignment = Alignment.CenterEnd,
                                                offset = IntOffset(x = 12, y = 0)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .shadow(6.dp, RoundedCornerShape(5.dp))
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(Color(0xFF1E1E1E))
                                                        .border(0.5.dp, Color(0xFF555555), RoundedCornerShape(5.dp))
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(tool.label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }

                // FG/BG-färgpaletter (Photoshop-stil) — klickbara + swap-knapp
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(62.dp)) {
                        // Sekundärfärg (BG)
                        Box(
                            modifier = Modifier.size(34.dp).offset(x = 20.dp, y = 20.dp)
                                .border(1.dp, Color(0xFF666666))
                                .background(secondaryColor)
                                .clickable { editingBgColor = true; showColorDialog = true }
                        )
                        // Förgrundsfärg (FG)
                        Box(
                            modifier = Modifier.size(34.dp)
                                .border(1.5.dp, Color(0xFFCCCCCC))
                                .background(currentColor)
                                .clickable { editingBgColor = false; showColorDialog = true }
                        )
                        // Swap-pil (byt FG ↔ BG) — uppe till höger
                        Box(
                            modifier = Modifier.size(18.dp).align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(Color(0xFF4A4A4A))
                                .border(1.dp, Color(0xFF777777), CircleShape)
                                .clickable {
                                    val tmp = currentColor
                                    currentColor = secondaryColor
                                    secondaryColor = tmp
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⇄", fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            }
            } // AnimatedVisibility sidebar

            // Canvas — wrapper med mörk bakgrund (syns när man zoomar ut)
            // rememberUpdatedState garanterar att lambdan alltid läser aktuellt värde
            val blockResize by rememberUpdatedState(showCropDialog || showAdjustBar)
            val baseModifier = Modifier.fillMaxSize()
                .onSizeChanged { size ->
                    if (blockResize) return@onSizeChanged  // Låt inte crop/adjust-baren krympa bitmapen
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
                // Zoom & pan med två fingrar
                if (event.pointerCount >= 2) {
                    val dx = event.getX(0) - event.getX(1); val dy = event.getY(0) - event.getY(1)
                    val dist = sqrt(dx*dx + dy*dy)
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    when (event.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN -> { prevPinchDist = dist; prevPinchMidX = midX; prevPinchMidY = midY }
                        MotionEvent.ACTION_MOVE -> {
                            if (prevPinchDist > 0f) {
                                val scaleFactor = dist / prevPinchDist
                                val newScale = (zoomScale * scaleFactor).coerceIn(0.5f, 8f)
                                zoomOffX += (midX - prevPinchMidX)
                                zoomOffY += (midY - prevPinchMidY)
                                zoomOffX = midX - (midX - zoomOffX) * (newScale / zoomScale)
                                zoomOffY = midY - (midY - zoomOffY) * (newScale / zoomScale)
                                zoomScale = newScale
                                prevPinchDist = dist; prevPinchMidX = midX; prevPinchMidY = midY
                                renderTick++
                            }
                        }
                        MotionEvent.ACTION_POINTER_UP -> { prevPinchDist = 0f }
                    }
                    return@pointerInteropFilter true
                }
                // Koordinattransform för zoom
                fun ex(raw: Float) = (raw - zoomOffX) / zoomScale
                fun ey(raw: Float) = (raw - zoomOffY) / zoomScale

                // Spåra fingerposition för verktygsmarkör
                if (event.pointerCount == 1) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            cursorPos = Offset(ex(event.x), ey(event.y)); renderTick++
                        }
                        MotionEvent.ACTION_UP -> { cursorPos = null; renderTick++ }
                    }
                }

                // Crop touch-hantering (tar över all touch när crop är öppen, tab=0)
                if (showCropDialog && cropTab == 0) {
                    val W = canvasWidth.toFloat(); val H = canvasHeight.toFloat()
                    if (W > 0 && H > 0) {
                        val tx = ex(event.x); val ty2 = ey(event.y)
                        val hitR = 40f / zoomScale   // handtag-radie i canvas-koordinater
                        val lx = cropL * W; val top = cropT * H
                        val rx = cropR * W; val bot = cropB * H
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                cropDragHandle = when {
                                    // Hörn-handtag
                                    (Offset(tx,ty2)-Offset(lx,top)).getDistance() < hitR*1.5f -> "TL"
                                    (Offset(tx,ty2)-Offset(rx,top)).getDistance() < hitR*1.5f -> "TR"
                                    (Offset(tx,ty2)-Offset(rx,bot)).getDistance() < hitR*1.5f -> "BR"
                                    (Offset(tx,ty2)-Offset(lx,bot)).getDistance() < hitR*1.5f -> "BL"
                                    // Kant-handtag
                                    abs(tx - lx) < hitR && ty2 in top..bot -> "L"
                                    abs(tx - rx) < hitR && ty2 in top..bot -> "R"
                                    abs(ty2 - top) < hitR && tx in lx..rx -> "T"
                                    abs(ty2 - bot) < hitR && tx in lx..rx -> "B"
                                    else -> null
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val nx = (tx / W).coerceIn(0f, 1f)
                                val ny = (ty2 / H).coerceIn(0f, 1f)
                                when (cropDragHandle) {
                                    "L"  -> if (nx < cropR - 0.02f) cropL = nx
                                    "R"  -> if (nx > cropL + 0.02f) cropR = nx
                                    "T"  -> if (ny < cropB - 0.02f) cropT = ny
                                    "B"  -> if (ny > cropT + 0.02f) cropB = ny
                                    "TL" -> { if (nx < cropR - 0.02f) cropL = nx; if (ny < cropB - 0.02f) cropT = ny }
                                    "TR" -> { if (nx > cropL + 0.02f) cropR = nx; if (ny < cropB - 0.02f) cropT = ny }
                                    "BR" -> { if (nx > cropL + 0.02f) cropR = nx; if (ny > cropT + 0.02f) cropB = ny }
                                    "BL" -> { if (nx < cropR - 0.02f) cropL = nx; if (ny > cropT + 0.02f) cropB = ny }
                                }
                                // Om valt aspect ratio != Free — lås proportioner
                                if (selectedAspect != 0 && cropDragHandle != null) {
                                    val preset = aspectPresets[selectedAspect]
                                    val targetRatio = preset.w.toFloat() / preset.h.toFloat()
                                    val cW = (cropR - cropL) * W; val cH = (cropB - cropT) * H
                                    if (cH > 0) {
                                        val curRatio = cW / cH
                                        if (curRatio > targetRatio) {
                                            val newW = cH * targetRatio
                                            cropR = (cropL + newW / W).coerceAtMost(1f)
                                        } else {
                                            val newH = cW / targetRatio
                                            cropB = (cropT + newH / H).coerceAtMost(1f)
                                        }
                                    }
                                }
                                renderTick++
                            }
                            MotionEvent.ACTION_UP -> { cropDragHandle = null }
                        }
                        return@pointerInteropFilter true
                    }
                }

                when (drawMode) {

                    DrawMode.HAND -> {
                        // Flytta canvas med ett finger (pan)
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                prevPinchMidX = event.x; prevPinchMidY = event.y
                            }
                            MotionEvent.ACTION_MOVE -> {
                                zoomOffX += event.x - prevPinchMidX
                                zoomOffY += event.y - prevPinchMidY
                                prevPinchMidX = event.x; prevPinchMidY = event.y
                                renderTick++
                            }
                        }; true
                    }

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
                                if (curSelObj != null) {
                                    val half = curSelObj.size / 2f
                                    val hx = curSelObj.x + half; val hy = curSelObj.y + half
                                    val ex0 = ex(event.x); val ey0 = ey(event.y)
                                    val dist = sqrt((ex0 - hx) * (ex0 - hx) + (ey0 - hy) * (ey0 - hy))
                                    if (dist < HANDLE_R) {
                                        objDragMode = ObjDragMode.RESIZE
                                        objDragPrev = Offset(ex0, ey0)
                                        renderTick++; return@pointerInteropFilter true
                                    }
                                }
                                val hit = emojiObjects.lastOrNull { objHalfHit(it, ex(event.x), ey(event.y)) }
                                if (hit != null) {
                                    selectedObjId = hit.id; objDragMode = ObjDragMode.MOVE
                                    objDragPrev = Offset(ex(event.x), ey(event.y))
                                } else { selectedObjId = null; objDragMode = null; objDragPrev = null }
                                renderTick++
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val prev = objDragPrev
                                val selIdx = selectedObjId?.let { id -> emojiObjects.indexOfFirst { it.id == id } } ?: -1
                                if (prev != null && selIdx >= 0) {
                                    val obj = emojiObjects[selIdx]
                                    when (objDragMode) {
                                        ObjDragMode.MOVE -> {
                                            var nx = obj.x + (ex(event.x) - prev.x)
                                            var ny = obj.y + (ey(event.y) - prev.y)
                                            val temp = obj.copy(x = nx, y = ny)
                                            computeGuides(temp)
                                            if (guideX != null && abs(nx - guideX!!) < SNAP) nx = guideX!!
                                            if (guideY != null && abs(ny - guideY!!) < SNAP) ny = guideY!!
                                            emojiObjects[selIdx] = obj.copy(x = nx, y = ny)
                                        }
                                        ObjDragMode.RESIZE -> {
                                            val delta = (ex(event.x) - prev.x + ey(event.y) - prev.y) / 2f
                                            emojiObjects[selIdx] = obj.copy(size = (obj.size + delta * 1.4f).coerceIn(20f, 500f))
                                        }
                                        null -> {}
                                    }
                                    objDragPrev = Offset(ex(event.x), ey(event.y))
                                }
                                renderTick++
                            }
                            MotionEvent.ACTION_UP -> {
                                if (objDragMode == null && selectedObjId == null) {
                                    val newObj = EmojiObject(nextObjId++, selectedStamp, ex(event.x), ey(event.y))
                                    emojiObjects.add(newObj); selectedObjId = newObj.id
                                }
                                objDragMode = null; guideX = null; guideY = null; renderTick++
                            }
                        }; true
                    }

                    DrawMode.FILL -> {
                        if (event.action == MotionEvent.ACTION_UP) {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) {
                                ensureLayerBmp(idx)
                                val bmp = layers[idx].bitmap ?: return@pointerInteropFilter true
                                val cx = ex(event.x).toInt().coerceIn(0, bmp.width-1)
                                val cy = ey(event.y).toInt().coerceIn(0, bmp.height-1)
                                addActionToActiveLayer(DrawAction.FillAction(cx, cy, currentColor)) { b, _ -> floodFill(b, cx, cy, currentColor.toArgb()) }
                            }
                        }; true
                    }

                    DrawMode.TEXT -> {
                        val tp2 = Offset(ex(event.x), ey(event.y))
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                val hitTxt = textObjects.lastOrNull { t ->
                                    val hw = t.fontSize * t.text.length * 0.3f; val hh = t.fontSize
                                    tp2.x in (t.x-hw)..(t.x+hw) && tp2.y in (t.y-hh)..(t.y+hh*0.3f)
                                }
                                if (hitTxt != null) {
                                    selectedTextId = hitTxt.id
                                    textDragOrigin = tp2; textOrigPos = Offset(hitTxt.x, hitTxt.y)
                                } else {
                                    selectedTextId = null
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val tIdx = textObjects.indexOfFirst { it.id == selectedTextId }
                                if (tIdx >= 0) {
                                    val dx = tp2.x - textDragOrigin.x; val dy = tp2.y - textDragOrigin.y
                                    textObjects[tIdx] = textObjects[tIdx].copy(x = textOrigPos.x+dx, y = textOrigPos.y+dy)
                                    renderTick++
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                if (selectedTextId == null) { pendingTextPos = tp2; showTextDialog = true }
                            }
                        }; true
                    }

                    DrawMode.EYEDROPPER -> {
                        if (event.action == MotionEvent.ACTION_UP) {
                            val bmp = cachedBitmap
                            if (bmp != null) {
                                val px = ex(event.x).toInt().coerceIn(0, bmp.width-1)
                                val py = ey(event.y).toInt().coerceIn(0, bmp.height-1)
                                currentColor = Color(bmp.getPixel(px, py)); drawMode = DrawMode.DRAW
                            }
                        }; true
                    }

                    DrawMode.SELECTION -> {
                        val tp = Offset(ex(event.x), ey(event.y))
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                // Kolla vektor-former
                                val hitShape = shapeObjects.lastOrNull { shapeObjHit(it, tp) }
                                // Kolla emoji-stämplar
                                val hitEmoji = emojiObjects.lastOrNull { o ->
                                    val hs = o.size / 2f; tp.x in (o.x-hs)..(o.x+hs) && tp.y in (o.y-hs-o.size/2f)..(o.y+hs)
                                }
                                // Kolla text-objekt
                                val hitText = textObjects.lastOrNull { t ->
                                    val hw = t.fontSize * t.text.length * 0.3f; val hh = t.fontSize
                                    tp.x in (t.x-hw)..(t.x+hw) && tp.y in (t.y-hh)..(t.y+hh*0.3f)
                                }
                                when {
                                    hitShape != null -> {
                                        selObjType = "shape"; selectedShapeId = hitShape.id
                                        selectedObjId = null; selectedTextId = null
                                        selState = SelState.NONE
                                        shapeResizeCorner = null
                                        shapeDragOrigin = tp; shapeOrigStart = hitShape.start; shapeOrigEnd = hitShape.end
                                    }
                                    hitEmoji != null -> {
                                        selObjType = "emoji"; selectedObjId = hitEmoji.id
                                        selectedShapeId = null; selectedTextId = null
                                        selState = SelState.NONE
                                        objDragMode = ObjDragMode.MOVE; objDragPrev = tp
                                    }
                                    hitText != null -> {
                                        selObjType = "text"; selectedTextId = hitText.id
                                        selectedShapeId = null; selectedObjId = null
                                        selState = SelState.NONE
                                        textDragOrigin = tp; textOrigPos = Offset(hitText.x, hitText.y)
                                    }
                                    selState == SelState.CAPTURED -> {
                                        selObjType = null
                                        selDragPrev = tp
                                    }
                                    else -> {
                                        selObjType = null
                                        selectedShapeId = null; selectedObjId = null; selectedTextId = null
                                        selRectStart = tp; selRectEnd = tp; selState = SelState.DRAWING
                                    }
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                when (selObjType) {
                                    "shape" -> {
                                        val sIdx = shapeObjects.indexOfFirst { it.id == selectedShapeId }
                                        if (sIdx >= 0) {
                                            val dx = tp.x - shapeDragOrigin.x; val dy = tp.y - shapeDragOrigin.y
                                            shapeObjects[sIdx] = shapeObjects[sIdx].copy(
                                                start = Offset(shapeOrigStart.x+dx, shapeOrigStart.y+dy),
                                                end   = Offset(shapeOrigEnd.x+dx,   shapeOrigEnd.y+dy)
                                            )
                                        }
                                    }
                                    "emoji" -> {
                                        val eIdx = emojiObjects.indexOfFirst { it.id == selectedObjId }
                                        if (eIdx >= 0 && objDragPrev != null) {
                                            val dx = tp.x - objDragPrev!!.x; val dy = tp.y - objDragPrev!!.y
                                            emojiObjects[eIdx] = emojiObjects[eIdx].copy(x = emojiObjects[eIdx].x+dx, y = emojiObjects[eIdx].y+dy)
                                            objDragPrev = tp
                                        }
                                    }
                                    "text" -> {
                                        val tIdx = textObjects.indexOfFirst { it.id == selectedTextId }
                                        if (tIdx >= 0) {
                                            val dx = tp.x - textDragOrigin.x; val dy = tp.y - textDragOrigin.y
                                            textObjects[tIdx] = textObjects[tIdx].copy(x = textOrigPos.x+dx, y = textOrigPos.y+dy)
                                        }
                                    }
                                    else -> {
                                        if (selState == SelState.DRAWING) {
                                            selRectEnd = tp
                                        } else if (selState == SelState.CAPTURED) {
                                            val prev = selDragPrev
                                            if (prev != null) selOffset += Offset(tp.x - prev.x, tp.y - prev.y)
                                            selDragPrev = tp
                                        }
                                    }
                                }
                                renderTick++
                            }
                            MotionEvent.ACTION_UP -> {
                                when {
                                    selObjType != null -> { selObjType = null }
                                    selState == SelState.DRAWING -> doCapture()
                                    else -> selDragPrev = null
                                }
                            }
                        }; true
                    }

                    DrawMode.SHAPE -> {
                        val touchPt = Offset(ex(event.x), ey(event.y))
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                val selObj = selectedShapeId?.let { id -> shapeObjects.find { it.id == id } }
                                val corner = selObj?.let { shapeCornerHit(it, touchPt) }
                                when {
                                    corner != null -> {
                                        // Resize via hörn-handtag
                                        shapeResizeCorner = corner
                                        shapeDragOrigin = touchPt
                                        shapeOrigStart = selObj.start
                                        shapeOrigEnd = selObj.end
                                    }
                                    selObj != null && shapeObjHit(selObj, touchPt) -> {
                                        // Flytta vald form
                                        shapeResizeCorner = null
                                        shapeDragOrigin = touchPt
                                        shapeOrigStart = selObj.start
                                        shapeOrigEnd = selObj.end
                                    }
                                    else -> {
                                        // Välj annan form eller börja rita ny
                                        val hit = shapeObjects.lastOrNull { shapeObjHit(it, touchPt) }
                                        if (hit != null) {
                                            selectedShapeId = hit.id
                                            shapeResizeCorner = null
                                            shapeDragOrigin = touchPt
                                            shapeOrigStart = hit.start
                                            shapeOrigEnd = hit.end
                                        } else {
                                            selectedShapeId = null
                                            shapeResizeCorner = null
                                            shapeStart = touchPt; shapeEnd = touchPt
                                        }
                                    }
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val selId = selectedShapeId
                                val selIdx = if (selId != null) shapeObjects.indexOfFirst { it.id == selId } else -1
                                if (selIdx >= 0) {
                                    val dx = touchPt.x - shapeDragOrigin.x
                                    val dy = touchPt.y - shapeDragOrigin.y
                                    val corner = shapeResizeCorner
                                    shapeObjects[selIdx] = when (corner) {
                                        0 -> shapeObjects[selIdx].copy(start = Offset(shapeOrigStart.x+dx, shapeOrigStart.y+dy))
                                        1 -> shapeObjects[selIdx].copy(start = Offset(shapeOrigStart.x, shapeOrigStart.y+dy), end = Offset(shapeOrigEnd.x+dx, shapeOrigEnd.y))
                                        2 -> shapeObjects[selIdx].copy(end = Offset(shapeOrigEnd.x+dx, shapeOrigEnd.y+dy))
                                        3 -> shapeObjects[selIdx].copy(start = Offset(shapeOrigStart.x+dx, shapeOrigStart.y), end = Offset(shapeOrigEnd.x, shapeOrigEnd.y+dy))
                                        else -> shapeObjects[selIdx].copy(
                                            start = Offset(shapeOrigStart.x+dx, shapeOrigStart.y+dy),
                                            end   = Offset(shapeOrigEnd.x+dx,   shapeOrigEnd.y+dy)
                                        )
                                    }
                                    renderTick++
                                } else if (shapeStart != null) {
                                    shapeEnd = touchPt; renderTick++
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                if (selectedShapeId == null && shapeStart != null) {
                                    val s = shapeStart!!
                                    if ((touchPt - s).getDistance() > 8f) {
                                        val newObj = ShapeObject(nextShapeId++, selectedShape, s, touchPt, effectiveColor(), brushSize, shapeFilled)
                                        shapeObjects.add(newObj)
                                        selectedShapeId = newObj.id
                                    }
                                    shapeStart = null; shapeEnd = null
                                }
                                shapeResizeCorner = null
                                renderTick++
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
                            MotionEvent.ACTION_DOWN -> { liveSprayDots.clear(); sprayAt(ex(event.x), ey(event.y)); compositeAllLayers() }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) sprayAt(ex(event.getHistoricalX(i)), ey(event.getHistoricalY(i)))
                                sprayAt(ex(event.x), ey(event.y)); compositeAllLayers()
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
                                val px = ex(event.x); val py = ey(event.y)
                                val numParticles = 28
                                val burst = (0 until numParticles).map {
                                    val angle = it * (2f * PI.toFloat() / numParticles) + Random.nextFloat() * 0.4f
                                    val r = (0.3f + Random.nextFloat() * 0.7f) * brushSize * 2.5f
                                    Offset(px + cos(angle) * r, py + sin(angle) * r)
                                }
                                val innerBurst = (0 until numParticles / 2).map {
                                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                                    val r = Random.nextFloat() * brushSize * 0.8f
                                    Offset(px + cos(angle) * r, py + sin(angle) * r)
                                }
                                val allDots = burst + innerBurst
                                val col = if (brushType == BrushType.RAINBOW) rainbowColors.random() else effectiveColor()
                                allDots.forEachIndexed { i, dot ->
                                    val dotSize = if (i < numParticles) brushSize * (0.08f + Random.nextFloat() * 0.18f) else brushSize * 0.25f
                                    val paint = AndroidPaint().apply { color = col.copy(alpha = 0.5f + Random.nextFloat() * 0.5f).toArgb(); style = AndroidPaint.Style.FILL; isAntiAlias = true }
                                    c.drawCircle(dot.x, dot.y, dotSize, paint)
                                }
                                val session = SpraySession(allDots, col, brushSize * 0.12f)
                                layers[idx] = layers[idx].copy(actions = layers[idx].actions + DrawAction.SprayAction(session))
                                globalHistory.add(activeLayerId to DrawAction.SprayAction(session))
                                redoStack.clear(); compositeAllLayers()
                            }
                        }; true
                    }

                    DrawMode.DODGE, DrawMode.BURN -> {
                        val col = if (drawMode == DrawMode.DODGE) Color(1f, 1f, 1f, 0.14f) else Color(0f, 0f, 0f, 0.14f)
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> { livePoints.clear(); livePoints.add(Offset(ex(event.x), ey(event.y))); renderTick++ }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) livePoints.add(Offset(ex(event.getHistoricalX(i)), ey(event.getHistoricalY(i))))
                                livePoints.add(Offset(ex(event.x), ey(event.y))); renderTick++
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
                            MotionEvent.ACTION_DOWN -> { smudgeAt(ex(event.x), ey(event.y)); compositeAllLayers() }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) smudgeAt(ex(event.getHistoricalX(i)), ey(event.getHistoricalY(i)))
                                smudgeAt(ex(event.x), ey(event.y)); compositeAllLayers()
                            }
                            MotionEvent.ACTION_UP -> {}
                        }; true
                    }

                    DrawMode.DRAW, DrawMode.ERASE -> {
                        val isErase = drawMode == DrawMode.ERASE
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                livePoints.clear(); liveMirrorPoints.clear()
                                livePoints.add(Offset(ex(event.x), ey(event.y)))
                                if (symmetryH && !isErase) liveMirrorPoints.add(Offset(canvasWidth - ex(event.x), ey(event.y)))
                                renderTick++
                            }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) {
                                    livePoints.add(Offset(ex(event.getHistoricalX(i)), ey(event.getHistoricalY(i))))
                                    if (symmetryH && !isErase) liveMirrorPoints.add(Offset(canvasWidth - ex(event.getHistoricalX(i)), ey(event.getHistoricalY(i))))
                                }
                                livePoints.add(Offset(ex(event.x), ey(event.y)))
                                if (symmetryH && !isErase) liveMirrorPoints.add(Offset(canvasWidth - ex(event.x), ey(event.y)))
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
                                            drawPathOnCanvas(dp, c); drawPathOnCanvas(mirrorDp, c)
                                        }
                                    } else {
                                        addActionToActiveLayer(DrawAction.PathAction(dp)) { _, c -> drawPathOnCanvas(dp, c) }
                                    }
                                }
                                livePoints.clear(); liveMirrorPoints.clear(); renderTick++
                            }
                        }; true
                    }

                    DrawMode.ANIMATION -> {
                        if (animFrames.isEmpty() && canvasWidth > 0) {
                            animFrames.add(android.graphics.Bitmap.createBitmap(canvasWidth, canvasHeight, android.graphics.Bitmap.Config.ARGB_8888))
                        }
                        val frameIdx = animFrame.coerceIn(0, (animFrames.size - 1).coerceAtLeast(0))
                        if (animFrames.isEmpty()) return@pointerInteropFilter true
                        val bmp = animFrames[frameIdx]
                        val isErase = false
                        when (event.action and MotionEvent.ACTION_MASK) {
                            MotionEvent.ACTION_DOWN -> {
                                livePoints.clear()
                                livePoints.add(Offset(ex(event.x), ey(event.y))); renderTick++
                            }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) livePoints.add(Offset(ex(event.getHistoricalX(i)), ey(event.getHistoricalY(i))))
                                livePoints.add(Offset(ex(event.x), ey(event.y))); renderTick++
                            }
                            MotionEvent.ACTION_UP -> {
                                if (livePoints.size >= 2) {
                                    val dp = DrawnPath(buildSmoothPath(livePoints), effectiveColor(), brushSize, false, brushType)
                                    drawPathOnCanvas(dp, android.graphics.Canvas(bmp))
                                }
                                livePoints.clear(); renderTick++
                            }
                        }; true
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1A1A1A)).clip(androidx.compose.ui.graphics.RectangleShape)) {
            Canvas(modifier = gestureModifier.graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                scaleX = zoomScale; scaleY = zoomScale
                translationX = zoomOffX; translationY = zoomOffY
            }) {
                @Suppress("UNUSED_EXPRESSION") renderTick
                // Pappers-bakgrund (alltid, även utanför bitmap)
                drawRect(color = backgroundColor)
                // Animering
                if (drawMode == DrawMode.ANIMATION) {
                    drawRect(color = backgroundColor)
                    if (animFrames.isNotEmpty()) {
                        val fi = animFrame.coerceIn(0, animFrames.size - 1)
                        if (showOnion && fi > 0) {
                            drawImage(animFrames[fi - 1].asImageBitmap(), alpha = 0.3f)
                        }
                        drawImage(animFrames[fi].asImageBitmap())
                    }
                    if (livePoints.size >= 2) {
                        drawPath(buildSmoothPath(livePoints), effectiveColor(), style = Stroke(brushSize, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                } else if (showCropDialog && cropTab == 1) {
                    // Live preview: rotera med nativeCanvas
                    val nc = drawContext.canvas.nativeCanvas
                    nc.save()
                    nc.rotate(straightenDeg, size.width / 2f, size.height / 2f)
                    cachedBitmap?.let { bmp ->
                        nc.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, size.width, size.height), null)
                    }
                    nc.restore()
                } else if (showCropDialog && cropTab == 2) {
                    // Live preview: skeva med Matrix
                    val nc = drawContext.canvas.nativeCanvas
                    nc.save()
                    val m = android.graphics.Matrix()
                    m.setSkew(skewH, skewV, size.width / 2f, size.height / 2f)
                    nc.concat(m)
                    cachedBitmap?.let { bmp ->
                        nc.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, size.width, size.height), null)
                    }
                    nc.restore()
                } else if (showAdjustBar && adjPreviewComposite != null) {
                    drawImage(adjPreviewComposite!!.asImageBitmap())
                } else {
                    cachedBitmap?.let { drawImage(it.asImageBitmap()) } ?: drawRect(color = backgroundColor)
                }

                // Canvas-textur overlay
                if (textureMode != TextureMode.NONE) {
                    val txC = drawContext.canvas.nativeCanvas
                    when (textureMode) {
                        TextureMode.CANVAS -> {
                            // Vävd canvas — korsande linjer med varierande bredd
                            val linePaint = AndroidPaint().apply { style = AndroidPaint.Style.STROKE; isAntiAlias = false }
                            val spacing = 8f
                            linePaint.strokeWidth = 1f
                            // Horisontella trådar
                            linePaint.color = android.graphics.Color.argb(45, 80, 55, 30)
                            var ty = 0f; while (ty < size.height) { txC.drawLine(0f, ty, size.width, ty, linePaint); ty += spacing }
                            // Vertikala trådar
                            linePaint.color = android.graphics.Color.argb(35, 60, 40, 20)
                            var tx = 0f; while (tx < size.width) { txC.drawLine(tx, 0f, tx, size.height, linePaint); tx += spacing }
                            // Knutpunkter (mörkare i varje kors)
                            val dotPaint = AndroidPaint().apply { style = AndroidPaint.Style.FILL; color = android.graphics.Color.argb(30, 50, 30, 10) }
                            tx = 0f; while (tx < size.width) { ty = 0f; while (ty < size.height) { txC.drawCircle(tx, ty, 1.2f, dotPaint); ty += spacing }; tx += spacing }
                        }
                        TextureMode.PAPER -> {
                            // Papper — finkornigt brus i lager
                            val rng = Random(7)
                            val grainPaint = AndroidPaint().apply { style = AndroidPaint.Style.FILL; isAntiAlias = false }
                            // Mörka korn
                            grainPaint.color = android.graphics.Color.argb(40, 50, 45, 35)
                            repeat(6000) { txC.drawCircle(rng.nextFloat() * size.width, rng.nextFloat() * size.height, rng.nextFloat() * 1.5f + 0.3f, grainPaint) }
                            // Ljusa korn
                            grainPaint.color = android.graphics.Color.argb(25, 255, 250, 240)
                            repeat(2000) { txC.drawCircle(rng.nextFloat() * size.width, rng.nextFloat() * size.height, rng.nextFloat() * 1.2f + 0.2f, grainPaint) }
                        }
                        TextureMode.KRAFT -> {
                            // Kraft — grova horisontella fibrer + korn
                            val rng = Random(13)
                            val fiberPaint = AndroidPaint().apply { style = AndroidPaint.Style.STROKE; isAntiAlias = false }
                            // Långa fibrer
                            fiberPaint.strokeWidth = 1f
                            fiberPaint.color = android.graphics.Color.argb(50, 110, 70, 25)
                            repeat(400) {
                                val y = rng.nextFloat() * size.height
                                val x1 = rng.nextFloat() * size.width * 0.4f
                                val x2 = x1 + rng.nextFloat() * size.width * 0.8f
                                txC.drawLine(x1, y + rng.nextFloat() * 2f - 1f, x2, y + rng.nextFloat() * 2f - 1f, fiberPaint)
                            }
                            // Korta fibrer
                            fiberPaint.color = android.graphics.Color.argb(35, 90, 55, 15)
                            repeat(600) {
                                val x = rng.nextFloat() * size.width; val y = rng.nextFloat() * size.height
                                txC.drawLine(x, y, x + rng.nextFloat() * 12f - 6f, y + rng.nextFloat() * 3f - 1.5f, fiberPaint)
                            }
                            // Grovt korn ovanpå
                            val grainPaint = AndroidPaint().apply { style = AndroidPaint.Style.FILL; color = android.graphics.Color.argb(30, 120, 80, 30) }
                            repeat(3000) { txC.drawCircle(rng.nextFloat() * size.width, rng.nextFloat() * size.height, rng.nextFloat() * 1.8f + 0.3f, grainPaint) }
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
                        ShapeType.PENTAGON -> {
                            val cx=(s.x+e.x)/2f; val cy=(s.y+e.y)/2f
                            val r=min(abs(e.x-s.x),abs(e.y-s.y))/2f
                            val pentPath = Path().apply {
                                for (i in 0 until 5) {
                                    val a = Math.toRadians((i * 72.0 - 90.0))
                                    val px = cx + r * cos(a).toFloat(); val py = cy + r * sin(a).toFloat()
                                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                                }
                                close()
                            }
                            drawPath(pentPath, col, style = style)
                        }
                    }
                }

                // Vektor-former (eget lager ovanpå allt)
                fun drawShapeVec(pS: Offset, pE: Offset, type: ShapeType, col: Color, sw: Float, filled: Boolean) {
                    val pStyle: androidx.compose.ui.graphics.drawscope.DrawStyle =
                        if (filled) Fill else Stroke(sw, cap = StrokeCap.Round)
                    when (type) {
                        ShapeType.LINE     -> drawLine(col, pS, pE, sw, StrokeCap.Round)
                        ShapeType.RECT     -> drawRect(col, topLeft = Offset(min(pS.x,pE.x), min(pS.y,pE.y)),
                            size = Size(abs(pE.x-pS.x), abs(pE.y-pS.y)), style = pStyle)
                        ShapeType.CIRCLE   -> { val dx=pE.x-pS.x; val dy=pE.y-pS.y; drawCircle(col, sqrt(dx*dx+dy*dy), pS, style=pStyle) }
                        ShapeType.TRIANGLE -> {
                            val tP = Path().apply {
                                val left=min(pS.x,pE.x); val right=max(pS.x,pE.x); val top=min(pS.y,pE.y); val bottom=max(pS.y,pE.y)
                                moveTo((left+right)/2f,top); lineTo(right,bottom); lineTo(left,bottom); close()
                            }; drawPath(tP, col, style = pStyle)
                        }
                        ShapeType.STAR -> {
                            val dx=pE.x-pS.x; val dy=pE.y-pS.y; val outerR=sqrt(dx*dx+dy*dy); val innerR=outerR*0.382f
                            val sp = Path().apply {
                                for (i in 0 until 10) {
                                    val angle=Math.toRadians((i*36.0-90.0)); val r=if(i%2==0) outerR else innerR
                                    val x=pS.x+r*cos(angle).toFloat(); val y=pS.y+r*sin(angle).toFloat()
                                    if(i==0) moveTo(x,y) else lineTo(x,y)
                                }; close()
                            }; drawPath(sp, col, style = pStyle)
                        }
                        ShapeType.ARROW -> {
                            val ang=atan2(pE.y-pS.y,pE.x-pS.x); val hw=sw*2.5f; val hp=hw*2.5f
                            val bx=pE.x-cos(ang)*hp; val by=pE.y-sin(ang)*hp
                            val ap = Path().apply {
                                moveTo(pS.x,pS.y); lineTo(bx,by); moveTo(pE.x,pE.y)
                                lineTo(bx+cos(ang+PI.toFloat()/2f)*hw, by+sin(ang+PI.toFloat()/2f)*hw)
                                lineTo(pE.x,pE.y)
                                lineTo(bx+cos(ang-PI.toFloat()/2f)*hw, by+sin(ang-PI.toFloat()/2f)*hw)
                            }; drawPath(ap, col, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        ShapeType.HEXAGON -> {
                            val dx=pE.x-pS.x; val dy=pE.y-pS.y; val r=sqrt(dx*dx+dy*dy)
                            val hp = Path().apply {
                                for (i in 0 until 6) { val a=Math.toRadians(60.0*i-30.0)
                                    val x=pS.x+r*cos(a).toFloat(); val y=pS.y+r*sin(a).toFloat()
                                    if(i==0) moveTo(x,y) else lineTo(x,y) }; close()
                            }; drawPath(hp, col, style = pStyle)
                        }
                        ShapeType.PENTAGON -> {
                            val cx=(pS.x+pE.x)/2f; val cy=(pS.y+pE.y)/2f; val r=min(abs(pE.x-pS.x),abs(pE.y-pS.y))/2f
                            val pp = Path().apply {
                                for (i in 0 until 5) { val a=Math.toRadians((i*72.0-90.0))
                                    val px2=cx+r*cos(a).toFloat(); val py2=cy+r*sin(a).toFloat()
                                    if(i==0) moveTo(px2,py2) else lineTo(px2,py2) }; close()
                            }; drawPath(pp, col, style = pStyle)
                        }
                    }
                }
                shapeObjects.forEach { obj ->
                    drawShapeVec(obj.start, obj.end, obj.type, obj.color, obj.strokeWidth, obj.filled)
                    if (obj.id == selectedShapeId) {
                        // Blå streckad markerings-ram + hörn-handtag
                        val m = obj.strokeWidth + 8f
                        val l = min(obj.start.x, obj.end.x)-m; val t = min(obj.start.y, obj.end.y)-m
                        val r = max(obj.start.x, obj.end.x)+m; val b = max(obj.start.y, obj.end.y)+m
                        drawRect(Color(0xFF54A0FF), topLeft = Offset(l,t), size = Size(r-l,b-t),
                            style = Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f,5f))))
                        // Hörn-handtag
                        listOf(Offset(l,t), Offset(r,t), Offset(r,b), Offset(l,b)).forEach { corner ->
                            drawCircle(Color.White, 10f, corner)
                            drawCircle(Color(0xFF54A0FF), 10f, corner, style = Stroke(2f))
                        }
                    }
                }

                // Markeringsrektangel (ritas) — "marching ants" med kontrast
                if (drawMode == DrawMode.SELECTION && selState == SelState.DRAWING) {
                    val l = min(selRectStart.x, selRectEnd.x); val t = min(selRectStart.y, selRectEnd.y)
                    val w = abs(selRectEnd.x - selRectStart.x); val h = abs(selRectEnd.y - selRectStart.y)
                    drawRect(Color(0x22000000), topLeft = Offset(l, t), size = Size(w, h))
                    // Svart streckad linje (kontur)
                    drawRect(Color.Black, topLeft = Offset(l, t), size = Size(w, h),
                        style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)))
                    // Vit streckad linje ovanpå (offset) — ger "marching ants"-effekt
                    drawRect(Color.White, topLeft = Offset(l, t), size = Size(w, h),
                        style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 7.5f)))
                }

                // Markering (infångad, kan flyttas)
                if (selState == SelState.CAPTURED) {
                    val cap = selCapture
                    if (cap != null) {
                        val ox = selOrigin.x + selOffset.x; val oy = selOrigin.y + selOffset.y
                        drawImage(cap.asImageBitmap(), topLeft = Offset(ox, oy))
                        drawRect(Color.Black, topLeft = Offset(ox, oy),
                            size = Size(cap.width.toFloat(), cap.height.toFloat()),
                            style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)))
                        drawRect(Color.White, topLeft = Offset(ox, oy),
                            size = Size(cap.width.toFloat(), cap.height.toFloat()),
                            style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 7.5f)))
                    }
                }

                // Verktygsmarkör vid fingerposition
                val cp = cursorPos
                if (cp != null) {
                    val r = brushSize / 2f
                    when (drawMode) {
                        DrawMode.DRAW -> {
                            // Cirkel som visar penselstorlek
                            drawCircle(Color.Black, r + 1.5f, cp, style = Stroke(2.5f))
                            drawCircle(Color.White, r, cp, style = Stroke(1.5f))
                            // Litet kryss i mitten
                            drawLine(Color.White, Offset(cp.x - 5f, cp.y), Offset(cp.x + 5f, cp.y), 1.5f)
                            drawLine(Color.White, Offset(cp.x, cp.y - 5f), Offset(cp.x, cp.y + 5f), 1.5f)
                        }
                        DrawMode.ERASE -> {
                            // Kvadratisk suddgummi-markör (PS-stil)
                            val half = (brushSize / 2f).coerceAtLeast(8f)
                            drawRect(Color.Black, topLeft = Offset(cp.x - half - 1.5f, cp.y - half - 1.5f),
                                size = Size(half * 2f + 3f, half * 2f + 3f), style = Stroke(2.5f))
                            drawRect(Color.White, topLeft = Offset(cp.x - half, cp.y - half),
                                size = Size(half * 2f, half * 2f), style = Stroke(1.5f))
                        }
                        DrawMode.SPRAY -> {
                            val sr = brushSize * 0.9f
                            drawCircle(Color.Black, sr + 1.5f, cp, style = Stroke(2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)))
                            drawCircle(Color.White, sr, cp, style = Stroke(1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 3f)))
                        }
                        DrawMode.SMUDGE -> {
                            val smR = brushSize / 2f
                            drawCircle(Color.Black, smR + 1.5f, cp, style = Stroke(2f))
                            drawCircle(Color(0xFFFFAA00), smR, cp, style = Stroke(1.5f))
                        }
                        DrawMode.DODGE -> {
                            val dR = brushSize / 2f
                            drawCircle(Color.Black, dR + 1.5f, cp, style = Stroke(2f))
                            drawCircle(Color(0xFFFFEE44), dR, cp, style = Stroke(1.5f))
                        }
                        DrawMode.BURN -> {
                            val bR = brushSize / 2f
                            drawCircle(Color.Black, bR + 1.5f, cp, style = Stroke(2f))
                            drawCircle(Color(0xFFFF4422), bR, cp, style = Stroke(1.5f))
                        }
                        DrawMode.FILL -> {
                            // Korsikoner med hinkform
                            drawLine(Color.Black, Offset(cp.x - 16f, cp.y), Offset(cp.x + 16f, cp.y), 3f)
                            drawLine(Color.Black, Offset(cp.x, cp.y - 16f), Offset(cp.x, cp.y + 16f), 3f)
                            drawLine(Color.White, Offset(cp.x - 15f, cp.y), Offset(cp.x + 15f, cp.y), 1.5f)
                            drawLine(Color.White, Offset(cp.x, cp.y - 15f), Offset(cp.x, cp.y + 15f), 1.5f)
                            drawCircle(currentColor, 6f, cp)
                            drawCircle(Color.Black, 6f, cp, style = Stroke(1.5f))
                        }
                        DrawMode.EYEDROPPER -> {
                            drawLine(Color.Black, Offset(cp.x - 17f, cp.y), Offset(cp.x + 17f, cp.y), 3f)
                            drawLine(Color.Black, Offset(cp.x, cp.y - 17f), Offset(cp.x, cp.y + 17f), 3f)
                            drawLine(Color.White, Offset(cp.x - 16f, cp.y), Offset(cp.x + 16f, cp.y), 1.5f)
                            drawLine(Color.White, Offset(cp.x, cp.y - 16f), Offset(cp.x, cp.y + 16f), 1.5f)
                            drawCircle(Color.Black, 10f, cp, style = Stroke(3f))
                            drawCircle(Color.White, 10f, cp, style = Stroke(1.5f))
                        }
                        DrawMode.SELECTION -> {
                            // Markeringsverktyg-kursor: kors med liten markerings-ikon
                            drawLine(Color.Black, Offset(cp.x - 16f, cp.y), Offset(cp.x + 16f, cp.y), 3f)
                            drawLine(Color.Black, Offset(cp.x, cp.y - 16f), Offset(cp.x, cp.y + 16f), 3f)
                            drawLine(Color.White, Offset(cp.x - 15f, cp.y), Offset(cp.x + 15f, cp.y), 1.5f)
                            drawLine(Color.White, Offset(cp.x, cp.y - 15f), Offset(cp.x, cp.y + 15f), 1.5f)
                        }
                        DrawMode.TEXT -> {
                            // I-balk cursor (text-markör)
                            val th = 18f
                            drawLine(Color.Black, Offset(cp.x, cp.y - th), Offset(cp.x, cp.y + th), 3f)
                            drawLine(Color.Black, Offset(cp.x - 8f, cp.y - th), Offset(cp.x + 8f, cp.y - th), 3f)
                            drawLine(Color.Black, Offset(cp.x - 8f, cp.y + th), Offset(cp.x + 8f, cp.y + th), 3f)
                            drawLine(Color.White, Offset(cp.x, cp.y - th), Offset(cp.x, cp.y + th), 1.5f)
                            drawLine(Color.White, Offset(cp.x - 7f, cp.y - th), Offset(cp.x + 7f, cp.y - th), 1.5f)
                            drawLine(Color.White, Offset(cp.x - 7f, cp.y + th), Offset(cp.x + 7f, cp.y + th), 1.5f)
                        }
                        DrawMode.PARTICLE -> {
                            val pR = brushSize / 2f
                            drawCircle(Color.Black, pR + 1.5f, cp, style = Stroke(2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)))
                            drawCircle(currentColor.copy(alpha = 0.7f), pR, cp, style = Stroke(1.5f))
                        }
                        DrawMode.HAND -> {
                            // Hand-cursor: öppen hand (cirkel + fingrar)
                            drawCircle(Color.Black, 14f, cp, style = Stroke(3f))
                            drawCircle(Color.White, 14f, cp, style = Stroke(1.5f))
                            for (i in -2..2) {
                                val fx = cp.x + i * 5f
                                drawLine(Color.Black, Offset(fx, cp.y - 14f), Offset(fx, cp.y - 22f), 4f)
                                drawLine(Color.White, Offset(fx, cp.y - 14f), Offset(fx, cp.y - 22f), 2f)
                            }
                        }
                        else -> {
                            // Generellt kors för övriga verktyg
                            drawLine(Color.Black, Offset(cp.x - 16f, cp.y), Offset(cp.x + 16f, cp.y), 3f)
                            drawLine(Color.Black, Offset(cp.x, cp.y - 16f), Offset(cp.x, cp.y + 16f), 3f)
                            drawLine(Color.White, Offset(cp.x - 15f, cp.y), Offset(cp.x + 15f, cp.y), 1.5f)
                            drawLine(Color.White, Offset(cp.x, cp.y - 15f), Offset(cp.x, cp.y + 15f), 1.5f)
                        }
                    }
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

                // Text-objekt (eget lager)
                textObjects.forEach { tObj ->
                    val paint = AndroidPaint().apply {
                        color = tObj.color.toArgb(); textSize = tObj.fontSize; isAntiAlias = true
                        typeface = when (tObj.style) {
                            TextStyleOption.BOLD   -> android.graphics.Typeface.DEFAULT_BOLD
                            TextStyleOption.ITALIC -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                            TextStyleOption.MONO   -> android.graphics.Typeface.MONOSPACE
                            TextStyleOption.NORMAL -> android.graphics.Typeface.DEFAULT
                        }
                    }
                    nativeC.drawText(tObj.text, tObj.x, tObj.y, paint)
                    if (tObj.id == selectedTextId) {
                        val hw = tObj.fontSize * tObj.text.length * 0.3f; val hh = tObj.fontSize
                        drawRect(Color(0xFF54A0FF), topLeft = Offset(tObj.x-hw, tObj.y-hh),
                            size = Size(hw*2, hh*1.3f),
                            style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f,5f), 0f)))
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

                // Crop-overlay
                if (showCropDialog && cropTab == 0) {
                    val W = size.width; val H = size.height
                    val lx = cropL * W; val ty = cropT * H
                    val rx = cropR * W; val by2 = cropB * H
                    // Mörk overlay utanför crop-rutan
                    drawRect(Color(0x88000000), topLeft = Offset(0f, 0f), size = Size(W, ty))
                    drawRect(Color(0x88000000), topLeft = Offset(0f, by2), size = Size(W, H - by2))
                    drawRect(Color(0x88000000), topLeft = Offset(0f, ty), size = Size(lx, by2 - ty))
                    drawRect(Color(0x88000000), topLeft = Offset(rx, ty), size = Size(W - rx, by2 - ty))
                    // Crop-ram
                    drawRect(Color.White, topLeft = Offset(lx, ty), size = Size(rx - lx, by2 - ty), style = Stroke(2f))
                    // Rule-of-thirds-linjer
                    val dw = (rx - lx) / 3f; val dh = (by2 - ty) / 3f
                    for (i in 1..2) {
                        drawLine(Color(0x66FFFFFF), Offset(lx + i * dw, ty), Offset(lx + i * dw, by2), 1f)
                        drawLine(Color(0x66FFFFFF), Offset(lx, ty + i * dh), Offset(rx, ty + i * dh), 1f)
                    }
                    // Kanthandtag (mitt)
                    val hLen = 30f; val hW = 4f; val hCol = Color.White
                    drawLine(hCol, Offset(lx, H/2f - hLen/2f), Offset(lx, H/2f + hLen/2f), hW)
                    drawLine(hCol, Offset(rx, H/2f - hLen/2f), Offset(rx, H/2f + hLen/2f), hW)
                    drawLine(hCol, Offset(W/2f - hLen/2f, ty), Offset(W/2f + hLen/2f, ty), hW)
                    drawLine(hCol, Offset(W/2f - hLen/2f, by2), Offset(W/2f + hLen/2f, by2), hW)
                    // Hörn-handtag
                    val cLen = 22f
                    for ((cx2, cy2) in listOf(Offset(lx,ty), Offset(rx,ty), Offset(rx,by2), Offset(lx,by2))) {
                        val sx = if (cx2 == lx) 1f else -1f; val sy = if (cy2 == ty) 1f else -1f
                        drawLine(hCol, Offset(cx2, cy2), Offset(cx2 + sx*cLen, cy2), hW+1f)
                        drawLine(hCol, Offset(cx2, cy2), Offset(cx2, cy2 + sy*cLen), hW+1f)
                    }
                }

                // (Live-preview för Straighten och Skew hanteras via nativeCanvas ovan)

                // Pappers-skugga och -ram (syns när man zoomar ut)
                if (zoomScale < 0.98f) {
                    drawRect(
                        color = Color(0x44000000),
                        topLeft = Offset(6f / zoomScale, 6f / zoomScale),
                        size = Size(size.width.toFloat(), size.height.toFloat())
                    )
                    drawRect(
                        color = Color(0x66000000),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width.toFloat(), size.height.toFloat()),
                        style = Stroke(2f / zoomScale)
                    )
                }
            }
            } // end canvas Box

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

        // Crop / Rotera / Transform bottom-bar
        AnimatedVisibility(
            visible = showCropDialog,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A))
            ) {
                // Tab-rad + Avbryt/Tillämpa
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF252525)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Crop", "Rotera", "Transform").forEachIndexed { i, lbl ->
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (cropTab == i) Color(0xFF5F27CD) else Color(0xFF3A3A3A))
                                .clickable { cropTab = i }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text(lbl, fontSize = 13.sp, color = Color.White, fontWeight = if (cropTab == i) FontWeight.Bold else FontWeight.Normal) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { restoreOrigLayers(); renderTick++ }) {
                        Text("Återst.", color = Color(0xFFFF9F43), fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        restoreOrigLayers()
                        showCropDialog = false
                    }) { Text("Avbryt", color = Color(0xFFFF4757)) }
                    Button(onClick = {
                        when (cropTab) {
                            0 -> applyCrop()
                            1 -> {
                                if (straightenDeg != 0f) {
                                    val deg = straightenDeg
                                    applyToAllLayers { bmp -> straightenBitmap(bmp, deg) }
                                    straightenDeg = 0f
                                }
                            }
                            2 -> {
                                val sh = skewH; val sv = skewV
                                applyToAllLayers { bmp -> skewBitmap(bmp, sh, sv) }
                                skewH = 0f; skewV = 0f
                            }
                        }
                        showCropDialog = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BE881))) {
                        Text("Tillämpa", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                // Innehåll per tab
                when (cropTab) {
                    // ── CROP ──────────────────────────────────────────────
                    0 -> {
                        // Aspect-ratio-chips
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            aspectPresets.forEachIndexed { i, preset ->
                                val sel = selectedAspect == i
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Color(0xFF5F27CD) else Color(0xFF2C2C2C))
                                        .then(if (sel) Modifier.border(1.dp, Color(0xFFFF9F43), RoundedCornerShape(8.dp)) else Modifier)
                                        .clickable {
                                            selectedAspect = i
                                            // Alltid börja från full canvas, snap sedan till ratio
                                            cropL = 0f; cropT = 0f; cropR = 1f; cropB = 1f
                                            if (i != 0) {
                                                val p = aspectPresets[i]
                                                val ratio = p.w.toFloat() / p.h.toFloat()
                                                val canvasRatio = canvasWidth.toFloat() / canvasHeight.toFloat()
                                                if (canvasRatio > ratio) {
                                                    // Canvas bredare än preset → begränsa bredd
                                                    val newCW = ratio / canvasRatio
                                                    cropL = (1f - newCW) / 2f
                                                    cropR = 1f - cropL
                                                } else {
                                                    // Canvas högre än preset → begränsa höjd
                                                    val newCH = canvasRatio / ratio
                                                    cropT = (1f - newCH) / 2f
                                                    cropB = 1f - cropT
                                                }
                                            }
                                            renderTick++
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(preset.label, fontSize = 11.sp, color = Color.White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                        if (preset.w > 0) Text("${preset.w}:${preset.h}", fontSize = 9.sp, color = Color(0xFFAAAAAA))
                                    }
                                }
                            }
                        }
                        // Crop-mått-info
                        val cWpx = ((cropR - cropL) * canvasWidth).toInt()
                        val cHpx = ((cropB - cropT) * canvasHeight).toInt()
                        Text("  $cWpx × $cHpx px   •   dra i kanterna på bilden för att beskära",
                            fontSize = 11.sp, color = Color(0xFF888888), modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                    }

                    // ── ROTERA ────────────────────────────────────────────
                    1 -> {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Straighten
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Straighten", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = straightenDeg, onValueChange = { straightenDeg = it; renderTick++ },
                                    valueRange = -45f..45f, modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF5F27CD), activeTrackColor = Color(0xFF5F27CD))
                                )
                                Text("${straightenDeg.toInt()}°", fontSize = 12.sp, color = Color.White, modifier = Modifier.width(34.dp))
                            }
                            // Knappar
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("↻ 90°" to { applyToAllLayers { bmp -> rotateCWLayer(bmp) } },
                                    "↺ 90°" to { applyToAllLayers { bmp -> rotateCCWLayer(bmp) } },
                                    "180°" to { applyToAllLayers { bmp -> rotateCWLayer(rotateCWLayer(bmp)) } },
                                    "⟺ H" to { applyToAllLayers { bmp -> flipHorizontalLayer(bmp) } },
                                    "⟺ V" to { applyToAllLayers { bmp -> flipVerticalLayer(bmp) } }
                                ).forEach { (lbl, action) ->
                                    OutlinedButton(
                                        onClick = action,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(4.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) { Text(lbl, fontSize = 12.sp) }
                                }
                            }
                        }
                    }

                    // ── TRANSFORM ─────────────────────────────────────────
                    2 -> {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Horis. skev", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = skewH, onValueChange = { skewH = it; renderTick++ },
                                    valueRange = -0.5f..0.5f, modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF5F27CD), activeTrackColor = Color(0xFF5F27CD))
                                )
                                Text("${(skewH * 100).toInt()}%", fontSize = 12.sp, color = Color.White, modifier = Modifier.width(34.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Vert. skev", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = skewV, onValueChange = { skewV = it; renderTick++ },
                                    valueRange = -0.5f..0.5f, modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF5F27CD), activeTrackColor = Color(0xFF5F27CD))
                                )
                                Text("${(skewV * 100).toInt()}%", fontSize = 12.sp, color = Color.White, modifier = Modifier.width(34.dp))
                            }
                            TextButton(onClick = { skewH = 0f; skewV = 0f; renderTick++ }) {
                                Text("Återställ", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        // ── Justeringar bottom-bar ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = showAdjustBar,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A))) {
                // Chip-rad
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    adjDefs.forEachIndexed { i, def ->
                        val sel = adjSelectedChip == i
                        val val0 = adjValues[def.key] ?: 0f
                        val modified = val0 != 0f
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(when { sel -> Color(0xFF5F27CD); modified -> Color(0xFF2C2060); else -> Color(0xFF2C2C2C) })
                                .then(if (modified && !sel) Modifier.border(1.dp, Color(0xFFFF9F43), RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { adjSelectedChip = i }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(def.label, fontSize = 11.sp, color = Color.White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                if (modified) {
                                    val pct = (val0 * 100f).toInt()
                                    Text("${if (pct > 0 && def.range.start < 0f) "+" else ""}$pct", fontSize = 9.sp, color = Color(0xFFFF9F43))
                                }
                            }
                        }
                    }
                }
                // Slider
                val curDef = adjDefs[adjSelectedChip]
                val curVal = adjValues[curDef.key] ?: 0f
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(curDef.label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(72.dp))
                    Slider(
                        value = curVal,
                        onValueChange = { v -> adjValues[curDef.key] = v; adjTick++ },
                        valueRange = curDef.range,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF5F27CD), activeTrackColor = Color(0xFF5F27CD))
                    )
                    val pct = (curVal * 100f).toInt()
                    Text("${if (pct > 0 && curDef.range.start < 0f) "+" else ""}$pct", fontSize = 12.sp, color = Color.White, modifier = Modifier.width(38.dp))
                    TextButton(onClick = { adjValues[curDef.key] = 0f; adjTick++ }, contentPadding = PaddingValues(4.dp)) {
                        Text("0", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                // Knappar
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF252525)).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { adjDefs.forEach { adjValues[it.key] = 0f }; adjTick++ }) {
                        Text("Nollst.", color = Color(0xFFFF9F43), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { adjDefs.forEach { adjValues[it.key] = 0f }; showAdjustBar = false }) {
                        Text("Avbryt", color = Color(0xFFFF4757))
                    }
                    Button(
                        onClick = {
                            val idx = layers.indexOfFirst { it.id == activeLayerId }
                            if (idx >= 0) {
                                val bmp = layers[idx].bitmap
                                if (bmp != null) {
                                    val vTemp = adjValues["temperature"] ?: 0f; if (vTemp != 0f) temperatureFilter(bmp, vTemp)
                                    val vTi   = adjValues["tint"]        ?: 0f; if (vTi   != 0f) tintFilter(bmp, vTi)
                                    val vCo   = adjValues["contrast"]    ?: 0f; if (vCo   != 0f) applyContrastFilter(bmp, (1f + vCo * 1.5f).coerceAtLeast(0.1f))
                                    val vBr   = adjValues["brightness"]  ?: 0f; if (vBr   != 0f) applyBrightnessFilter(bmp, 1f + vBr)
                                    val vHi   = adjValues["highlights"]  ?: 0f; if (vHi   != 0f) highlightsFilter(bmp, vHi)
                                    val vSh   = adjValues["shadows"]     ?: 0f; if (vSh   != 0f) shadowsFilter(bmp, vSh)
                                    val vSa   = adjValues["saturation"]  ?: 0f; if (vSa   != 0f) applySaturationFilter(bmp, (1f + vSa * 2f).coerceAtLeast(0f))
                                    val vCl   = adjValues["clarity"]     ?: 0f; if (vCl   != 0f) clarityFilter(bmp, vCl)
                                    val vTx   = adjValues["texture"]     ?: 0f; if (vTx   != 0f) textureFilter(bmp, vTx)
                                    val vSp   = adjValues["sharpen"]     ?: 0f; if (vSp   >  0f) sharpenFilter(bmp, vSp * 2f)
                                    val vVi   = adjValues["vignette"]    ?: 0f; if (vVi   >  0f) vignetteFilter(bmp, vVi * 2f)
                                    val vFa   = adjValues["fade"]        ?: 0f; if (vFa   >  0f) fadeFilter(bmp, vFa)
                                    compositeAllLayers()
                                }
                            }
                            adjDefs.forEach { adjValues[it.key] = 0f }
                            showAdjustBar = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BE881))
                    ) { Text("Tillämpa", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Stämpelpicker (expanderbar nerifrån, bara för STAMP-läge)
        AnimatedVisibility(visible = drawMode == DrawMode.STAMP && showStampPicker && selectedObjId == null,
            enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A2E)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    stampCategories.keys.forEach { cat ->
                        val sel = stampCategory == cat
                        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Color(0xFF5F27CD) else Color(0xFF2C2C2C))
                            .clickable { stampCategory = cat }.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text(cat, fontSize = 11.sp, color = Color.White) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (stampCategories[stampCategory] ?: emptyList()).forEach { emoji ->
                        val sel = selectedStamp == emoji
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Color(0xFF5F27CD) else Color(0xFF2C2C2C))
                            .then(if (sel) Modifier.border(2.dp, Color(0xFFFF9F43), RoundedCornerShape(8.dp)) else Modifier)
                            .clickable { selectedStamp = emoji; selectedObjId = null; showStampPicker = false },
                            contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
                    }
                }
            }
        }

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
private fun MenuItemRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                fontSize = 13.sp,
                color = if (danger) Color(0xFFFF6B6B) else Color(0xFFD4D4D4)
            )
        },
        onClick = onClick,
        modifier = Modifier.background(Color(0xFF2D2D2D))
    )
}

// Options bar helpers
@Composable
private fun OVertDivider() {
    Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color(0xFF444444)))
}

@Composable
private fun OSizeSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, thumbColor: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 10.sp, color = Color(0xFF888888))
        // Visuell cirkel som visar storlek
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val dotDp = (4f + fraction * 20f).dp
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(dotDp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
        }
        // Värde i fet text
        Text("${value.toInt()}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.width(90.dp),
            colors = SliderDefaults.colors(thumbColor = thumbColor, activeTrackColor = thumbColor,
                inactiveTrackColor = thumbColor.copy(alpha = 0.25f)))
    }
}

@Composable
private fun OOpacitySlider(opacity: Float, currentColor: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Opacitet", fontSize = 10.sp, color = Color(0xFF888888))
        // Visuell ruta: schackrutor + färg med aktuell opacitet
        Box(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(4.dp))) {
            // Schackruta-bakgrund
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cs = 6.dp.toPx()
                val cols = (size.width / cs).toInt() + 1
                val rows = (size.height / cs).toInt() + 1
                for (r in 0..rows) for (c in 0..cols)
                    drawRect(if ((r+c)%2==0) Color(0xFF888888) else Color(0xFF555555),
                        topLeft = Offset(c*cs, r*cs), size = Size(cs, cs))
            }
            Box(modifier = Modifier.fillMaxSize().background(currentColor.copy(alpha = opacity)))
        }
        // Värde i fet text
        Text("${(opacity * 100).toInt()}%", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp))
        Slider(value = opacity, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.width(80.dp),
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9F43), activeTrackColor = Color(0xFFFF9F43),
                inactiveTrackColor = Color(0xFFFF9F43).copy(alpha = 0.25f)))
    }
}

@Composable
private fun OBar(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 10.sp, color = Color(0xFF888888))
        content()
    }
}

@Composable
private fun OToggle(label: String, active: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .background(if (active) Color(0xFF1473E6) else Color(0xFF3A3A3A))
            .clickable { onChange(!active) }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) { Text(label, fontSize = 11.sp, color = if (active) Color.White else Color(0xFFAAAAAA)) }
}

@Composable
private fun OChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .background(if (selected) Color(0xFF1473E6) else Color(0xFF3A3A3A))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) { Text(label, fontSize = 11.sp, color = if (selected) Color.White else Color(0xFFAAAAAA)) }
}

@Composable
private fun OActionBtn(label: String, color: Color = Color(0xFF3A3A3A), onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .background(color).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium) }
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
// HSV-färgväljare
// =============================================================================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HsvColorPickerDialog(
    initialColor: Color,
    title: String = "Välj färg",
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (initialColor.alpha * 255).toInt(),
            (initialColor.red * 255).toInt(),
            (initialColor.green * 255).toInt(),
            (initialColor.blue * 255).toInt()
        ), initHsv
    )
    var hue   by remember { mutableFloatStateOf(initHsv[0]) }
    var sat   by remember { mutableFloatStateOf(initHsv[1]) }
    var hsv_v by remember { mutableFloatStateOf(initHsv[2]) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }

    var svSize    by remember { mutableStateOf(Size.Zero) }
    var hueSize   by remember { mutableStateOf(Size.Zero) }
    var alphaSize by remember { mutableStateOf(Size.Zero) }

    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val chosen   = Color(android.graphics.Color.HSVToColor(
        (alpha * 255).toInt().coerceIn(0, 255), floatArrayOf(hue, sat, hsv_v)
    ))

    val hueColors = remember {
        (0..12).map { i -> Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f))) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Förhandsvisning + hex-kod
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(chosen))
                    val hex = "#%02X%02X%02X".format(
                        (chosen.red * 255).toInt(),
                        (chosen.green * 255).toInt(),
                        (chosen.blue * 255).toInt()
                    )
                    Text(hex, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("A: ${(alpha * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray)
                }

                // SV-rektangel (mättnad × ljusstyrka)
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color.Gray, RoundedCornerShape(6.dp))
                        .onSizeChanged { svSize = Size(it.width.toFloat(), it.height.toFloat()) }
                        .pointerInteropFilter { event ->
                            if (svSize != Size.Zero && (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE)) {
                                sat   = (event.x / svSize.width).coerceIn(0f, 1f)
                                hsv_v = (1f - event.y / svSize.height).coerceIn(0f, 1f)
                            }
                            true
                        }
                ) {
                    // Horisontell gradient: vit → ren nyansfärg
                    drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                    // Vertikal overlay: transparent → svart
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    // Korsmarkör
                    val cx = sat * size.width
                    val cy = (1f - hsv_v) * size.height
                    drawCircle(Color.Black, 11f, Offset(cx, cy), style = Stroke(3f))
                    drawCircle(Color.White, 11f, Offset(cx, cy), style = Stroke(1.5f))
                }

                // Nyansfältet (hue)
                Text("Nyans", fontSize = 10.sp, color = Color.Gray)
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .onSizeChanged { hueSize = Size(it.width.toFloat(), it.height.toFloat()) }
                        .pointerInteropFilter { event ->
                            if (hueSize != Size.Zero && (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE)) {
                                hue = (event.x / hueSize.width * 360f).coerceIn(0f, 360f)
                            }
                            true
                        }
                ) {
                    drawRect(Brush.horizontalGradient(hueColors))
                    // Position-indikator
                    val hx = hue / 360f * size.width
                    drawRect(Color.White, topLeft = Offset(hx - 2f, 0f), size = Size(4f, size.height))
                    drawRect(Color.Black, topLeft = Offset(hx - 1f, 0f), size = Size(2f, size.height))
                }

                // Alfafältet (genomskinlighet)
                Text("Genomskinlighet", fontSize = 10.sp, color = Color.Gray)
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .onSizeChanged { alphaSize = Size(it.width.toFloat(), it.height.toFloat()) }
                        .pointerInteropFilter { event ->
                            if (alphaSize != Size.Zero && (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE)) {
                                alpha = (event.x / alphaSize.width).coerceIn(0f, 1f)
                            }
                            true
                        }
                ) {
                    // Schackrutebakgrund (visar transparens)
                    val cs = 13f
                    val cols = (size.width / cs).toInt() + 1
                    val rows = (size.height / cs).toInt() + 1
                    for (row in 0..rows) for (col in 0..cols) {
                        drawRect(
                            if ((row + col) % 2 == 0) Color(0xFFCCCCCC) else Color(0xFF888888),
                            topLeft = Offset(col * cs, row * cs), size = Size(cs, cs)
                        )
                    }
                    // Transparent → vald färg (utan alfa)
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, chosen.copy(alpha = 1f))))
                    // Position-indikator
                    val ax = alpha * size.width
                    drawRect(Color.White, topLeft = Offset(ax - 2f, 0f), size = Size(4f, size.height))
                    drawRect(Color.Black, topLeft = Offset(ax - 1f, 0f), size = Size(2f, size.height))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(chosen); onDismiss() }) { Text("Välj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
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

private fun saveToGalleryJpg(context: Context, bitmap: android.graphics.Bitmap?) {
    if (bitmap == null) { Toast.makeText(context, "Rita nagot forst!", Toast.LENGTH_SHORT).show(); return }
    try {
        val filename = "Rita_${System.currentTimeMillis()}.jpg"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rita")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { s -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, s) }
            Toast.makeText(context, "Sparad som JPG i Bilder/Rita!", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, "Kunde inte spara", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Fel vid sparande", Toast.LENGTH_SHORT).show()
    }
}
