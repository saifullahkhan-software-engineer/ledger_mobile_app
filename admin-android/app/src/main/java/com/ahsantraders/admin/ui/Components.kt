@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ahsantraders.admin.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.R
import com.ahsantraders.admin.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val MAX_COMPONENT_IMAGE_BYTES = 8 * 1024 * 1024

private val componentImageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

private fun readBoundedBytes(stream: java.io.InputStream, limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = stream.read(buffer)
        if (read == -1) break
        total += read
        if (total > limit) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

suspend fun loadRemoteBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = componentImageClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null
            else {
                response.body?.let { body ->
                    if (body.contentLength() > MAX_COMPONENT_IMAGE_BYTES) null
                    else body.byteStream().use { readBoundedBytes(it, MAX_COMPONENT_IMAGE_BYTES) }
                }
            }
        }
        bytes?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) }
    }.getOrNull()
}

fun decodeDataUriOrBase64Bitmap(src: String): Bitmap? {
    val trimmed = src.trim()
    val raw = if (trimmed.startsWith("data:image/") && trimmed.contains(";base64,")) {
        trimmed.substringAfter(";base64,")
    } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("/")) {
        trimmed
    } else {
        null
    } ?: return null
    return runCatching {
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

// ---------------------------------------------------------------------------
// Icons baked in at build time.
//
// The Gradle task `fetchAppIcons` (see build.gradle.kts) downloads the icons
// that are saved in the backend database — the ones uploaded through the app's
// "Screen icons" screen — into assets/saved_icons/<key>.<ext> on EVERY build,
// so they get packaged inside the APK. The app then uses those icons directly
// (no runtime download, works offline).
//
// Resolution order for every icon slot:
//   1. live image URL from the server (if one is currently set) — always the freshest
//   2. the icon baked into this build from the database
//   3. the bundled default (sector vector icon / saved logo lockup)
// ---------------------------------------------------------------------------
private const val BUILT_IN_ICON_DIR = "saved_icons"
private val builtInIconExtensions = listOf("png", "jpg", "jpeg", "webp", "gif", "ico")

/** Loads `saved_icons/<key>.<ext>` from the APK assets, or null when this build has no baked icon for it. */
fun loadBuiltInIcon(context: Context, key: String): Bitmap? {
    val safe = key.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").ifBlank { "icon" }
    for (ext in builtInIconExtensions) {
        val bmp = runCatching {
            context.assets.open("$BUILT_IN_ICON_DIR/$safe.$ext").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (bmp != null) return bmp
    }
    return null
}

/**
 * Resolves one icon with the built-in priority: live server URL → icon baked
 * into this build from the database → the provided default. Custom images are
 * always shown in a CIRCULAR container (never a square one).
 */
@Composable
fun ResolvedIcon(
    liveUrl: String?,
    builtInKeys: List<String>,
    modifier: Modifier,
    shape: Shape = CircleShape,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    if (!liveUrl.isNullOrBlank()) {
        DynamicImage(
            src = liveUrl,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
            fallback = { builtInOrFallback(builtInKeys, context, modifier, shape, fallback) }
        )
    } else {
        builtInOrFallback(builtInKeys, context, modifier, shape, fallback)
    }
}

@Composable
private fun builtInOrFallback(
    keys: List<String>,
    context: Context,
    modifier: Modifier,
    shape: Shape,
    fallback: @Composable () -> Unit
) {
    val bmp = remember(keys) { keys.firstNotNullOfOrNull { loadBuiltInIcon(context, it) } }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        fallback()
    }
}

@Composable
fun DynamicImage(
    src: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit
) {
    val clean = src?.trim().orEmpty()
    if (clean.isBlank()) {
        fallback()
        return
    }

    val embeddedBitmap = remember(clean) { decodeDataUriOrBase64Bitmap(clean) }
    if (embeddedBitmap != null) {
        Image(
            bitmap = embeddedBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        )
        return
    }

    if (clean.startsWith("http://") || clean.startsWith("https://")) {
        var bmp by remember(clean) { mutableStateOf<Bitmap?>(null) }
        var failed by remember(clean) { mutableStateOf(false) }
        LaunchedEffect(clean) {
            val fetched = loadRemoteBitmap(clean)
            if (fetched != null) {
                bmp = fetched
            } else {
                failed = true
            }
        }
        val currentBmp = bmp
        if (currentBmp != null) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = modifier
            )
        } else if (failed) {
            fallback()
        }
        return
    }

    fallback()
}

fun sectorIcon(type: String): ImageVector = when (type) {
    "CHICKEN" -> Icons.Default.Restaurant
    "LPG" -> Icons.Default.LocalFireDepartment
    else -> Icons.Default.Eco
}

fun sectorDrawableRes(type: String): Int = when (type) {
    "CHICKEN" -> R.drawable.ic_sector_chicken
    "LPG" -> R.drawable.ic_sector_lpg
    else -> R.drawable.ic_sector_broiler
}

