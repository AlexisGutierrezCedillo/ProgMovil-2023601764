package com.example.productos

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.productos.ui.theme.ProductosTheme
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import androidx.exifinterface.media.ExifInterface
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontWeight

// --- MODELO ---
data class Producto(
    val id: Int,
    val nombre: String,
    val precio: Double,
    val descripcion: String?,
    val imagenUrl: String?
)

// --- RUTAS DE NAVEGACIÓN ---
sealed class Screen(val route: String) {
    object Lista   : Screen("lista")
    object Agregar : Screen("agregar")
    object Editar  : Screen("editar/{id}") {
        fun createRoute(id: Int) = "editar/$id"
    }
    object Ajustes : Screen("ajustes")
}

// --- ACCESO A BD ---
fun obtenerProductos(ctx: Context): List<Producto> {
    val db     = DBHelper(ctx).readableDatabase
    val cursor = db.rawQuery("SELECT * FROM producto", null)
    val lista  = mutableListOf<Producto>()
    while (cursor.moveToNext()) {
        lista += Producto(
            id          = cursor.getInt(cursor.getColumnIndexOrThrow("id_producto")),
            nombre      = cursor.getString(cursor.getColumnIndexOrThrow("nombre")),
            precio      = cursor.getDouble(cursor.getColumnIndexOrThrow("precio")),
            descripcion = cursor.getString(cursor.getColumnIndexOrThrow("descripcion")),
            imagenUrl   = cursor.getString(cursor.getColumnIndexOrThrow("imagenURL"))
        )
    }
    cursor.close()
    db.close()
    return lista
}

// --- Orden de la lista (must be top-level) ---
private enum class SortMode {
    ALPHABETIC,
    PRICE_ASC,
    PRICE_DESC
}

