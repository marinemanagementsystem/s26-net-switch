package com.s26ultra.netswitch

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import rikka.shizuku.Shizuku

private val OneUiDarkPalette = darkColorScheme(
    primary = Color(0xFF3B82F6),
    secondary = Color(0xFF10B981),
    background = Color(0xFF0B0F19),
    surface = Color(0xFF111827),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF9FAFB),
    onSurface = Color(0xFFF3F4F6)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = OneUiDarkPalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NetSwitchHomeScreen()
                }
            }
        }
    }
}

@Composable
fun NetSwitchHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var refreshTick by remember { mutableIntStateOf(0) }
    var hasPhonePerm by remember { mutableStateOf(SimNetworkHelper.hasPhonePermission(context)) }
    var isAccessibilityOn by remember {
        mutableStateOf(SimNetworkHelper.isAccessibilityServiceEnabled(context))
    }
    var hasShizukuPerm by remember {
        mutableStateOf(DirectModemSwitcher.hasShizukuPermission())
    }
    var simSelection by remember {
        mutableStateOf(SwitchPreferences.getSimSelectionMode(context))
    }
    var autoReturnHome by remember {
        mutableStateOf(SwitchPreferences.isAutoReturnHomeEnabled(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPhonePerm = granted
        refreshTick++
        NetSwitchWidgetProvider.updateAllWidgets(context)
    }

    DisposableEffect(Unit) {
        val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            hasShizukuPerm = (grantResult == PackageManager.PERMISSION_GRANTED)
            refreshTick++
        }
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuListener) }
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPhonePerm = SimNetworkHelper.hasPhonePermission(context)
                isAccessibilityOn = SimNetworkHelper.isAccessibilityServiceEnabled(context)
                hasShizukuPerm = DirectModemSwitcher.hasShizukuPermission()
                simSelection = SwitchPreferences.getSimSelectionMode(context)
                autoReturnHome = SwitchPreferences.isAutoReturnHomeEnabled(context)
                refreshTick++
                NetSwitchWidgetProvider.updateAllWidgets(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val simStatuses = remember(refreshTick, hasPhonePerm) {
        SimNetworkHelper.getSimStatuses(context)
    }
    val resolvedTarget = remember(refreshTick, simSelection, hasPhonePerm) {
        SimNetworkHelper.resolveTargetSim(context, simSelection)
    }
    val lastLog = remember(refreshTick) {
        SwitchPreferences.getLastLog(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "S26 Ultra • 5G / 4.5G Switch",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Çift SIM Destekli Hızlı Şebeke Modu Widget & Otomasyonu",
                    fontSize = 13.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        // Canlı Hedef Kartı + Hızlı Geçiş Butonları
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (resolvedTarget.isFromAutoData) {
                                "AKTİF MOBİL VERİ HATTI (OTOMATİK)"
                            } else {
                                "SEÇİLİ HEDEF HAT"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF60A5FA)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = resolvedTarget.displayTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    val is5G = resolvedTarget.currentMode == NetworkModeTarget.MODE_5G
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (is5G) Color(0xFF2563EB) else Color(0xFF059669))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = resolvedTarget.currentMode.shortLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            SimNetworkHelper.triggerNetworkSwitch(context, NetworkModeTarget.MODE_5G)
                            refreshTick++
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        )
                    ) {
                        Text("5G Yap", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            SimNetworkHelper.triggerNetworkSwitch(
                                context,
                                NetworkModeTarget.MODE_4_5G
                            )
                            refreshTick++
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669)
                        )
                    ) {
                        Text("4.5G Yap", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        SimNetworkHelper.triggerNetworkSwitch(context, null)
                        refreshTick++
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "⇄ Tek Tıkla Değiştir (5G ↔ 4.5G)",
                        color = Color(0xFF93C5FD),
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Son işlem: $lastLog",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        // Çift SIM Seçimi Kartı
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Çift SIM Hedef Seçimi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                SimSelectionMode.entries.forEach { mode ->
                    val selected = simSelection == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color(0xFF1E3A8A) else Color(0xFF1F2937))
                            .clickable {
                                SwitchPreferences.setSimSelectionMode(context, mode)
                                simSelection = mode
                                refreshTick++
                                NetSwitchWidgetProvider.updateAllWidgets(context)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.chipLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = mode.description,
                                fontSize = 12.sp,
                                color = Color(0xFFD1D5DB)
                            )
                        }
                        Text(
                            text = if (selected) "✓ Seçili" else "Seç",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color(0xFF93C5FD) else Color(0xFF9CA3AF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    simStatuses.forEach { sim ->
                        val savedMode = SwitchPreferences.getSavedModeForSlot(context, sim.slotIndex)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1F2937))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "SIM ${sim.slotIndex + 1}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                if (sim.isDefaultData) {
                                    Text(
                                        text = "Aktif Veri",
                                        fontSize = 10.sp,
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = sim.carrierName,
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF)
                            )
                            Text(
                                text = "Mod: ${sim.liveNetworkBadge ?: savedMode.shortLabel}",
                                fontSize = 12.sp,
                                color = Color(0xFF93C5FD),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { SimNetworkHelper.launchSimManagerSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Samsung SIM Yöneticisini Aç (SIM 1 / SIM 2 Veri Geçişi)")
                }
            }
        }

        // Ana Ekrana Widget Ekleme Kartı
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Ana Ekrana Widget Ekle",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Aşağıdaki butonlarla widget'ı S26 Ultra ana ekranınıza tek dokunuşla sabitleyebilirsiniz.",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            pinWidget(
                                context,
                                ComponentName(context, NetSwitchWidgetProvider::class.java)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Geniş Widget (4x1)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            pinWidget(
                                context,
                                ComponentName(context, NetSwitchCompactWidgetProvider::class.java)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151))
                    ) {
                        Text("Kompakt (2x1)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Otomasyon & İzin Kurulum Kartı
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Kurulum & Geçiş Yöntemi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // 1. Telefon İzni
                SetupItemRow(
                    title = "1. Çift SIM & Aktif Veri Algılama İzni",
                    subtitle = if (hasPhonePerm) {
                        "Aktif mobil veri SIM'i otomatik algılanıyor"
                    } else {
                        "Hangi SIM'de mobil verinin açık olduğunu bilmek için gerekli"
                    },
                    isDone = hasPhonePerm,
                    actionText = if (hasPhonePerm) "Verildi" else "İzin Ver",
                    onClick = {
                        if (!hasPhonePerm) {
                            permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    }
                )

                // 2. Erişilebilirlik Servisi
                SetupItemRow(
                    title = "2. Otomatik 5G/4.5G Seçim Servisi (Erişilebilirlik)",
                    subtitle = if (isAccessibilityOn) {
                        "Açık: Ayar penceresini milisaniyede açıp 5G/4.5G seçip kapatır"
                    } else {
                        "Kapalı: Otomatik tıklama için etkinleştirin"
                    },
                    isDone = isAccessibilityOn,
                    actionText = if (isAccessibilityOn) "Açık" else "Etkinleştir",
                    onClick = {
                        SimNetworkHelper.openAccessibilitySettings(context)
                    }
                )

                // 3. Shizuku (Ayarlar ekranı hiç açılmadan arka planda sessiz geçiş)
                SetupItemRow(
                    title = "3. Sessiz Arka Plan Modu (Shizuku - İsteğe Bağlı)",
                    subtitle = if (hasShizukuPerm) {
                        "Aktif: Telefon ayarları HİÇ açılmadan doğrudan ana ekranda değişir!"
                    } else if (DirectModemSwitcher.isShizukuAvailable()) {
                        "Shizuku çalışıyor: İzin vererek ayar ekranı açılmadan değiştirebilirsiniz"
                    } else {
                        "Shizuku kuruluysa ayar ekranı hiç açılmadan arka planda değiştirir"
                    },
                    isDone = hasShizukuPerm,
                    actionText = if (hasShizukuPerm) "Aktif" else "Shizuku İzni",
                    onClick = {
                        if (DirectModemSwitcher.isShizukuAvailable()) {
                            DirectModemSwitcher.requestShizukuPermission()
                        } else {
                            Toast.makeText(
                                context,
                                "Play Store'dan 'Shizuku' uygulamasını başlatıp Kablosuz Hata Ayıklama ile eşleştirdiğinizde ekran açılmadan çalışır.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Seçimden Sonra Otomatik Ana Ekrana Dön",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Erişilebilirlik modunda 5G/4.5G seçildikten sonra pencereyi kapatır",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    Switch(
                        checked = autoReturnHome,
                        onCheckedChange = { checked ->
                            autoReturnHome = checked
                            SwitchPreferences.setAutoReturnHomeEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupItemRow(
    title: String,
    subtitle: String,
    isDone: Boolean,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1F2937))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isDone) Color(0xFF10B981) else Color(0xFFF59E0B))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDone) Color(0xFF065F46) else Color(0xFF2563EB)
            )
        ) {
            Text(actionText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun pinWidget(context: android.content.Context, provider: ComponentName) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        val pinned = appWidgetManager.requestPinAppWidget(provider, null, null)
        if (!pinned) {
            Toast.makeText(
                context,
                "Ana ekrana basılı tutup Widget'lar menüsünden ekleyebilirsiniz.",
                Toast.LENGTH_LONG
            ).show()
        }
    } else {
        Toast.makeText(
            context,
            "Ana ekrana basılı tutup Widget'lar menüsünden ekleyebilirsiniz.",
            Toast.LENGTH_LONG
        ).show()
    }
}
