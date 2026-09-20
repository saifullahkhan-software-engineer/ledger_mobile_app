plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// ---------------------------------------------------------------------------
// Backend base URL — resolved ONCE at build time and baked into the APK as
// BuildConfig.SERVER_URL. The user never types a server address in the app.
//
// Priority:
//   1. APP_SERVER_URL environment variable   (e.g. export APP_SERVER_URL=https://api.example.com)
//   2. -PappServerUrl=... on the command line or appServerUrl in gradle.properties
//   3. http://10.0.2.2:8000 (Android emulator loopback to the host PC)
//
// `explicitServerUrl` is the configured value WITHOUT the emulator fallback —
// the fetchAppIcons task uses it so that builds without any configured
// backend simply skip icon baking instead of probing the emulator address.
// ---------------------------------------------------------------------------
val explicitServerUrl: String = (System.getenv("APP_SERVER_URL") ?: "").trim()
    .ifBlank { ((project.findProperty("appServerUrl") as String?) ?: "").trim() }
    .removeSuffix("/")
val appServerUrl: String = explicitServerUrl.ifBlank { "http://10.0.2.2:8000" }

android {
    namespace = "com.ahsantraders.admin"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ahsantraders.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Baked-in backend URL (see resolution above) — used app-wide as the
        // default server address; the login screen no longer asks for it.
        buildConfigField("String", "SERVER_URL", "\"$appServerUrl\"")
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
// ---------------------------------------------------------------------------
// fetchAppIcons — bake the uploaded icons into the APK on every build.
//
// Downloads the icons that are currently saved in the backend database (the
// ones uploaded through the app's "Screen icons" screen) into
// src/main/assets/saved_icons/, where the packaging step embeds them into
// the APK. The app then uses those icons directly — even offline — and only
// falls back to the live server URLs or the bundled defaults when a baked
// icon is missing.
//
// Configure in ../gradle.properties:
//   appIconServer=http://10.0.2.2:8000   (required to enable the task)
//   appIconPhone=03001234567             (optional: also bakes per-business icons)
//   appIconPassword=...                  (optional, used together with appIconPhone)
//
// If appIconServer is empty the task is skipped; if the server is unreachable
// the task only warns and the build continues with the previously baked
// icons. Never a build breaker on its own.
// ---------------------------------------------------------------------------
val fetchAppIcons by tasks.registering {
    group = "ahsan"
    description = "Fetch the icons saved in the backend database and bake them into assets/saved_icons."
    // appIconServer is an optional override; by default the explicitly
    // configured backend (APP_SERVER_URL / appServerUrl) is used. When nothing
    // is configured the task is skipped (onlyIf below).
    val server = ((project.findProperty("appIconServer") as String?) ?: "").trim().removeSuffix("/")
        .ifBlank { explicitServerUrl }
    val phone = ((project.findProperty("appIconPhone") as String?) ?: "").trim()
    val password = ((project.findProperty("appIconPassword") as String?) ?: "").trim()
    val outDir = layout.projectDirectory.dir("src/main/assets/saved_icons")
    onlyIf { server.isNotBlank() } // skipped when no server is configured

    doLast {
        try {
            val client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build()
            val slurper = groovy.json.JsonSlurper()
            val dir = outDir.asFile
            dir.mkdirs()

            fun httpGet(url: String, bearer: String? = null): Pair<ByteArray, String> {
                val request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("User-Agent", "ahsan-admin-icon-bake/1.0")
                    .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
                    .GET()
                    .build()
                val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                if (response.statusCode() !in 200..299) throw java.io.IOException("HTTP ${response.statusCode()} for $url")
                return response.body() to response.headers().firstValue("Content-Type").orElse("")
            }
            fun resolve(url: String): String =
                if (url.startsWith("http://") || url.startsWith("https://")) url else "$server/${url.removePrefix("/")}"
            fun extFor(contentType: String, url: String): String {
                val ct = contentType.substringBefore(';').trim().lowercase()
                return when {
                    "png" in ct -> "png"
                    "jpeg" in ct || "jpg" in ct -> "jpg"
                    "webp" in ct -> "webp"
                    "gif" in ct -> "gif"
                    "icon" in ct || "ico" in ct -> "ico"
                    else -> {
                        val fromUrl = url.substringAfterLast('/').substringBefore('?').substringAfterLast('.', "")
                        if (fromUrl.length in 1..5 && fromUrl.all { it.isLetterOrDigit() }) fromUrl.lowercase() else "png"
                    }
                }
            }
            fun jsonEscape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
            val written = linkedMapOf<String, String>()
            fun bake(key: String, url: String) {
                val safeKey = key.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").ifBlank { "icon" }
                val (bytes, contentType) = httpGet(resolve(url))
                if (bytes.isEmpty()) return
                val file = dir.newFile("$safeKey.${extFor(contentType, url)}")
                file.writeBytes(bytes)
                written[safeKey] = file.name
            }

            // 1) App icon slots — public endpoint, no credentials needed.
            val (mobileBody, _) = httpGet("$server/api/v1/mobile/icons")
            val mobile = slurper.parseText(mobileBody.toString(Charsets.UTF_8)) as? Map<*, *> ?: emptyMap<Any, Any>()
            val slotIcons = mobile["icons"] as? Map<*, *> ?: emptyMap<Any, Any>()
            for ((key, value) in slotIcons) {
                val url = ((value as? Map<*, *>)?.get("image_url")?.toString() ?: "").trim()
                if (url.isNotBlank()) bake(key.toString(), url)
            }

            // 2) Per-business icons — needs the admin businesses list (JWT).
            var token: String? = null
            if (phone.isNotBlank() && password.isNotBlank()) {
                val loginRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create("$server/api/v1/auth/login"))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        """{"phone":"${jsonEscape(phone)}","password":"${jsonEscape(password)}"}"""
                    ))
                    .build()
                val loginResponse = client.send(loginRequest, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                token = if (loginResponse.statusCode() in 200..299) {
                    (slurper.parseText(loginResponse.body().toString(Charsets.UTF_8)) as? Map<*, *>)?.get("access_token")?.toString()
                } else {
                    logger.warn("fetchAppIcons: login to $server failed (HTTP ${loginResponse.statusCode()}); per-business icons skipped.")
                    null
                }
            }
            if (token != null) {
                val (businessesBody, _) = httpGet("$server/api/v1/admin/businesses", token)
                val businesses = slurper.parseText(businessesBody.toString(Charsets.UTF_8)) as? List<*> ?: emptyList<Any>()
                businesses.forEach { item ->
                    val business = item as? Map<*, *> ?: return@forEach
                    val type = (business["type"]?.toString() ?: "").trim()
                    val url = (business["icon_url"]?.toString() ?: "").trim()
                    if (type.isNotBlank() && url.isNotBlank()) bake("business_${type.lowercase()}", url)
                }
            } else if (phone.isBlank() || password.isBlank()) {
                logger.lifecycle("fetchAppIcons: appIconPhone/appIconPassword not set — slot icons are baked, per-business icons need credentials.")
            }

            // 3) Drop stale baked icons and record what this build contains.
            dir.listFiles()?.forEach { f ->
                if (f.name != "manifest.json" && f.name.substringBeforeLast('.') !in written.keys) f.delete()
            }
            dir.newFile("manifest.json").writeText(
                groovy.json.JsonOutput.toJson(
                    mapOf(
                        "server" to server,
                        "fetched_at" to java.time.Instant.now().toString(),
                        "icons" to written.toSortedMap()
                    )
                )
            )
            logger.lifecycle("fetchAppIcons: baked ${written.size} icon(s) from $server into assets/saved_icons.")
        } catch (e: Exception) {
            logger.warn("fetchAppIcons: could not bake icons from $server (${e.message}); the build continues with the previously baked icons.")
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchAppIcons) }

dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