@Composable
fun SectorIcon(type: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Icon(
        painter = painterResource(sectorDrawableRes(type)),
        contentDescription = sectorName(type),
        tint = tint,
        modifier = modifier
    )
}

/**
 * Business icon for cards: the uploaded icon (live from the server, or the
 * copy baked into this build from the database) is shown in a CIRCULAR
 * container; the bundled saved vector icon stays the final fallback.
 */
@Composable
fun DynamicSectorIcon(
    type: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    shape: Shape = CircleShape
) {
    ResolvedIcon(
        liveUrl = imageUrl,
        builtInKeys = listOf("business_${type.lowercase()}"),
        modifier = modifier,
        shape = shape
    ) {
        SectorIcon(type = type, modifier = modifier, tint = tint)
    }
}

/**
 * Brand lockup for the top app bar, side bar and login header.
 *
 * Resolution order: live `app_logo` from the server → `app_logo` baked into
 * this build from the database → the saved logo asset (`logo_lockup.png`,
 * the AT badge + "AHSAN TRADERS" wordmark). Either way the brand looks
 * identical on every screen of the app.
 *
 * @param compact small size for the top app bar / side bar header.
 */
@Composable
fun Brand(compact: Boolean = false, liveUrl: String? = null) {
    // Saved lockup aspect ratio is 360:140 (≈ 2.571:1) — keep both dimensions in
    // sync with it so the image is never stretched.
    val height = if (compact) 34.dp else 72.dp
    val size = Modifier
        .height(height)
        .width((height.value * 2.571f).dp)
    if (!liveUrl.isNullOrBlank()) {
        DynamicImage(
            src = liveUrl,
            modifier = size,
            contentScale = ContentScale.Fit,
            fallback = { builtInLockup(size) }
        )
    } else {
        builtInLockup(size)
    }
}

/** `app_logo` baked into this build from the database, else the saved logo lockup. */
@Composable
private fun builtInLockup(modifier: Modifier) {
    val context = LocalContext.current
    val bmp = remember { loadBuiltInIcon(context, "app_logo") }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Ahsan Traders",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Image(
            painter = painterResource(R.drawable.logo_lockup),
            contentDescription = "Ahsan Traders",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, translate: Boolean = true) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(if (translate) tr(title) else title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Metric(label: String, value: String, icon: ImageVector = Icons.Default.Payments, previous: Long? = null, current: Long? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(Green, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
            Column(Modifier.weight(1f)) {
                Text(tr(label), style = MaterialTheme.typography.labelLarge, color = Muted)
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Forest)
            }
            if (previous != null && current != null) {
                val percent = if (previous > 0) (current.toDouble() - previous) / previous * 100 else null
                Column(horizontalAlignment = Alignment.End) {
                    Text(percent?.let { "%+.0f%%".format(it) } ?: "—", color = if (current >= previous) Green else Chicken, fontWeight = FontWeight.Bold)
                    Text("vs yesterday", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun DataRow(label: String, value: String, accent: Color = Ink) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(label), modifier = Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, color = accent, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Status(value: String) {
    val color = when (value) { "ACTIVE", "OPEN" -> Green; "FUNDING" -> Lpg; else -> Muted }
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
        Text(tr(value.lowercase().replaceFirstChar { it.uppercase() }), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    color: Color = Green,
    customImageUrl: String? = null,
    builtInKeys: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 90.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResolvedIcon(
                liveUrl = customImageUrl,
                builtInKeys = builtInKeys,
                modifier = Modifier.size(24.dp)
            ) { Icon(icon, null, tint = Color.White) }
            Text(tr(label), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LinkRow(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    customImageUrl: String? = null,
    builtInKeys: List<String> = emptyList(),
    translate: Boolean = true,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ResolvedIcon(
                liveUrl = customImageUrl,
                builtInKeys = builtInKeys,
                modifier = Modifier.size(24.dp)
            ) { Icon(icon, null, tint = Green) }
            Column(Modifier.weight(1f)) {
                Text(if (translate) tr(title) else title, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(it, fontSize = 12.sp, color = Muted) }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun Empty(message: String = "No records yet") {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Inventory2, null, tint = Muted, modifier = Modifier.size(40.dp))
        Text(tr(message), color = Muted)
    }
}

@Composable
fun Banner(message: String, error: Boolean, onDismiss: () -> Unit) {
    Surface(color = if (error) Color(0xFFFFEBE9) else Mint, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = if (error) Color(0xFF912727) else Forest, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Dismiss"), modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
fun Selection(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean = true, translateChoices: Boolean = false, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(
            value = choices.find { it.first == value }?.second?.let { if (translateChoices) tr(it) else it } ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(tr(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled,
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(if (translateChoices) tr(title) else title) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, null) },
        title = { Text(tr(title)) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(tr("Confirm")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

@Composable
fun MoreButton(s: AdminState, vm: AdminViewModel) {
    if (s.more) OutlinedButton(onClick = { vm.refresh(true) }, enabled = !s.loading && !s.saving, modifier = Modifier.fillMaxWidth()) { Text(tr("Load more")) }
}
