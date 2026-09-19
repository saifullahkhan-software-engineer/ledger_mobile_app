@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ahsantraders.admin.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Screen & business icons (SUPERADMIN only). Two ways to set an image:
//   • Upload — pick from this device's gallery; the server validates the real
//     image bytes and stores them in the PostgreSQL image_assets table.
//   • Paste URL — point at an externally hosted image (kept as a plain URL).
// Either way the saved value is a URL: uploaded images come back as a short
// relative /api/v1/images/{id} serving URL, which this app resolves against
// the configured server. Image bytes never travel inside icon lists or the
// mobile icons configuration — only URLs do.
// ---------------------------------------------------------------------------

private const val MAX_ICON_BYTES = 2 * 1024 * 1024
private const val MAX_PREVIEW_BYTES = 8 * 1024 * 1024

private enum class IconTab { UPLOAD, URL }
private data class PickedImage(val name: String, val bytes: ByteArray)

private data class IconSlot(
    val key: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
)

private val iconSlots = listOf(
    IconSlot("app_logo", "Brand & Header Logo", "Main AT badge on the mobile app bar and drawer.", Icons.Default.Eco, Forest),
    IconSlot("business_chicken", "Chicken Shop Card Icon", "Red Chicken Shop card (first).", Icons.Default.Restaurant, Chicken),
    IconSlot("business_lpg", "LPG / Gas Business Card Icon", "Blue LPG / Gas card (second).", Icons.Default.LocalFireDepartment, Lpg),
    IconSlot("business_broiler", "Poultry Farm (Broiler) Card Icon", "Green Poultry Farm card (third).", Icons.Default.Agriculture, Broiler),
    IconSlot("quick_sale", "Add Sale Action Icon", "\"Add sale\" quick action.", Icons.Default.AddCircle, Forest),
    IconSlot("quick_expense", "Add Expense Action Icon", "\"Add expense\" quick action.", Icons.Default.AccountBalanceWallet, Color(0xFFE58B19)),
    IconSlot("quick_reports", "Reports Action Icon", "\"Reports\" quick action.", Icons.Default.InsertChart, Forest),
    IconSlot("quick_stock", "Stock Action Icon", "\"Stock\" quick action.", Icons.Default.Inventory2, Forest),
)

/** Shared client for icon previews; image-serving routes are public, like the legacy /uploads files. */
private val imagePreviewClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/** Resolves the display name of a content URI, falling back to its last path segment. */
private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

/** Reads a stream with a hard byte cap; returns null when the cap is exceeded. */
private fun readBounded(stream: java.io.InputStream, limit: Int): ByteArray? {
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

/** Fetches a remote image with bounded reads, or null on any failure. */
private suspend fun fetchRemoteBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = imagePreviewClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                val body = response.body
                if (body.contentLength() > MAX_PREVIEW_BYTES) null
                else body.byteStream().use { readBounded(it, MAX_PREVIEW_BYTES) }
            }
        }
        bytes?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) }
    }.getOrNull()
}

/** Draws a picked bitmap, else a network image (http/https), else the fallback slot. */
@Composable
private fun PreviewImage(validUrl: String?, thumb: Bitmap?, fallback: @Composable () -> Unit) {
    if (thumb != null) {
        Image(bitmap = thumb.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        return
    }
    if (!validUrl.isNullOrBlank() && (validUrl.startsWith("http://") || validUrl.startsWith("https://"))) {
        var bmp by remember(validUrl) { mutableStateOf<Bitmap?>(null) }
        var failed by remember(validUrl) { mutableStateOf(false) }
        LaunchedEffect(validUrl) {
            val fetched = fetchRemoteBitmap(validUrl)
            if (fetched != null) bmp = fetched else failed = true
        }
        val currentBmp = bmp
        if (currentBmp != null) Image(bitmap = currentBmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (failed) fallback()
        return
    }
    fallback()
}

@Composable
fun IconsScreen(s: AdminState, vm: AdminViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Super admin: set the image shown on the mobile app for each screen card, app logo and quick action. Uploaded images are stored in the PostgreSQL database; the server returns a short /api/v1/images/… URL and the apps load the image from that URL — so delivery still works exactly like any hosted image.",
            color = Muted, fontSize = 12.sp
        )
        iconSlots.forEach { slot ->
            val saved = s.icons.find { it.key == slot.key }
            IconSlotCard(slot, saved, s, vm)
        }
        SectionTitle("Business icons", "A custom image can replace the coloured sector tile on the home screen.")
        if (s.user?.role != "SUPERADMIN") {
            Text("Only the owner can change business icons.", color = Muted, fontSize = 12.sp)
        } else if (s.businesses.isEmpty()) {
            Text("No businesses configured yet.", color = Muted, fontSize = 12.sp)
        } else {
            s.businesses.forEach { business -> BusinessIconCard(business, s, vm) }
        }
    }
}

