package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
}

class DoodleOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    
    private var canvasView: ComposeView? = null
    private var controlView: ComposeView? = null
    
    private lateinit var canvasLifecycleOwner: ServiceLifecycleOwner
    private lateinit var controlLifecycleOwner: ServiceLifecycleOwner

    private lateinit var canvasParams: WindowManager.LayoutParams
    private lateinit var controlParams: WindowManager.LayoutParams

    // Track position coordinates for draggable floating pill
    private var controlX = 100
    private var controlY = 200

    companion object {
        private const val CHANNEL_ID = "DoodleOverlayChannel"
        private const val NOTIFICATION_ID = 8877
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        DoodleState.isServiceRunning.value = true

        canvasLifecycleOwner = ServiceLifecycleOwner().apply { start() }
        controlLifecycleOwner = ServiceLifecycleOwner().apply { start() }

        showCanvasOverlay()
        showControlOverlay()
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "S Pen Doodle Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active transparent doodling pad stream"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("S Pen Doodle Active")
            .setContentText("Tap to configure S Pen drawing trails.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCanvasOverlay() {
        canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(canvasLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(canvasLifecycleOwner)
            setViewTreeViewModelStoreOwner(canvasLifecycleOwner)
            
            setContent {
                val isTouchThrough by DoodleState.isTouchThrough.collectAsState()
                
                // Dynamically adjust parameters based on touch through mode
                LaunchedEffect(isTouchThrough) {
                    updateCanvasTouchState(isTouchThrough)
                }

                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxSize()
                ) {
                    OverlayDrawingCanvas()
                }
            }
        }

        canvasView = view
        windowManager.addView(view, canvasParams)
    }

    private fun updateCanvasTouchState(touchThrough: Boolean) {
        val currentCanvasView = canvasView ?: return
        if (touchThrough) {
            canvasParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            canvasParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        try {
            windowManager.updateViewLayout(currentCanvasView, canvasParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showControlOverlay() {
        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = controlX
            y = controlY
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(controlLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(controlLifecycleOwner)
            setViewTreeViewModelStoreOwner(controlLifecycleOwner)
            
            setContent {
                FloatingControlUI(
                    onDrag = { dx, dy ->
                        controlX += dx.roundToInt()
                        controlY += dy.roundToInt()
                        controlParams.x = controlX
                        controlParams.y = controlY
                        try {
                            windowManager.updateViewLayout(this@apply, controlParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onClose = {
                        stopSelf()
                    }
                )
            }
        }

        controlView = view
        windowManager.addView(view, controlParams)
    }

    override fun onDestroy() {
        DoodleState.isServiceRunning.value = false
        
        canvasView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }
        controlView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }

        canvasLifecycleOwner.stop()
        controlLifecycleOwner.stop()
        
        super.onDestroy()
    }
}

@Composable
fun OverlayDrawingCanvas() {
    val paths = DoodleState.activePaths
    val brushColor by DoodleState.brushColor.collectAsState()
    val brushWidth by DoodleState.brushWidth.collectAsState()
    val fadeTimeSeconds by DoodleState.fadeTimeSeconds.collectAsState()
    val sPenOnlyMode by DoodleState.sPenOnlyMode.collectAsState()
    val isTouchThrough by DoodleState.isTouchThrough.collectAsState()

    val density = LocalDensity.current
    val brushWidthPx = remember(brushWidth) { with(density) { brushWidth.dp.toPx() } }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Redraw loop to animate fading paths beautifully (60 FPS)
    LaunchedEffect(paths.size, fadeTimeSeconds) {
        if (fadeTimeSeconds != Float.MAX_VALUE) {
            while (true) {
                currentTime = System.currentTimeMillis()
                // Safely remove faded out paths to prevent piling up memory
                val threshold = (fadeTimeSeconds * 1000).toLong()
                val iterator = DoodleState.activePaths.iterator()
                while (iterator.hasNext()) {
                    val path = iterator.next()
                    if (currentTime - path.initialTimestamp >= threshold) {
                        iterator.remove()
                    }
                }
                delay(16) // ~60fps
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isTouchThrough, sPenOnlyMode) {
                if (isTouchThrough) return@pointerInput
                
                var currentPoints = mutableListOf<DoodlePoint>()
                
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Find the primary changes
                        val change = event.changes.firstOrNull() ?: continue
                        
                        // Handle Stylus constraint
                        val isStylus = change.type == PointerType.Stylus || change.type == PointerType.Eraser
                        if (sPenOnlyMode && !isStylus) {
                            // S Pen only is active, hand palm & other fingers are rejected!
                            continue
                        }

                        val pos = change.position
                        // Gracefully check pressure sensitivity
                        val pressure = try { change.pressure } catch (e: Exception) { 1.0f }

                        if (change.pressed) {
                            if (change.previousPressed.not()) {
                                // Down gesture: Start new trail path
                                currentPoints = mutableListOf(DoodlePoint(pos.x, pos.y, pressure))
                                DoodleState.addPath(currentPoints, brushColor, brushWidthPx)
                            } else {
                                // Move gesture: Flow path drawing
                                currentPoints.add(DoodlePoint(pos.x, pos.y, pressure))
                                if (DoodleState.activePaths.isNotEmpty()) {
                                    val lastIdx = DoodleState.activePaths.size - 1
                                    val lastPath = DoodleState.activePaths[lastIdx]
                                    DoodleState.activePaths[lastIdx] = lastPath.copy(
                                        points = lastPath.points + DoodlePoint(pos.x, pos.y, pressure)
                                    )
                                }
                            }
                            change.consume()
                        } else if (change.previousPressed) {
                            // Up gesture: Complete path
                            currentPoints = mutableListOf()
                            change.consume()
                        }
                    }
                }
            }
    ) {
        for (doodlePath in paths) {
            val ageSec = (currentTime - doodlePath.initialTimestamp) / 1000f
            val alpha = if (fadeTimeSeconds == Float.MAX_VALUE) {
                1.0f
            } else {
                (1.0f - (ageSec / fadeTimeSeconds)).coerceIn(0f, 1f)
            }

            if (alpha > 0f) {
                val pts = doodlePath.points
                if (pts.isEmpty()) continue

                // High visual premium design: render pressure-sensitive segments for S Pen
                if (pts.size == 1) {
                    val p = pts.first()
                    drawCircle(
                        color = doodlePath.color,
                        radius = doodlePath.strokeWidth / 2f,
                        center = Offset(p.x, p.y),
                        alpha = alpha
                    )
                } else {
                    for (i in 0 until pts.size - 1) {
                        val p1 = pts[i]
                        val p2 = pts[i + 1]

                        // Line thickness modulated by physical pen pressure dynamically!
                        val avgPressure = (p1.pressure + p2.pressure) / 2f
                        val segmentWidth = doodlePath.strokeWidth * (0.3f + avgPressure * 1.4f)

                        drawLine(
                            color = doodlePath.color,
                            start = Offset(p1.x, p1.y),
                            end = Offset(p2.x, p2.y),
                            strokeWidth = segmentWidth,
                            cap = StrokeCap.Round,
                            alpha = alpha
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingControlUI(
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    var showColorMenu by remember { mutableStateOf(false) }
    var showWidthMenu by remember { mutableStateOf(false) }
    var showFadeMenu by remember { mutableStateOf(false) }

    val brushWidth by DoodleState.brushWidth.collectAsState()
    val brushColor by DoodleState.brushColor.collectAsState()
    val fadeTimeSeconds by DoodleState.fadeTimeSeconds.collectAsState()
    val isTouchThrough by DoodleState.isTouchThrough.collectAsState()
    val sPenOnlyMode by DoodleState.sPenOnlyMode.collectAsState()
    
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF090D16).copy(alpha = 0.75f), // Authentic slate-900/60 frosted backing
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
            .padding(2.dp)
    ) {
        if (!isExpanded) {
            // Minimized Bubble Layout: highly discreet while watching screens
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(6.dp)
            ) {
                IconButton(
                    onClick = { isExpanded = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "Expand controls",
                        tint = brushColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            // Expanded Control panel with premium pill layout
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Draggable bar grip
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape)
                        .padding(bottom = 6.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Pen Mode / Watch-through Mode Toggle button with distinct glowing indicator
                    FilledIconToggleButton(
                        checked = isTouchThrough,
                        onCheckedChange = { DoodleState.isTouchThrough.value = it },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White.copy(alpha = 0.8f),
                            checkedContainerColor = Color(0xFFF472B6), // Gorgeous frosted Pink
                            checkedContentColor = Color(0xFF020617)
                        )
                    ) {
                        Icon(
                            imageVector = if (isTouchThrough) Icons.Default.PlayArrow else Icons.Default.Edit,
                            contentDescription = "Toggle modes",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Brush Color Selector Button
                    IconButton(
                        onClick = {
                            showColorMenu = !showColorMenu
                            showWidthMenu = false
                            showFadeMenu = false
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(brushColor, CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    }

                    // Stroke Size Button
                    IconButton(
                        onClick = {
                            showWidthMenu = !showWidthMenu
                            showColorMenu = false
                            showFadeMenu = false
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Brush width options",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Trail Fades Button
                    IconButton(
                        onClick = {
                            showFadeMenu = !showFadeMenu
                            showColorMenu = false
                            showWidthMenu = false
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Fade timing options",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // S Pen Only Mode Toggle
                    FilledIconToggleButton(
                        checked = sPenOnlyMode,
                        onCheckedChange = { DoodleState.sPenOnlyMode.value = it },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White.copy(alpha = 0.8f),
                            checkedContainerColor = Color(0xFF818CF8), // Elegant frosted Indigo
                            checkedContentColor = Color(0xFF020617)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "S Pen Only Mode",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Clear Canvas
                    IconButton(
                        onClick = { DoodleState.clear() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear board",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Minimize
                    IconButton(
                        onClick = { isExpanded = false },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Minimize menu",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Close service
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFF3B30).copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay tracker",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Dynamic Menus shown below the buttons bar when activated
                AnimatedVisibility(visible = showColorMenu) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Brush Colors",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            DoodleState.colorPalette.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (brushColor == color) 2.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            DoodleState.brushColor.value = color
                                            showColorMenu = false
                                        }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showWidthMenu) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(180.dp)) {
                        Text(
                            text = "Width: ${brushWidth.roundToInt()}dp",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Slider(
                            value = brushWidth,
                            onValueChange = { DoodleState.brushWidth.value = it },
                            valueRange = 2f..24f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = brushColor,
                                activeTrackColor = brushColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                AnimatedVisibility(visible = showFadeMenu) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Fade Out Trail Delays",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            DoodleState.fadeOptions.forEach { opt ->
                                val optionLabel = opt.first
                                val delayValue = opt.second
                                val isSelected = fadeTimeSeconds == delayValue
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) brushColor else Color.White.copy(alpha = 0.08f)
                                        )
                                        .clickable {
                                            DoodleState.fadeTimeSeconds.value = delayValue
                                            showFadeMenu = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = optionLabel,
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Small helpful Mode label
                Text(
                    text = if (isTouchThrough) "Watch Mode (Click-Through)" else "Pen Mode (Draw trails)",
                    fontSize = 10.sp,
                    color = if (isTouchThrough) Color(0xFFF472B6) else Color(0xFF818CF8),
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (sPenOnlyMode) {
                    Text(
                        text = "S Pen Palm Reject Active",
                        fontSize = 9.sp,
                        color = Color(0xFF818CF8),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
