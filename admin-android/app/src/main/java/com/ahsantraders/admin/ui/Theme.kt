package com.ahsantraders.admin.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val Forest = BrandGreen
val Green = Color(0xFF078448)
val Gold = BrandGold
val Mint = BrandMint
val Paper = BrandPaper
val Ink = BrandInk
val Muted = BrandMuted
val Chicken = ChickenRed
val Lpg = LpgBlue
val Broiler = BroilerGreen
val LocalLanguage = staticCompositionLocalOf { "en" }

@Composable
fun AhsanTheme(language: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLanguage provides language, LocalLayoutDirection provides if (language == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White,
            secondary = Green, tertiary = Gold, background = Paper, surface = Color.White,
            onSurface = Ink, onBackground = Ink, surfaceVariant = Mint, outline = Color(0xFFCAD9D0)), content = content)
    }
}
private val urdu = mapOf(
    "Home" to "ہوم", "Dashboard" to "ڈیش بورڈ", "Sales" to "فروخت", "Expenses" to "اخراجات",
    "Reports" to "رپورٹس", "More" to "مزید", "Settings" to "ترتیبات", "Profile" to "پروفائل",
    "Chicken Shop" to "چکن شاپ", "LPG Business" to "ایل پی جی کاروبار", "Broiler Farming" to "برائلر فارمنگ",
    "LPG / Gas Business" to "ایل پی جی / گیس کاروبار", "Poultry Farm (Broiler)" to "پولٹری فارم (برائلر)",
    "Today’s overview" to "آج کا خلاصہ", "Total sales" to "کل فروخت", "Net profit" to "خالص منافع",
    "Your businesses" to "آپ کے کاروبار", "Quick actions" to "فوری اقدامات", "Add sale" to "فروخت شامل کریں",
    "Add expense" to "اخراجات شامل کریں", "Add purchase" to "خریداری شامل کریں", "Stock" to "اسٹاک",
    "Suppliers" to "سپلائرز", "Supplier bills" to "سپلائر بل", "Language" to "زبان",
    "Sign in" to "سائن ان", "Sign out" to "سائن آؤٹ", "Phone number" to "فون نمبر",
    "Password" to "پاس ورڈ", "Server address" to "سرور کا پتہ", "Save record" to "ریکارڈ محفوظ کریں",
    "Save changes" to "تبدیلیاں محفوظ کریں", "Cancel" to "منسوخ", "Confirm" to "تصدیق", "Back" to "واپس",
    "Refresh" to "تازہ کریں", "Try again" to "دوبارہ کوشش کریں", "Close day" to "دن بند کریں",
    "Transaction history" to "لین دین کی تاریخ", "Settlement history" to "تقسیم کی تاریخ",
    "Today’s summary" to "آج کا خلاصہ", "Revenue" to "آمدن", "Cost of sales" to "فروخت کی لاگت",
    "Operating expenses" to "کاروباری اخراجات", "Profit" to "منافع", "Purchased" to "خریداری",
    "Sold" to "فروخت شدہ", "Remaining stock" to "باقی اسٹاک", "Inventory value" to "اسٹاک کی قیمت",
    "Live birds" to "زندہ پرندے", "Feed consumed" to "استعمال شدہ خوراک", "Mortality" to "اموات",
    "Pota-Kaliji sale" to "پوٹا کلیجی کی فروخت", "Retail sales" to "پرچون فروخت", "Commercial sales" to "تجارتی فروخت",
    "Batches" to "بیچز", "Create batch" to "بیچ بنائیں", "Start batch" to "بیچ شروع کریں",
    "Add daily record" to "روزانہ ریکارڈ شامل کریں", "Harvest batch" to "بیچ مکمل کریں",
    "Batch history" to "بیچ کی تاریخ", "Funding" to "سرمایہ کاری", "Active" to "فعال", "Harvested" to "مکمل",
    "Open" to "کھلا", "Closed" to "بند", "No records yet" to "ابھی کوئی ریکارڈ نہیں",
    "Load more" to "مزید دکھائیں", "Choose a business" to "کاروبار منتخب کریں", "All businesses" to "تمام کاروبار",
    "Daily" to "روزانہ", "Weekly" to "ہفتہ وار", "Monthly" to "ماہانہ", "Apply dates" to "تاریخیں لاگو کریں",
    "From" to "سے", "To" to "تک", "Business-wise profit" to "کاروبار کے لحاظ سے منافع",
    "Business summary" to "کاروباری خلاصہ", "Total costs & expenses" to "کل لاگت اور اخراجات",
    "Investor distribution" to "سرمایہ کاروں میں تقسیم", "Retained" to "برقرار رقم",
    "Edit profile" to "پروفائل تبدیل کریں", "Change password" to "پاس ورڈ تبدیل کریں", "About this app" to "ایپ کے بارے میں",
    "Name" to "نام", "Supplier name" to "سپلائر کا نام", "Add supplier" to "سپلائر شامل کریں",
    "Date" to "تاریخ", "Amount (Rs.)" to "رقم (روپے)", "Quantity (kg)" to "مقدار (کلوگرام)",
    "Cylinders" to "سلنڈر", "Note" to "نوٹ", "Sale channel" to "فروخت کا شعبہ", "Retail" to "پرچون",
    "Commercial" to "تجارتی", "Supplier (optional)" to "سپلائر (اختیاری)", "None" to "کوئی نہیں",
    "Batch name" to "بیچ کا نام", "Chicks" to "چوزے", "Total shares" to "کل حصص",
    "Share price (Rs.)" to "فی حصہ قیمت (روپے)", "Initial cost (Rs.)" to "ابتدائی لاگت (روپے)",
    "Feed (kg)" to "خوراک (کلوگرام)", "Deaths" to "اموات", "Expense (Rs.)" to "اخراجات (روپے)",
    "Yield (kg)" to "کل وزن (کلوگرام)", "Sale price per kg (Rs.)" to "فی کلو فروخت قیمت (روپے)",
    "Additional expense (Rs.)" to "اضافی اخراجات (روپے)", "Current password" to "موجودہ پاس ورڈ",
    "New password" to "نیا پاس ورڈ", "Confirm password" to "پاس ورڈ کی تصدیق", "Dismiss" to "بند کریں",
    "Users" to "صارفین", "Screen icons" to "اسکرین آئیکنز", "Add user" to "صارف شامل کریں", "Search users" to "صارفین تلاش کریں",
    "Investors" to "سرمایہ کار", "Managers" to "مینیجرز", "Super admin" to "سپر ایڈمن", "Verify KYC" to "کے وائی سی تصدیق کریں",
    "Verify" to "تصدیق کریں", "Business access" to "کاروبار تک رسائی", "Create manager" to "مینیجر بنائیں", "Create investor" to "سرمایہ کار بنائیں",
    "Super admin actions" to "سپر ایڈمن کارروائیاں", "Add image" to "تصویر شامل کریں", "Change" to "تبدیل کریں", "Save" to "محفوظ کریں",
    "Details" to "تفصیلات", "Users & access" to "صارفین اور رسائی", "User details" to "صارف کی تفصیلات", "Set image for" to "تصویر منتخب کریں"
)
@Composable fun tr(text: String): String = if (LocalLanguage.current == "ur") urdu[text] ?: text else text
fun sectorName(type: String) = when (type) { "CHICKEN" -> "Chicken Shop"; "LPG" -> "LPG / Gas Business"; else -> "Poultry Farm (Broiler)" }
fun sectorColor(type: String) = when (type) { "CHICKEN" -> Chicken; "LPG" -> Lpg; else -> Broiler }
