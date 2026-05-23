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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                // Fallback: Try to start without type to prevent app crash under strict OS environments
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        // Initialize and load saved user settings from SharedPreferences
        DoodleState.init(this)

        DoodleState.isServiceRunning.value = true

        canvasLifecycleOwner = ServiceLifecycleOwner().apply { start() }
        controlLifecycleOwner = ServiceLifecycleOwner().apply { start() }

        showCanvasOverlay()
        showControlOverlay()

        // Spin up observers to write changes to local storage reactively
        serviceScope.launch {
            DoodleState.brushWidth.collect {
                DoodleState.save(this@DoodleOverlayService, "brush_width", it)
            }
        }
        serviceScope.launch {
            DoodleState.brushColor.collect { color ->
                val argb = ((color.alpha * 255.0f + 0.5f).toInt() shl 24) or
                           ((color.red * 255.0f + 0.5f).toInt() shl 16) or
                           ((color.green * 255.0f + 0.5f).toInt() shl 8) or
                           (color.blue * 255.0f + 0.5f).toInt()
                DoodleState.save(this@DoodleOverlayService, "brush_color", argb)
            }
        }
        serviceScope.launch {
            DoodleState.fadeTimeSeconds.collect {
                DoodleState.save(this@DoodleOverlayService, "fade_time_seconds", it)
            }
        }
        serviceScope.launch {
            DoodleState.isTouchThrough.collect {
                DoodleState.save(this@DoodleOverlayService, "is_touch_through", it)
            }
        }
        serviceScope.launch {
            DoodleState.customColorHue.collect {
                DoodleState.save(this@DoodleOverlayService, "custom_color_hue", it)
            }
        }
        serviceScope.launch {
            DoodleState.customColorSat.collect {
                DoodleState.save(this@DoodleOverlayService, "custom_color_sat", it)
            }
        }
        serviceScope.launch {
            DoodleState.customColorVal.collect {
                DoodleState.save(this@DoodleOverlayService, "custom_color_val", it)
            }
        }
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
                        
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels
                        controlX = controlX.coerceIn(0, screenWidth - 100)
                        controlY = controlY.coerceIn(0, screenHeight - 150)
                        
                        controlParams.x = controlX
                        controlParams.y = controlY
                        try {
                            windowManager.updateViewLayout(this@apply, controlParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onExpandChanged = { expanded ->
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val density = displayMetrics.density
                        val minimizedWidth = 48 * density
                        val expandedWidth = 180 * density
                        
                        if (controlX > screenWidth / 2) {
                            val diff = (expandedWidth - minimizedWidth).toInt()
                            if (expanded) {
                                controlX = (controlX - diff).coerceAtLeast(0)
                            } else {
                                controlX = (controlX + diff).coerceAtMost(screenWidth - minimizedWidth.toInt())
                            }
                            controlParams.x = controlX
                            try {
                                windowManager.updateViewLayout(controlView, controlParams)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onColorMenuToggled = { showMenu ->
                        if (showMenu) {
                            val displayMetrics = resources.displayMetrics
                            val screenHeight = displayMetrics.heightPixels
                            val density = displayMetrics.density
                            val fullHeight = 270 * density
                            if (controlY + fullHeight > screenHeight) {
                                controlY = (screenHeight - fullHeight).toInt().coerceAtLeast(0)
                                controlParams.y = controlY
                                try {
                                    windowManager.updateViewLayout(controlView, controlParams)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
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
        serviceScope.cancel()
        
        try {
            stopForeground(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
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
    val sPenOnlyMode by DoodleState.sPenOnlyMode.collectAsState()
    val isTouchThrough by DoodleState.isTouchThrough.collectAsState()
    val isEraserMode by DoodleState.isEraserMode.collectAsState()

    val density = LocalDensity.current
    // Dynamically calculate brush width in pixels based on user's selected size
    val brushWidthPx = with(density) { brushWidth.dp.toPx() }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Redraw loop that drives smooth 60fps trail fading and manages expired paths memory cleanup
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            
            // Clean up fully expired paths (paths where the newest/last point is older than 3 seconds)
            val expiredPaths = DoodleState.activePaths.filter { path ->
                val lastPoint = path.points.lastOrNull()
                lastPoint != null && (currentTime - lastPoint.timestamp >= 3000L)
            }
            
            if (expiredPaths.isNotEmpty()) {
                DoodleState.activePaths.removeAll(expiredPaths)
            }
            
            delay(16) // Solid 60 FPS driving frame updates
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) // Crucial for BlendMode.Clear to erase
            .pointerInput(isTouchThrough, sPenOnlyMode, isEraserMode) {
                if (isTouchThrough) return@pointerInput
                
                val activePointers = mutableMapOf<PointerId, Long>()
                val pointerPoints = mutableMapOf<PointerId, MutableList<DoodlePoint>>()
                
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        
                        event.changes.forEach { change ->
                            // Handle Stylus constraint
                            val isStylus = change.type == PointerType.Stylus || change.type == PointerType.Eraser
                            if (sPenOnlyMode && !isStylus) {
                                return@forEach
                            }
     
                            val pointerId = change.id
                            val pos = change.position
                            val pressure = try { change.pressure } catch (e: Exception) { 1.0f }
                            val now = System.currentTimeMillis()
     
                            if (change.pressed) {
                                val isNewPath = !change.previousPressed || !activePointers.containsKey(pointerId)
                                if (isNewPath) {
                                    // Down gesture: Start new path or eraser stroke with current timestamp
                                    val pathId = System.nanoTime()
                                    val currentPoints = mutableListOf(DoodlePoint(pos.x, pos.y, pressure, now))
                                    pointerPoints[pointerId] = currentPoints
                                    activePointers[pointerId] = pathId
                                    
                                    val currentWidthPx = with(density) { DoodleState.brushWidth.value.dp.toPx() }
                                    val currentColor = DoodleState.brushColor.value
                                    
                                    val newPath = DoodlePath(
                                        id = pathId,
                                        points = currentPoints.toList(),
                                        color = currentColor,
                                        strokeWidth = currentWidthPx,
                                        isEraser = isEraserMode
                                    )
                                    DoodleState.activePaths.add(newPath)
                                } else {
                                    // Move gesture: Interpolate intermediate points to prevent dots/gaps under fast drag
                                    val currentPoints = pointerPoints[pointerId] ?: mutableListOf()
                                    if (currentPoints.isNotEmpty()) {
                                        val lastPoint = currentPoints.last()
                                        val dx = pos.x - lastPoint.x
                                        val dy = pos.y - lastPoint.y
                                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                                        val stepSize = 8.0f // Butter smooth sampling intervals
                                        if (distance > stepSize) {
                                            val steps = (distance / stepSize).toInt()
                                            val dt = now - lastPoint.timestamp
                                            val dp = pressure - lastPoint.pressure
                                            for (step in 1 until steps) {
                                                val fraction = step.toFloat() / steps
                                                val interpX = lastPoint.x + dx * fraction
                                                val interpY = lastPoint.y + dy * fraction
                                                val interpP = lastPoint.pressure + dp * fraction
                                                val interpT = lastPoint.timestamp + (dt * fraction).toLong()
                                                currentPoints.add(DoodlePoint(interpX, interpY, interpP, interpT))
                                            }
                                        }
                                    }
                                    currentPoints.add(DoodlePoint(pos.x, pos.y, pressure, now))
                                    
                                    // Clean up points older than 3 seconds inline to keep memory footprint minuscule and rendering at high FPS!
                                    val limit = System.currentTimeMillis()
                                    currentPoints.removeAll { limit - it.timestamp >= 3000L }
                                    
                                    val pathId = activePointers[pointerId]
                                    if (pathId != null) {
                                        val index = DoodleState.activePaths.indexOfFirst { it.id == pathId }
                                        if (index != -1) {
                                            val existingPath = DoodleState.activePaths[index]
                                            DoodleState.activePaths[index] = existingPath.copy(
                                                points = currentPoints.toList()
                                            )
                                        }
                                    }
                                }
                                change.consume()
                            } else if (change.previousPressed) {
                                // Up gesture: Complete path
                                activePointers.remove(pointerId)
                                pointerPoints.remove(pointerId)
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        for (doodlePath in paths) {
            val pts = doodlePath.points
            if (pts.isEmpty()) continue
 
            val isEraser = doodlePath.isEraser
 
            if (pts.size == 1) {
                val p = pts.first()
                val ageSec = (currentTime - p.timestamp) / 1000f
                val alpha = (1.0f - (ageSec / 3.0f)).coerceIn(0f, 1f)
                if (alpha > 0f) {
                    drawCircle(
                        color = if (isEraser) Color.Transparent else doodlePath.color,
                        radius = doodlePath.strokeWidth / 2f,
                        center = Offset(p.x, p.y),
                        alpha = if (isEraser) 1.0f else alpha,
                        blendMode = if (isEraser) BlendMode.Clear else BlendMode.Src
                    )
                }
            } else {
                for (i in 0 until pts.size - 1) {
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
 
                    val avgTimestamp = (p1.timestamp + p2.timestamp) / 2
                    val ageSec = (currentTime - avgTimestamp) / 1000f
                    val alpha = (1.0f - (ageSec / 3.0f)).coerceIn(0f, 1f)
 
                    if (alpha > 0f) {
                        drawLine(
                            color = if (isEraser) Color.Transparent else doodlePath.color,
                            start = Offset(p1.x, p1.y),
                            end = Offset(p2.x, p2.y),
                            strokeWidth = doodlePath.strokeWidth, // Fixed width prevents dotted bumpy fluctuations
                            cap = StrokeCap.Round,
                            alpha = if (isEraser) 1.0f else alpha,
                            blendMode = if (isEraser) BlendMode.Clear else BlendMode.Src
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetColorDot(
    color: Color,
    activeColor: Color,
    onClick: () -> Unit
) {
    val isSelected = activeColor == color
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

@Composable
fun FloatingControlUI(
    onDrag: (Float, Float) -> Unit,
    onExpandChanged: (Boolean) -> Unit,
    onColorMenuToggled: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    var showColorMenu by remember { mutableStateOf(false) }

    val brushColor by DoodleState.brushColor.collectAsState()
    val brushWidth by DoodleState.brushWidth.collectAsState()

    LaunchedEffect(isExpanded) {
        onExpandChanged(isExpanded)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.35f), // More transparent thin elegant background
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Strict REQUIREMENT: No shadow
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(2.dp)
    ) {
        if (!isExpanded) {
            // Minimized Bubble Layout: highly discreet while watching screens
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.5.dp, brushColor, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { isExpanded = true },
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Custom drawn neat plus icon
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(2.5.dp)
                                .background(brushColor, RoundedCornerShape(1.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(14.dp)
                                .background(brushColor, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        } else {
            // Expanded Control panel: extremely simple with 4 spaced compact buttons & absolutely zero labels!
            Column(
                modifier = Modifier.width(190.dp), // COMPACT WIDTH guarantees NO overlapping and NO excessive padding!
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly, // Perfectly distributes space for 4 compact buttons
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    // 1. Color Picker indicator button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (showColorMenu) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable {
                                showColorMenu = !showColorMenu
                                onColorMenuToggled(showColorMenu)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val previewSize = (5f + (brushWidth - 2f) * (10f / 22f)).dp.coerceIn(5.dp, 16.dp)
                        Box(
                            modifier = Modifier
                                .size(previewSize)
                                .background(brushColor, CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    }

                    // 2. Click-Through Toggle button (glowing green/lock state feedback)
                    val isTouchThrough by DoodleState.isTouchThrough.collectAsState()
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTouchThrough) Color(0xFF34C759).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable {
                                DoodleState.isTouchThrough.value = !isTouchThrough
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTouchThrough) Icons.Default.Lock else Icons.Default.Edit,
                            contentDescription = "Toggle Click-Through Mode",
                            tint = if (isTouchThrough) Color(0xFF30D158) else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // 3. Minimize button (Minus icon)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { 
                                showColorMenu = false
                                isExpanded = false 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Custom drawn neat minus icon
                        Box(
                            modifier = Modifier
                                .width(9.dp)
                                .height(1.8.dp)
                                .background(Color.White, RoundedCornerShape(1.dp))
                        )
                    }

                    // 4. Close icon to exit completely
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30).copy(alpha = 0.25f))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay tracker",
                            tint = Color(0xFFFF453A),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Color Selection Submenu - Instant display (no laggy animation as requested)
                if (showColorMenu) {
                    val context = LocalContext.current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                    ) {
                        // Spacing from the main buttons Row
                        Spacer(modifier = Modifier.height(6.dp))

                        // Preset Colors Grid: 4x2 grid of circles with precise padding!
                        // Row 1 of presets
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            DoodleState.colorPalette.take(4).forEach { color ->
                                PresetColorDot(color = color, activeColor = brushColor) {
                                    DoodleState.brushColor.value = color
                                    DoodleState.updateCustomColorFromColor(color)
                                    with(DoodleState) {
                                        DoodleState.save(context, "brush_color", color.toArgbInt())
                                        DoodleState.save(context, "custom_color_hue", DoodleState.customColorHue.value)
                                        DoodleState.save(context, "custom_color_sat", DoodleState.customColorSat.value)
                                        DoodleState.save(context, "custom_color_val", DoodleState.customColorVal.value)
                                        DoodleState.save(context, "custom_color_tone", DoodleState.customColorTone.value)
                                    }
                                }
                            }
                        }

                        // Row 2 of presets
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            DoodleState.colorPalette.drop(4).forEach { color ->
                                PresetColorDot(color = color, activeColor = brushColor) {
                                    DoodleState.brushColor.value = color
                                    DoodleState.updateCustomColorFromColor(color)
                                    with(DoodleState) {
                                        DoodleState.save(context, "brush_color", color.toArgbInt())
                                        DoodleState.save(context, "custom_color_hue", DoodleState.customColorHue.value)
                                        DoodleState.save(context, "custom_color_sat", DoodleState.customColorSat.value)
                                        DoodleState.save(context, "custom_color_val", DoodleState.customColorVal.value)
                                        DoodleState.save(context, "custom_color_tone", DoodleState.customColorTone.value)
                                    }
                                }
                            }
                        }

                        // Thin subtle line divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Compact Brush Size Slider Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            val context = LocalContext.current
                            val brushWidthState by DoodleState.brushWidth.collectAsState()
                            
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Brush Size Indicator",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )

                            Slider(
                                value = brushWidthState,
                                onValueChange = {
                                    DoodleState.brushWidth.value = it
                                    DoodleState.save(context, "brush_width", it)
                                },
                                valueRange = 2f..24f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White.copy(alpha = 0.8f),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                            )

                            Text(
                                text = "${brushWidthState.toInt()}px",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Thin subtle line divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Padded custom spectrum sliders: Hue spectrum and Tone spectrum (for Black & White!)
                        val hue by DoodleState.customColorHue.collectAsState()
                        val sat by DoodleState.customColorSat.collectAsState()
                        val valState by DoodleState.customColorVal.collectAsState()

                        // Row 1: Hue spectrum slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Color Palette Icon",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            val rainbowBrush = remember {
                                androidx.compose.ui.graphics.Brush.Companion.horizontalGradient(
                                    colors = listOf(
                                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                    )
                                )
                            }

                            // Box wrapping the slider to overlay standard transparent tracks on top of a thin modern rainbow bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Thin elegant continuous gradient color spectrum track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(rainbowBrush, RoundedCornerShape(2.dp))
                                )

                                Slider(
                                    value = hue,
                                    onValueChange = { newHue ->
                                        DoodleState.customColorHue.value = newHue
                                        val updatedColor = Color.hsv(newHue, sat, valState)
                                        DoodleState.brushColor.value = updatedColor

                                        DoodleState.save(context, "custom_color_hue", newHue)
                                        with(DoodleState) {
                                            DoodleState.save(context, "brush_color", updatedColor.toArgbInt())
                                        }
                                    },
                                    valueRange = 0f..360f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White, // Match the brush size style
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.width(32.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 2: Tone spectrum slider (Black & White!)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            val tone by DoodleState.customColorTone.collectAsState()
                            val pureHueColor = remember(hue) { Color.hsv(hue, 1.0f, 1.0f) }

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(brushColor)
                                    .border(1.dp, Color.White, CircleShape)
                            )

                            // Box wrapping the slider to overlay standard transparent tracks on top of custom tone bar
                            val toneBrush = remember(pureHueColor) {
                                androidx.compose.ui.graphics.Brush.Companion.horizontalGradient(
                                    colors = listOf(
                                        Color.Black,
                                        pureHueColor,
                                        Color.White
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Thin elegant continuous gradient tone spectrum track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(toneBrush, RoundedCornerShape(2.dp))
                                )

                                Slider(
                                    value = tone,
                                    onValueChange = { newTone ->
                                        DoodleState.customColorTone.value = newTone

                                        // Map tone: 0.0 -> Black, 0.5 -> Pure Hue Color, 1.0 -> White
                                        val newSat = if (newTone <= 0.5f) 1.0f else (1.0f - (newTone - 0.5f) * 2f).coerceIn(0f, 1f)
                                        val newVal = if (newTone <= 0.5f) (newTone * 2f).coerceIn(0f, 1f) else 1.0f

                                        DoodleState.customColorSat.value = newSat
                                        DoodleState.customColorVal.value = newVal

                                        val updatedColor = Color.hsv(hue, newSat, newVal)
                                        DoodleState.brushColor.value = updatedColor

                                        DoodleState.save(context, "custom_color_tone", newTone)
                                        DoodleState.save(context, "custom_color_sat", newSat)
                                        DoodleState.save(context, "custom_color_val", newVal)
                                        with(DoodleState) {
                                            DoodleState.save(context, "brush_color", updatedColor.toArgbInt())
                                        }
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White, // Match the brush size style
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.width(32.dp))
                        }
                    }
                }
            }
        }
    }
}