@Composable
private fun IconSlotCard(slot: IconSlot, saved: AppIconItem?, s: AdminState, vm: AdminViewModel) {
    var editing by remember { mutableStateOf(false) }
    val rawUrl = saved?.image_url.orEmpty()
    val displayUrl = absoluteUrl(vm.server, rawUrl.ifBlank { null })
    val enabled = !s.saving && !s.uploading
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(slot.color, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                PreviewImage(displayUrl, null) { Icon(slot.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(slot.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (rawUrl.isNotBlank()) CustomBadge()
                }
                Text(if (rawUrl.isBlank()) "Using default system icon" else rawUrl, color = if (rawUrl.isBlank()) Muted else Lpg, fontSize = 11.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { editing = true }, enabled = enabled) { Text(if (rawUrl.isBlank()) tr("Add image") else tr("Change")) }
                if (rawUrl.isNotBlank()) TextButton(onClick = { vm.removeScreenIcon(slot.key, slot.label) }, enabled = enabled, modifier = Modifier.height(36.dp)) { Text(tr("Remove"), fontSize = 12.sp) }
            }
        }
    }
    if (editing) {
        IconEditorDialog(
            title = "Set image for ${slot.label}",
            description = slot.description,
            originalUrl = displayUrl,
            previewColor = slot.color,
            uploading = s.uploading,
            fallback = { Icon(slot.icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) },
            onSaveUrl = { url -> editing = false; vm.setScreenIconFromUrl(slot.key, slot.label, url) },
            onSaveImage = { bytes, name -> editing = false; vm.setScreenIconFromBytes(slot.key, slot.label, bytes, name) },
            onRemove = if (rawUrl.isNotBlank()) ({ vm.removeScreenIcon(slot.key, slot.label) }) else null,
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun BusinessIconCard(business: Business, s: AdminState, vm: AdminViewModel) {
    var editing by remember { mutableStateOf(false) }
    val displayUrl = absoluteUrl(vm.server, business.icon_url?.ifBlank { null })
    val enabled = !s.saving && !s.uploading
    Card(
        onClick = { editing = true },
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(sectorColor(business.type), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                PreviewImage(displayUrl, null) { SectorIcon(business.type, tint = Color.White, modifier = Modifier.size(26.dp)) }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(business.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (!business.icon_url.isNullOrBlank()) CustomBadge()
                }
                Text(tr(sectorName(business.type)), color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (business.icon_url.isNullOrBlank()) tr("Add image") else tr("Change"), color = Forest, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                if (!business.icon_url.isNullOrBlank()) {
                    TextButton(onClick = { vm.removeBusinessIcon(business) }, enabled = enabled, modifier = Modifier.height(32.dp)) { Text(tr("Remove"), fontSize = 12.sp) }
                }
            }
        }
    }
    if (editing) {
        IconEditorDialog(
            title = "Set image for ${business.name}",
            description = "Custom image for this business on the home screen.",
            originalUrl = displayUrl,
            previewColor = sectorColor(business.type),
            uploading = s.uploading,
            fallback = { SectorIcon(business.type, tint = Color.White, modifier = Modifier.size(30.dp)) },
            onSaveUrl = { url -> editing = false; vm.setBusinessIconFromUrl(business, url) },
            onSaveImage = { bytes, name -> editing = false; vm.setBusinessIconFromBytes(business, bytes, name) },
            onRemove = if (business.icon_url.isNullOrBlank()) null else ({ vm.removeBusinessIcon(business) }),
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun CustomBadge() {
    Surface(color = Gold.copy(alpha = .25f), shape = RoundedCornerShape(6.dp)) {
        Text("CUSTOM", color = Color(0xFF8E6C00), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun IconEditorDialog(
    title: String,
    description: String,
    originalUrl: String,
    previewColor: Color,
    uploading: Boolean,
    fallback: @Composable () -> Unit,
    onSaveUrl: (String) -> Unit,
    onSaveImage: (ByteArray, String) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // Upload is the primary flow: it stores image bytes in the database and
    // shows a preview. The external-URL option stays on its own tab for
    // legacy/self-hosted images and is never mixed into the upload flow.
    var tab by remember { mutableStateOf(if (originalUrl.startsWith("http")) IconTab.URL else IconTab.UPLOAD) }
    var draft by remember { mutableStateOf(originalUrl) }
    var picked by remember { mutableStateOf<PickedImage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            runCatching {
                val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
                    ?: throw IllegalStateException("Could not read the selected image")
                require(bytes.size <= MAX_ICON_BYTES) { "Image exceeds the 2 MB limit" }
                PickedImage(displayName(context, it), bytes)
            }.onSuccess {
                picked = it; error = null
            }.onFailure { e ->
                error = e.message ?: "Could not read the selected image"
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pickedBmp = remember(picked) { picked?.bytes?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull() } }
                    val previewUrl = if (tab == IconTab.URL && draft.trim().startsWith("http")) draft.trim()
                        else if (tab == IconTab.UPLOAD && originalUrl.isNotBlank()) originalUrl
                        else null
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(previewColor), contentAlignment = Alignment.Center) {
                        PreviewImage(previewUrl, if (tab == IconTab.UPLOAD) pickedBmp else null) { fallback() }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(description, color = Muted, fontSize = 12.sp)
                        picked?.let { Text("${it.name} · ${it.bytes.size / 1024} KB", color = Green, fontSize = 11.sp) }
                    }
                }
                TabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                    IconTab.entries.forEach { t ->
                        Tab(selected = tab == t, onClick = { tab = t; error = null }, text = { Text(if (t == IconTab.URL) "External URL" else "Upload image") })
                    }
                }
                when (tab) {
                    IconTab.UPLOAD -> {
                        OutlinedButton(
                            onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Choose from gallery")) }
                        Text("PNG, JPEG, WebP or ICO up to 2 MB. The image is stored in the database and shown from its own short /api/v1/images/… link — no URL typing needed.", color = Muted, fontSize = 11.sp)
                    }
                    IconTab.URL -> {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it; error = null },
                            label = { Text("Image URL") },
                            placeholder = { Text("https://…") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Only for images hosted elsewhere; the URL itself is stored without copying the image.", color = Muted, fontSize = 11.sp)
                        if (draft.isNotBlank()) TextButton(onClick = { draft = ""; error = null }) { Text(tr("Clear"), fontSize = 12.sp) }
                    }
                }
                error?.let { Text(it, color = Chicken, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (tab) {
                        IconTab.UPLOAD -> {
                            val p = picked
                            if (p == null) error = "Choose an image first" else onSaveImage(p.bytes, p.name)
                        }
                        IconTab.URL -> {
                            val v = draft.trim()
                            when {
                                v.isEmpty() -> error = "Enter a URL or switch to upload"
                                !v.startsWith("http://") && !v.startsWith("https://") -> error = "Enter a valid https:// image URL"
                                else -> onSaveUrl(v)
                            }
                        }
                    }
                },
                enabled = !uploading
            ) {
                if (uploading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Saving…"))
                } else {
                    Text(tr("Save"))
                }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRemove != null) {
                    TextButton(onClick = { onDismiss(); onRemove() }, enabled = !uploading) { Text(tr("Remove")) }
                }
                TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
            }
        },
    )
}
