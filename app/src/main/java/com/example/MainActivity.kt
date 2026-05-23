package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainUIContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainUIContainer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Settings/Logs, 1: In-App Cinema

    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020617), // slate-950 (Frosted Glass Dark Space)
                        Color(0xFF0A0F1D), // Deep dark indigo-slate
                        Color(0xFF1E1B4B)  // Dark Indigo glow
                    )
                )
            )
    ) {
        // App Header Unit
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "S Pen Doodle",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Seamless overlay & S Pen trails",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            
            // Frosted Ghost Status indicator badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF818CF8).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFF472B6), CircleShape) // Frosted Pink glowing dot
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ghost Active",
                        color = Color(0xFF818CF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Modern Tab selector (Frosted Glass Theme)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF818CF8),
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                selectedContentColor = Color(0xFF818CF8),
                unselectedContentColor = Color.White.copy(alpha = 0.5f),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Overlay control panel tab",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "System Overlay",
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                selectedContentColor = Color(0xFF818CF8),
                unselectedContentColor = Color.White.copy(alpha = 0.5f),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "In-App Cinema movie screen",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cinema Doodle",
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (selectedTab == 0) {
                SystemOverlayTab()
            } else {
                CinemaDoodleTab()
            }
        }
    }
}

@Composable
fun SystemOverlayTab() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isRunning by DoodleState.isServiceRunning.collectAsState()
    val brushColor by DoodleState.brushColor.collectAsState()
    val brushWidth by DoodleState.brushWidth.collectAsState()
    val fadeTimeSeconds by DoodleState.fadeTimeSeconds.collectAsState()
    val sPenOnlyMode by DoodleState.sPenOnlyMode.collectAsState()

    // Activity launcher for managing system alert window permission
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Overlay Permission Granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission Denied. Overlay won't show.", Toast.LENGTH_LONG).show()
        }
    }

    val startServiceAction = {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Grant background overlay draw permission first", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            val serviceIntent = Intent(context, DoodleOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    val stopServiceAction = {
        val serviceIntent = Intent(context, DoodleOverlayService::class.java)
        context.stopService(serviceIntent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overlay Status Controller Card (Frosted Glass Theme)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.03f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Overlay Controller",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isRunning) "Active drawing layer shown over other apps" else "Service is dormant",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Pulse status circle using Frosted Lavender/Pink
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (isRunning) Color(0xFF818CF8) else Color(0xFFFF4B72),
                                CircleShape
                            )
                            .shadow(
                                elevation = if (isRunning) 8.dp else 0.dp,
                                shape = CircleShape,
                                ambientColor = if (isRunning) Color(0xFF818CF8) else Color.Transparent,
                                spotColor = if (isRunning) Color(0xFF818CF8) else Color.Transparent
                            )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons styled as premium capsule pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { if (isRunning) stopServiceAction() else startServiceAction() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFF472B6) else Color(0xFF818CF8),
                            contentColor = Color(0xFF020617)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("service_toggle_button"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "Service Trigger"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "Stop Overlay" else "Start Overlay",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .testTag("permission_button"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Grant Draw Permission"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "App Draw Setup", fontSize = 13.sp)
                    }
                }
            }
        }

        // Live Drawing Parameters Card (Frosted Glass Theme)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.03f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Doodle Customizations",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Changes reflect instantly on system streams in real-time.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Brush Stroke width control
                Text(
                    text = "Stroke Width: ${brushWidth.roundToInt()}dp",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Slider(
                    value = brushWidth,
                    onValueChange = { DoodleState.brushWidth.value = it },
                    valueRange = 2f..24f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = brushColor,
                        activeTrackColor = brushColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Color palette row
                Text(
                    text = "Trail Color Theme",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DoodleState.colorPalette.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (brushColor == color) 2.5.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                               )
                                .clickable {
                                    DoodleState.brushColor.value = color
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Time delays dropdown/row selection
                Text(
                    text = "Decay/Fade Timeout Delay",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DoodleState.fadeOptions.forEach { opt ->
                        val label = opt.first
                        val delayVal = opt.second
                        val isSelected = fadeTimeSeconds == delayVal

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) brushColor else Color.White.copy(alpha = 0.08f)
                                )
                                .clickable {
                                    DoodleState.fadeTimeSeconds.value = delayVal
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // S Pen Palm rejection feature toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "S Pen Only Mode (Palm Rejection)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Ignores drawing triggers by hand parts/fingers to only accept clean physical stylus coordinate input trails.",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = sPenOnlyMode,
                        onCheckedChange = { DoodleState.sPenOnlyMode.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF818CF8),
                            checkedTrackColor = Color(0xFF818CF8).copy(alpha = 0.35f),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Instructions/Guide help Card (Frosted Glass Theme)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.02f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(28.dp))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Quick S Pen Overlay Manual",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))

                DoodleManualItem(
                    index = "1",
                    text = "Launch Overlay Service. Click 'App Draw Setup' to grant system layer rights first."
                )
                DoodleManualItem(
                    index = "2",
                    text = "A small control bar pill appears. Drag it by sliding your finger or physical pen anywhere on the pill background."
                )
                DoodleManualItem(
                    index = "3",
                    text = "Under 'Pen Mode' (glowing green/blue), draw trails anywhere. Physical S Pen pressure naturally modulates stroke line thickness!"
                )
                DoodleManualItem(
                    index = "4",
                    text = "Toggle the 'Watch Mode / Play' button (orange) to interact with background videos (play/pause/scrub), then toggle back to draw."
                )
                DoodleManualItem(
                    index = "5",
                    text = "Set timeout delays like '2s' or '4s' so drawing trails disappear magically after writing while watching."
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun DoodleManualItem(index: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(Color(0xFF818CF8).copy(alpha = 0.15f), CircleShape)
                .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF818CF8)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CinemaDoodleTab() {
    var videoUrl by remember { mutableStateOf("https://www.youtube.com/embed/ScMzIvxBSi4") } // Default relaxing study view
    var isEditingUrl by remember { mutableStateOf(false) }
    var currentUrlToLoad by remember { mutableStateOf(videoUrl) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Video URL Input Card (Frosted Glass Theme)
            Card(
                shape = RoundedCornerShape(0.dp, 0.dp, 24.dp, 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.04f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(0.dp, 0.dp, 24.dp, 24.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = videoUrl,
                            onValueChange = { videoUrl = it },
                            label = { Text("Stream/Video or Web URL", color = Color.White.copy(alpha = 0.5f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF818CF8),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedLabelColor = Color(0xFF818CF8),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                currentUrlToLoad = videoUrl
                                isEditingUrl = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF818CF8),
                                contentColor = Color(0xFF020617)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Load", fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Tip: Paste video stream links (MP4, YouTube Embeds, series streams) to doodle directly over them inside this app's Cinema sandbox!",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }

            // Interactive player + Drawing overlay container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                // Interactive safe Web Stream View
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                        }
                    },
                    update = { web ->
                        // Only load when URL actually changes
                        if (web.url != currentUrlToLoad) {
                            web.loadUrl(currentUrlToLoad)
                        }
                    }
                )

                // The Premium S Pen Drawing Overlay layer matching user parameters
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxSize()
                ) {
                    OverlayDrawingCanvas()
                }

                // Small quick indicators on standard drawing frame
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Small floating "Clear trails" button
                    FloatingActionButton(
                        onClick = { DoodleState.clear() },
                        containerColor = Color(0xFF131326).copy(alpha = 0.85f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear in-app doodles",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Floating color check indicator
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131326).copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val activeColor by DoodleState.brushColor.collectAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(activeColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