// --- UTILERÍAS DE IMAGEN ---
fun base64ToBitmap(base64Str: String): Bitmap? = try {
    val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (_: Exception) { null }

fun rotateBitmap(src: Bitmap, angle: Float): Bitmap {
    val m = Matrix().apply { postRotate(angle) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

suspend fun uriToBase64(ctx: Context, uri: Uri): String? = try {
    // Leemos una sola vez el stream para EXIF y luego para el bitmap
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        val rot = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else                                -> 0f
        }
        input.close()
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            var bmp = BitmapFactory.decodeStream(s) ?: return@use
            if (rot != 0f) bmp = rotateBitmap(bmp, rot)
            val big = Bitmap.createScaledBitmap(bmp, 1100, 1100, true)
            ByteArrayOutputStream().use { baos ->
                big.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
        }
    }
    null
} catch (_: Exception) { null }

// Genera un URI donde la cámara guardará la foto completa
fun createImageUri(ctx: Context): Uri =
    ContentValues().run {
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DISPLAY_NAME, "producto_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, this)!!
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIPrincipal(
    productos: List<Producto>,
    onAgregar: () -> Unit,
    onEditar: (Int) -> Unit,
    onEliminar: (Int) -> Unit,
    onAjustes: () -> Unit
) {
    //Las siguientes dos lineas de código sirven para recargar la vista cada que se ponga el siguiente código: refrescarClave++
    var refrescarClave by remember { mutableStateOf(0) }
    key(refrescarClave) {

        val ctx = LocalContext.current
        val tourPrefs = remember {
            ctx.getSharedPreferences("tour_prefs", Context.MODE_PRIVATE)
        }
        var firstStart by remember { mutableStateOf(tourPrefs.getBoolean("first_start", true)) }
        var pasoTour by remember { mutableStateOf(-1) }
        val listState = rememberLazyListState()

        val pasos = listOf(
            "Pulsa aquí si necesitas ayuda.",
            "Este es el botón de ajustes ⚙, para cambiar preferencias.",
            "Con este botón añades un nuevo producto.",
            "Este botón abre las opciones de filtro.",
            "Aquí puedes escribir para buscar productos.",
            "Con este botón eliminas productos existentes.",
            "Con este botón editas la información.",
            "Toca cualquier tarjeta para ver detalle."
        )

        // Coordenadas y tamaños
        var infoPos by remember { mutableStateOf(Offset.Zero) };
        var infoSize by remember { mutableStateOf(IntSize.Zero) }
        var ajustesPos by remember { mutableStateOf(Offset.Zero) };
        var ajustesSize by remember { mutableStateOf(IntSize.Zero) }
        var addPos by remember { mutableStateOf(Offset.Zero) };
        var addSize by remember { mutableStateOf(IntSize.Zero) }
        var filterPos by remember { mutableStateOf(Offset.Zero) };
        var filterSize by remember { mutableStateOf(IntSize.Zero) }
        var searchPos by remember { mutableStateOf(Offset.Zero) };
        var searchSize by remember { mutableStateOf(IntSize.Zero) }
        var primeraPos by remember { mutableStateOf(Offset.Zero) };
        var primeraSize by remember { mutableStateOf(IntSize.Zero) }
        var firstVisibleEditPos by remember { mutableStateOf(Offset.Zero) }
        var firstVisibleEditSize by remember { mutableStateOf(IntSize.Zero) }
        var firstVisibleDelPos by remember { mutableStateOf(Offset.Zero) }
        var firstVisibleDelSize by remember { mutableStateOf(IntSize.Zero) }

        // Highlight inicial de Info durante 5s
        LaunchedEffect(Unit) {
            if (firstStart) {
                pasoTour = 0
                delay(5000)
                pasoTour = -1
                firstStart = false
                tourPrefs.edit().putBoolean("first_start", false).apply()
            }
        }

        val accent = MaterialTheme.colorScheme.primary
        var detalleId by rememberSaveable { mutableStateOf<Int?>(null) }
        var toEliminar by rememberSaveable { mutableStateOf<Int?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var sortMode by rememberSaveable { mutableStateOf(SortMode.ALPHABETIC) }
        var menuExpanded by remember { mutableStateOf(false) }

        // 1) Filtrar y ordenar solo cuando cambian productos, query o sortMode
        val ordenados by remember(productos, query, sortMode) {
            derivedStateOf {
                val filtrados = productos.filter { it.nombre.contains(query, ignoreCase = true) }
                when (sortMode) {
                    SortMode.ALPHABETIC -> filtrados.sortedBy { it.nombre.lowercase() }
                    SortMode.PRICE_ASC -> filtrados.sortedBy { it.precio }
                    SortMode.PRICE_DESC -> filtrados.sortedByDescending { it.precio }
                }
            }
        }

        // 2) Memorizar miniaturas Base64→ImageBitmap
        val thumbnailMap = remember(ordenados) {
            ordenados.associate { p ->
                p.id to p.imagenUrl
                    ?.let(::base64ToBitmap)
                    ?.let { Bitmap.createScaledBitmap(it, 100, 100, true) }
                    ?.asImageBitmap()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Productos", color = MaterialTheme.colorScheme.onPrimary) },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = accent,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        // INFO
                        IconButton(
                            onClick = { pasoTour = 1 },
                            modifier = Modifier.onGloballyPositioned { c ->
                                infoPos = c.localToWindow(Offset.Zero)
                                infoSize = c.size
                            }
                        ) { Icon(Icons.Default.Info, contentDescription = "Ayuda") }
                        // AJUSTES
                        IconButton(
                            onClick = onAjustes,
                            modifier = Modifier.onGloballyPositioned { c ->
                                ajustesPos = c.localToWindow(Offset.Zero)
                                ajustesSize = c.size
                            }
                        ) { Icon(Icons.Default.Settings, contentDescription = "Ajustes") }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAgregar,
                    containerColor = accent,
                    modifier = Modifier.onGloballyPositioned { c ->
                        addPos = c.localToWindow(Offset.Zero)
                        addSize = c.size
                    }
                ) { Icon(Icons.Default.Add, contentDescription = "Agregar") }
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                // Búsqueda + Filtro
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.onGloballyPositioned { c ->
                            filterPos = c.localToWindow(Offset.Zero)
                            filterSize = c.size
                        }
                    ) { Icon(Icons.Default.FilterList, contentDescription = "Ordenar") }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            onClick = { sortMode = SortMode.ALPHABETIC; menuExpanded = false },
                            text = { Text("A–Z") }
                        )
                        DropdownMenuItem(
                            onClick = { sortMode = SortMode.PRICE_ASC; menuExpanded = false },
                            text = { Text("Precio ↑") }
                        )
                        DropdownMenuItem(
                            onClick = { sortMode = SortMode.PRICE_DESC; menuExpanded = false },
                            text = { Text("Precio ↓") }
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar…") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { c ->
                                searchPos = c.localToWindow(Offset.Zero)
                                searchSize = c.size
                            },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = accent,
                            unfocusedBorderColor = accent
                        )
                    )
                }

                // Lista de productos
                Box(Modifier.weight(1f)) {
                    if (ordenados.isEmpty()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No hay productos para mostrar")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onAgregar) { Text("Agregar producto") }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = ordenados,
                                key = { _, p -> p.id }
                            ) { index, p ->
                                var cardMod = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable { detalleId = p.id }
                                // Primer visible
                                if (index == listState.firstVisibleItemIndex) {
                                    // Captura posición de la tarjeta
                                    cardMod = cardMod.onGloballyPositioned { coords ->
                                        primeraPos = coords.localToWindow(Offset.Zero)
                                        primeraSize = coords.size
                                    }

                                }
                                Card(
                                    modifier = cardMod
                                        .fillMaxWidth()
                                        .padding(1.dp)
                                        .clickable { detalleId = p.id },
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Row(
                                        Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Imagen desde cache
                                        thumbnailMap[p.id]?.let { imgBmp ->
                                            Image(
                                                bitmap = imgBmp,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(100.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                p.nombre,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                "$${p.precio}",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        //Editar
                                        IconButton(
                                            onClick = { onEditar(p.id) },
                                            modifier = Modifier.onGloballyPositioned { coords ->
                                                if (index == listState.firstVisibleItemIndex) {
                                                    firstVisibleEditPos =
                                                        coords.localToWindow(Offset.Zero)
                                                    firstVisibleEditSize = coords.size
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Editar"
                                            )
                                        }
                                        //Eliminar
                                        IconButton(
                                            onClick = { toEliminar = p.id },
                                            modifier = Modifier.onGloballyPositioned { coords ->
                                                if (index == listState.firstVisibleItemIndex) {
                                                    firstVisibleDelPos =
                                                        coords.localToWindow(Offset.Zero)
                                                    firstVisibleDelSize = coords.size
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Eliminar"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // OVERLAY & TOOLTIP (mismos ajustes que tenías)
            if (pasoTour in pasos.indices) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

                val (tPos, tSize) = when (pasoTour) {
                    0 -> infoPos to infoSize
                    1 -> ajustesPos to ajustesSize
                    2 -> addPos to addSize
                    3 -> filterPos to filterSize
                    4 -> searchPos to searchSize
                    5 -> firstVisibleDelPos to firstVisibleDelSize
                    6 -> firstVisibleEditPos to firstVisibleEditSize
                    7 -> primeraPos to primeraSize
                    else -> infoPos to infoSize
                }
                // --- AJUSTES MANUALES ---
                // Desplazamiento y expansión del recuadro amarillo:
                val highlightXAdj = /*+/- px en X*/ -25
                val highlightYAdj = /*+/- px en Y*/ -100
                val highlightWidthAdj = /*+/- px ancho*/ -80
                val highlightHeightAdj = /*+/- px alto*/ -80

                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (tPos.x.toInt() + highlightXAdj),
                                (tPos.y.toInt() + highlightYAdj)
                            )
                        }
                        .size(
                            width = (tSize.width + highlightWidthAdj).dp,
                            height = (tSize.height + highlightHeightAdj).dp
                        )
                        .border(BorderStroke(2.dp, Color.Magenta), shape = RoundedCornerShape(4.dp))
                )

                // 3) Tooltip: calcula offset X/Y
                val xOffAdj = /*+/- px en X*/ -800
                val yOffAdj = /*+/- px en Y*/ 0

                val xOff = tPos.x.toInt() + xOffAdj
                val yOff = if (pasoTour == 2) {
                    // Tooltip encima del botón Añadir
                    tPos.y.toInt() + yOffAdj
                } else {
                    // Tooltip debajo del target
                    (tPos.y + tSize.height).toInt() + yOffAdj
                }

                Popup(
                    offset = IntOffset(xOff, yOff),
                    onDismissRequest = { /* no dismiss */ }
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(pasos[pasoTour], style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = {
                                pasoTour++
                                if (pasoTour > pasos.lastIndex) pasoTour = -1
                            }) {
                                Text(if (pasoTour == pasos.lastIndex) "Entendido" else "Siguiente")
                            }
                        }
                    }
                }
            }

            // DIÁLOGO DE DETALLE (centrado)
            detalleId?.let { id ->
                val prod = ordenados.firstOrNull { it.id == id } ?: return@let
                Dialog(
                    onDismissRequest = { detalleId = null },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnClickOutside = true
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { detalleId = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp)
                                )
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            prod.imagenUrl?.let { bs ->
                                base64ToBitmap(bs)?.let { bmp ->
                                    Image(
                                        painter = BitmapPainter(bmp.asImageBitmap()),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .sizeIn(maxWidth = 250.dp, maxHeight = 250.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = prod.nombre,
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Precio: $${prod.precio}",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = prod.descripcion.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // CONFIRMACIÓN ELIMINAR
            toEliminar?.let { id ->
                AlertDialog(
                    onDismissRequest = { toEliminar = null },
                    title = { Text("Eliminar producto") },
                    text = { Text("¿Seguro quieres eliminar este producto?") },
                    confirmButton = {
                        TextButton(onClick = {
                            onEliminar(id)
                            toEliminar = null
                            refrescarClave++
                        }) { Text("Sí") }
                    },
                    dismissButton = {
                        TextButton(onClick = { toEliminar = null }) { Text("No") }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgregarProductoScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val db  = DBHelper(ctx)
    val scope = rememberCoroutineScope()

    var nombre by rememberSaveable { mutableStateOf("") }
    var precio by rememberSaveable { mutableStateOf("") }
    var desc   by rememberSaveable { mutableStateOf("") }
    var uri    by rememberSaveable { mutableStateOf<Uri?>(null) }
    var b64    by rememberSaveable { mutableStateOf<String?>(null) }
    var show   by rememberSaveable { mutableStateOf(false) }

    // Validaciones
    val nombreValido = nombre.isNotBlank()
    val precioDouble = precio.toDoubleOrNull()
    val precioValido = precioDouble != null && precioDouble >= 0

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Lanzadores de cámara y galería
    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && uri != null) {
            scope.launch {
                uriToBase64(ctx, uri!!)?.let { b64 = it }
            }
        } else {
            uri = null
        }
    }
    val galLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { u ->
        u?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            uri = it
            scope.launch {
                uriToBase64(ctx, it)?.let { b64 = it }
            }
        }
    }

    val bmpPreview = remember(b64) {
        b64?.let(::base64ToBitmap)?.let { Bitmap.createScaledBitmap(it, 100, 100, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        // Aquí inyectamos el host
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nombre
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                isError = !nombreValido && nombre.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            if (!nombreValido && nombre.isNotEmpty()) {
                Text("El nombre es obligatorio", color = MaterialTheme.colorScheme.error)
            }

            // Precio
            OutlinedTextField(
                value = precio,
                onValueChange = { precio = it },
                label = { Text("Precio") },
                isError = !precioValido && precio.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (!precioValido && precio.isNotEmpty()) {
                Text("Introduce un precio válido", color = MaterialTheme.colorScheme.error)
            }

            // Descripción
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )

            // Galería y Cámara
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    uri = null; b64 = null
                    galLauncher.launch("image/*")
                }) { Text("Galería") }
                Button(onClick = {
                    uri = createImageUri(ctx)
                    b64 = null
                    takePhotoLauncher.launch(uri!!)
                }) { Text("Cámara") }
            }

            // Vista previa
            bmpPreview?.let {
                Image(
                    painter = BitmapPainter(it.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { show = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Guardar con validación y snackbar
            Button(
                onClick = {
                    if (nombreValido && precioValido) {
                        scope.launch {
                            // Calcula la imagen final: si no hay b64, carga el drawable de sistema por defecto
                            val finalImage = b64 ?: getDefaultBase64(ctx)

                            db.insertarProducto(
                                Producto(0, nombre, precioDouble ?: 0.0, desc, finalImage)
                            )

                            onDone()
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Nombre y precio obligatorios")
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Guardar")
            }
        }

        // Diálogo de preview
        if (show && b64 != null) {
            Dialog(
                onDismissRequest = { show = false },
                properties = DialogProperties(dismissOnClickOutside = true)
            ) {
                base64ToBitmap(b64!!)?.let {
                    Image(
                        painter = BitmapPainter(it.asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .sizeIn(maxWidth = 350.dp, maxHeight = 350.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductoScreen(id: Int, onDone: ()->Unit) {
    val ctx = LocalContext.current
    val db  = DBHelper(ctx)
    val scope = rememberCoroutineScope()

    var nombre by rememberSaveable { mutableStateOf("") }
    var precio by rememberSaveable { mutableStateOf("") }
    var desc   by rememberSaveable { mutableStateOf("") }
    var uri    by rememberSaveable { mutableStateOf<Uri?>(null) }
    var b64    by rememberSaveable { mutableStateOf<String?>(null) }
    var show   by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) {
        obtenerProductos(ctx).firstOrNull { it.id == id }?.let {
            nombre = it.nombre
            precio = it.precio.toString()
            desc   = it.descripcion.orEmpty()
            b64    = it.imagenUrl
        }
    }

    val nombreValido = nombre.isNotBlank()
    val precioDouble = precio.toDoubleOrNull()
    val precioValido = precioDouble != null && precioDouble >= 0

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && uri != null) {
            scope.launch {
                uriToBase64(ctx, uri!!)?.let { b64 = it }
            }
        } else {
            uri = null
        }
    }
    val galLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { u ->
        u?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            uri = it
            scope.launch {
                uriToBase64(ctx, it)?.let { b64 = it }
            }
        }
    }

    val bmpPreview = remember(b64) {
        b64?.let(::base64ToBitmap)?.let { Bitmap.createScaledBitmap(it, 100, 100, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        // Inyectamos también el snackbarHost aquí
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nombre
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                isError = !nombreValido && nombre.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            if (!nombreValido && nombre.isNotEmpty()) {
                Text("El nombre es obligatorio", color = MaterialTheme.colorScheme.error)
            }

            // Precio
            OutlinedTextField(
                value = precio,
                onValueChange = { precio = it },
                label = { Text("Precio") },
                isError = !precioValido && precio.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (!precioValido && precio.isNotEmpty()) {
                Text("Introduce un precio válido", color = MaterialTheme.colorScheme.error)
            }

            // Descripción
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )

            // Galería y Cámara
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    uri = null; b64 = null
                    galLauncher.launch("image/*")
                }) { Text("Galería") }
                Button(onClick = {
                    uri = createImageUri(ctx)
                    b64 = null
                    takePhotoLauncher.launch(uri!!)
                }) { Text("Cámara") }
            }

            // Vista previa
            bmpPreview?.let {
                Image(
                    painter = BitmapPainter(it.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { show = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Guardar con snackbar de validación
            Button(
                onClick = {
                    if (nombreValido && precioValido) {
                        // si no hay imagen nueva, reutiliza la anterior o usa la por defecto
                        val finalImage = b64 ?: getDefaultBase64(ctx)
                        scope.launch {
                            db.actualizarProducto(
                                Producto(id, nombre, precioDouble ?: 0.0, desc, finalImage)
                            )
                            onDone()
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Nombre y precio obligatorios")
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Guardar")
            }
        }

        // Diálogo de preview
        if (show && b64 != null) {
            Dialog(
                onDismissRequest = { show = false },
                properties = DialogProperties(dismissOnClickOutside = true)
            ) {
                base64ToBitmap(b64!!)?.let {
                    Image(
                        painter = BitmapPainter(it.asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .sizeIn(maxWidth = 350.dp, maxHeight = 350.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}


// --- AJUSTES ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    settings: UserSettings,
    onToggleDark: (Boolean)->Unit,
    onFontScaleChange: (Float)->Unit,
    onAccentChange: (Color)->Unit,
    onDone: ()->Unit
) {
    val cs = MaterialTheme.colorScheme
    var r by rememberSaveable { mutableStateOf((settings.accentColor shr 16 and 0xFF) / 255f) }
    var g by rememberSaveable { mutableStateOf((settings.accentColor shr 8  and 0xFF) / 255f) }
    var b by rememberSaveable { mutableStateOf((settings.accentColor       and 0xFF) / 255f) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Ajustes", color = cs.onPrimary) },
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = cs.primary)
        )
    }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Modo oscuro", Modifier.weight(1f), color = cs.onBackground)
                Switch(settings.darkMode, onToggleDark)
            }

            Text("Tamaño letra: ${"%.1f".format(settings.fontScale)}×", color = cs.onBackground)
            Slider(
                value = settings.fontScale,
                onValueChange = onFontScaleChange,
                valueRange = 0.8f..1.5f,
                steps = 7,
                colors = SliderDefaults.colors(
                    thumbColor = cs.primary,
                    activeTrackColor = cs.primary
                )
            )

            Text("Color de énfasis", color = cs.onBackground)
            Slider(
                value = r,
                onValueChange = { r = it; onAccentChange(Color(red = r, green = g, blue = b)) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(activeTrackColor = Color.Red, thumbColor = Color.Red)
            )
            Slider(
                value = g,
                onValueChange = { g = it; onAccentChange(Color(red = r, green = g, blue = b)) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(activeTrackColor = Color.Green, thumbColor = Color.Green)
            )
            Slider(
                value = b,
                onValueChange = { b = it; onAccentChange(Color(red = r, green = g, blue = b)) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(activeTrackColor = Color.Blue, thumbColor = Color.Blue)
            )

            Spacer(Modifier.weight(1f))
            /*Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = Color(red = r, green = g, blue = b))
            ) {
                Text("Guardar", color = cs.onPrimary)
            }*/
        }
    }
}

// --- MAINACTIVITY ---
class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        setContent {
            val settings by prefs.settingsFlow.collectAsState(
                initial = UserSettings(false, 1f, Color(0xFF2196F3).toArgb())
            )
            val scope = rememberCoroutineScope()

            ProductosTheme(
                darkTheme = settings.darkMode,
                accent    = Color(settings.accentColor.toLong()),
                fontScale = settings.fontScale
            ) {
                val ctx = LocalContext.current
                val nav = rememberNavController()
                var productos by remember { mutableStateOf(obtenerProductos(ctx)) }
                val reload = { productos = obtenerProductos(ctx) }

                NavHost(navController = nav, startDestination = Screen.Lista.route) {
                    composable(Screen.Lista.route) {
                        UIPrincipal(
                            productos  = productos,
                            onAgregar  = { nav.navigate(Screen.Agregar.route) },
                            onEditar   = { nav.navigate(Screen.Editar.createRoute(it)) },
                            onEliminar = { DBHelper(ctx).eliminarProducto(it); reload() },
                            onAjustes  = { nav.navigate(Screen.Ajustes.route) }
                        )
                    }
                    composable(Screen.Agregar.route) {
                        AgregarProductoScreen {
                            nav.popBackStack()
                            reload()
                        }
                    }
                    composable(
                        Screen.Editar.route,
                        arguments = listOf(navArgument("id") { type = NavType.IntType })
                    ) { back ->
                        val id = back.arguments?.getInt("id") ?: 0
                        EditProductoScreen(id) {
                            nav.popBackStack()
                            reload()
                        }
                    }
                    composable(Screen.Ajustes.route) {
                        AjustesScreen(
                            settings          = settings,
                            onToggleDark      = { scope.launch { prefs.updateDarkMode(it) } },
                            onFontScaleChange = { scope.launch { prefs.updateFontScale(it) } },
                            onAccentChange    = { scope.launch { prefs.updateAccentColor(it.toArgb()) } },
                            onDone            = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}