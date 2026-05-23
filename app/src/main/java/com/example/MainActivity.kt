package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if overlay permission is granted
        if (Settings.canDrawOverlays(this)) {
            launchDoodleService()
            safelyFinish()
            return
        }
        
        // Otherwise, show the permission request screen
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFFF8FAFC) // Clean elegant light Slate background
                ) { innerPadding ->
                    OverlayPermissionGateway(
                         modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        onPermissionGranted = {
                            launchDoodleService()
                            safelyFinish()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If they returned from settings and permission is granted, start and finish!
        if (Settings.canDrawOverlays(this)) {
            launchDoodleService()
            safelyFinish()
        }
    }

    private fun safelyFinish() {
        // Run on the next main handler loop so the window is fully attached,
        // allowing the system to perform clean input channel disposal.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (!isFinishing) {
                finish()
                overridePendingTransition(0, 0) // Eliminate task transition flicker
            }
        }
    }

    private fun launchDoodleService() {
        val serviceIntent = Intent(this, DoodleOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

@Composable
fun OverlayPermissionGateway(
    modifier: Modifier = Modifier,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            onPermissionGranted()
        }
    }

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF1F5F9) // Subtle premium ultra-light Slate shading
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(28.dp))
                .shadow(12.dp, RoundedCornerShape(28.dp), clip = false)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Realtime Canvas drawn gradient doodle curve - matching the app icon and aesthetic!
                Canvas(modifier = Modifier.size(100.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.30f, size.height * 0.42f)
                        cubicTo(size.width * 0.30f, size.height * 0.30f, size.width * 0.45f, size.height * 0.26f, size.width * 0.50f, size.height * 0.34f)
                        cubicTo(size.width * 0.55f, size.height * 0.42f, size.width * 0.45f, size.height * 0.52f, size.width * 0.50f, size.height * 0.62f)
                        cubicTo(size.width * 0.55f, size.height * 0.72f, size.width * 0.70f, size.height * 0.64f, size.width * 0.70f, size.height * 0.51f)
                        cubicTo(size.width * 0.70f, size.height * 0.40f, size.width * 0.63f, size.height * 0.36f, size.width * 0.55f, size.height * 0.38f)
                        cubicTo(size.width * 0.48f, size.height * 0.40f, size.width * 0.44f, size.height * 0.48f, size.width * 0.40f, size.height * 0.52f)
                        cubicTo(size.width * 0.36f, size.height * 0.56f, size.width * 0.32f, size.height * 0.64f, size.width * 0.38f, size.height * 0.70f)
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899))
                        ),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                
                Spacer(modifier = Modifier.height(18.dp))
                
                Text(
                    text = "Screen Doodle",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A), // Dark slate-900
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "Drawing trails on your screen has never been more fluid. Draw, annotate, or loop custom fading S Pen trails over videos and reading material seamlessly.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569), // Muted slate-600
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                
                Spacer(modifier = Modifier.height(28.dp))
                
                // Gradient CTA Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899))
                            )
                        )
                        .clickable {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Unlock",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Overlay & Launch",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
