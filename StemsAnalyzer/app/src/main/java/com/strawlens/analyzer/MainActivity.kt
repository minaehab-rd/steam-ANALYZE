package com.strawlens.analyzer

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.strawlens.analyzer.data.MixtureResult
import com.strawlens.analyzer.data.SettingsStore
import com.strawlens.analyzer.network.GeminiApiException
import com.strawlens.analyzer.network.GeminiClient
import com.strawlens.analyzer.ui.theme.StemsAnalyzerTheme
import com.strawlens.analyzer.ui.theme.productColor
import com.strawlens.analyzer.ui.theme.stemColor
import com.strawlens.analyzer.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(applicationContext)

        setContent {
            StemsAnalyzerTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(settingsStore)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf("en") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(SettingsStore.DEFAULT_MODEL) }

    var showSettings by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MixtureResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load persisted settings once.
    LaunchedEffect(Unit) {
        language = settingsStore.getLanguage()
        apiKey = settingsStore.getApiKey()
        model = settingsStore.getModel()
    }

    fun t(en: String, ar: String) = if (language == "ar") ar else en

    fun loadPreview(uri: Uri) {
        result = null
        errorMessage = null
        previewBitmap = ImageUtils.loadAndPrepareBitmap(context, uri, maxDimension = 1024)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            selectedImageUri = pendingCameraUri
            loadPreview(pendingCameraUri!!)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            loadPreview(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraOutputUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val uri = createCameraOutputUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun runAnalysis() {
        val bitmap = previewBitmap ?: return
        isAnalyzing = true
        errorMessage = null
        result = null
        scope.launch {
            try {
                val fullResBitmap = selectedImageUri?.let {
                    ImageUtils.loadAndPrepareBitmap(context, it, maxDimension = 1536)
                } ?: bitmap
                val base64 = ImageUtils.bitmapToBase64Jpeg(fullResBitmap)
                val analysis = GeminiClient.analyzeStemsVsProduct(
                    apiKey = apiKey,
                    model = model,
                    imageBase64Jpeg = base64,
                    language = language
                )
                result = analysis
            } catch (e: GeminiApiException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = t("Something went wrong: ${e.message}", "حدث خطأ: ${e.message}")
            } finally {
                isAnalyzing = false
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            initialApiKey = apiKey,
            initialModel = model,
            initialLanguage = language,
            onDismiss = { showSettings = false },
            onSave = { newKey, newModel, newLang ->
                apiKey = newKey
                model = newModel
                language = newLang
                scope.launch {
                    settingsStore.setApiKey(newKey)
                    settingsStore.setModel(newModel)
                    settingsStore.setLanguage(newLang)
                }
                showSettings = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Stems Analyzer", "محلل القش")) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = t("Settings", "الإعدادات"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                t(
                    "Photograph raw material with stems in it, and get the stems % vs. product % breakdown.",
                    "التقط صورة للمادة الخام التي تحتوي على قش، واحصل على نسبة القش مقابل نسبة المنتج."
                ),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Image preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = t("Selected photo", "الصورة المختارة"),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        t("No photo yet", "لم يتم اختيار صورة بعد"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { launchCamera() }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Take Photo", "التقط صورة"))
                }
                OutlinedButton(onClick = { pickImageLauncher.launch("image/*") }) {
                    Icon(ImageIcon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Gallery", "المعرض"))
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { runAnalysis() },
                enabled = previewBitmap != null && !isAnalyzing,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(t("Analyzing...", "جاري التحليل..."))
                } else {
                    Text(t("Analyze", "تحليل"))
                }
            }

            if (apiKey.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    t(
                        "Tip: add your free Gemini API key in Settings before analyzing.",
                        "نصيحة: أضف مفتاح Gemini API الخاص بك من الإعدادات قبل التحليل."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            errorMessage?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            result?.let { r ->
                Spacer(Modifier.height(24.dp))
                ResultView(result = r, language = language)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ResultView(result: MixtureResult, language: String) {
    fun t(en: String, ar: String) = if (language == "ar") ar else en

    val stems = result.stemsPercentage.coerceIn(0.0, 100.0)
    val product = result.productPercentage.coerceIn(0.0, 100.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp)
    ) {
        Text(
            t("Result", "النتيجة"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        ResultBar(
            label = t("Stems / Straw", "القش / السيقان"),
            percentage = stems,
            color = stemColor
        )
        Spacer(Modifier.height(12.dp))
        ResultBar(
            label = t("Product", "المنتج"),
            percentage = product,
            color = productColor
        )

        if (!result.notes.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                result.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ResultBar(label: String, percentage: Double, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            Text("${"%.1f".format(percentage)}%", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = (percentage / 100.0).toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    initialApiKey: String,
    initialModel: String,
    initialLanguage: String,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, model: String, language: String) -> Unit
) {
    var apiKeyField by remember { mutableStateOf(initialApiKey) }
    var modelField by remember { mutableStateOf(initialModel) }
    var languageField by remember { mutableStateOf(initialLanguage) }

    fun t(en: String, ar: String) = if (languageField == "ar") ar else en

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Settings", "الإعدادات")) },
        text = {
            Column {
                Text(
                    t(
                        "Your Gemini API key is stored only on this device and used to call Google's API directly.",
                        "يتم حفظ مفتاح Gemini API الخاص بك على هذا الجهاز فقط، ويُستخدم للاتصال المباشر بواجهة Google."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKeyField,
                    onValueChange = { apiKeyField = it },
                    label = { Text(t("Gemini API Key", "مفتاح Gemini API")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = modelField,
                    onValueChange = { modelField = it },
                    label = { Text(t("Model name", "اسم النموذج")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(t("Language", "اللغة"), style = MaterialTheme.typography.labelLarge)
                Row {
                    FilterChip(
                        selected = languageField == "en",
                        onClick = { languageField = "en" },
                        label = { Text("English") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = languageField == "ar",
                        onClick = { languageField = "ar" },
                        label = { Text("العربية") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiKeyField, modelField.ifBlank { SettingsStore.DEFAULT_MODEL }, languageField) }) {
                Text(t("Save", "حفظ"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Cancel", "إلغاء"))
            }
        }
    )
}

/** Creates a content:// Uri (via FileProvider) that the system camera app can write a full-res photo into. */
private fun createCameraOutputUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
