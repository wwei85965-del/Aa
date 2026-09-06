package com.vv.translate

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VVTranslateApp()
        }
    }
}

data class Language(
    val name: String,
    val code: String
)

private val languages = listOf(
    Language("自动检测", "auto"),
    Language("中文", TranslateLanguage.CHINESE),
    Language("English", TranslateLanguage.ENGLISH),
    Language("日本語", TranslateLanguage.JAPANESE),
    Language("한국어", TranslateLanguage.KOREAN)
)

@Composable
fun VVTranslateApp() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var sourceLanguage by remember {
        mutableStateOf(languages[0])
    }

    var targetLanguage by remember {
        mutableStateOf(languages[1])
    }

    var inputText by remember {
        mutableStateOf("")
    }

    var resultText by remember {
        mutableStateOf("")
    }

    var statusText by remember {
        mutableStateOf("输入文字，或者选择图片")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var sourceMenuExpanded by remember {
        mutableStateOf(false)
    }

    var targetMenuExpanded by remember {
        mutableStateOf(false)
    }

    var ocrLines by remember {
        mutableStateOf(listOf<String>())
    }

    /*
     * OCR
     */
    fun recognizeBitmap(bitmap: Bitmap) {

        scope.launch {

            isLoading = true
            statusText = "正在识别图片文字…"

            try {

                val image = InputImage.fromBitmap(bitmap, 0)

                val recognizer =
                    TextRecognition.getClient(
                        ChineseTextRecognizerOptions.Builder().build()
                    )

                val visionText =
                    recognizer.process(image).await()

                recognizer.close()

                val text = visionText.text

                ocrLines =
                    text.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                inputText = text
                resultText = ""

                statusText =
                    if (text.isBlank()) {
                        "没有识别到文字"
                    } else {
                        "识别完成，点击下面任意文字即可翻译"
                    }

            } catch (e: Exception) {

                statusText =
                    "图片识别失败：${e.message ?: "未知错误"}"

            } finally {

                isLoading = false
            }
        }
    }

    /*
     * 相册
     */
    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {

                scope.launch {

                    try {

                        val bitmap =
                            android.provider.MediaStore.Images.Media
                                .getBitmap(
                                    context.contentResolver,
                                    uri
                                )

                        recognizeBitmap(bitmap)

                    } catch (e: Exception) {

                        statusText =
                            "无法读取图片：${e.message}"
                    }
                }
            }
        }

    /*
     * 相机
     */
    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->

            if (bitmap != null) {
                recognizeBitmap(bitmap)
            }
        }

    /*
     * 相机权限
     */
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                cameraLauncher.launch(null)

            } else {

                statusText = "没有相机权限，无法拍照翻译"
            }
        }

    fun openCamera() {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            cameraLauncher.launch(null)

        } else {

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    /*
     * 翻译
     */
    fun translate(text: String = inputText) {

        if (text.isBlank()) {

            statusText = "请先输入文字"

            return
        }

        scope.launch {

            isLoading = true
            statusText = "正在翻译…"

            try {

                var sourceCode = sourceLanguage.code

                /*
                 * 自动检测语言
                 */
                if (sourceCode == "auto") {

                    sourceCode =
                        LanguageIdentification
                            .getClient()
                            .identifyLanguage(text)
                            .await()
                }

                /*
                 * 相同语言
                 */
                if (sourceCode == targetLanguage.code) {

                    resultText = text

                } else if (sourceCode == "und") {

                    resultText = "无法识别源语言"

                } else {

                    val options =
                        TranslatorOptions.Builder()
                            .setSourceLanguage(sourceCode)
                            .setTargetLanguage(targetLanguage.code)
                            .build()

                    val translator =
                        Translation.getClient(options)

                    /*
                     * 下载对应翻译模型
                     */
                    translator
                        .downloadModelIfNeeded(
                            DownloadConditions.Builder().build()
                        )
                        .await()

                    resultText =
                        translator
                            .translate(text)
                            .await()

                    translator.close()
                }

                statusText = "翻译完成"

            } catch (e: Exception) {

                statusText =
                    "翻译失败：${e.message ?: "请检查网络连接"}"

            } finally {

                isLoading = false
            }
        }
    }

    MaterialTheme {

        Scaffold(

            topBar = {

                TopAppBar(

                    title = {

                        Column {

                            Text(
                                text = "VV Translate",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text(
                                text = "快速 · 简洁 · 图片翻译",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            }

        ) { padding ->

            LazyColumn(

                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),

                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                /*
                 * 语言选择
                 */
                item {

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                            )
                    ) {

                        Row(

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),

                            verticalAlignment =
                                Alignment.CenterVertically,

                            horizontalArrangement =
                                Arrangement.Center
                        ) {

                            /*
                             * 源语言
                             */
                            Column {

                                BoxLanguageButton(
                                    text = sourceLanguage.name,
                                    expanded = sourceMenuExpanded,
                                    onExpandedChange = {
                                        sourceMenuExpanded = it
                                    }
                                ) {

                                    languages.forEach { language ->

                                        DropdownMenuItem(

                                            text = {
                                                Text(language.name)
                                            },

                                            onClick = {

                                                sourceLanguage =
                                                    language

                                                sourceMenuExpanded =
                                                    false
                                            }
                                        )
                                    }
                                }
                            }

                            /*
                             * 交换语言
                             */
                            IconButton(

                                onClick = {

                                    val temp =
                                        sourceLanguage

                                    sourceLanguage =
                                        targetLanguage

                                    targetLanguage =
                                        temp
                                }
                            ) {

                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = "交换语言"
                                )
                            }

                            /*
                             * 目标语言
                             */
                            Column {

                                BoxLanguageButton(
                                    text = targetLanguage.name,
                                    expanded = targetMenuExpanded,
                                    onExpandedChange = {
                                        targetMenuExpanded = it
                                    }
                                ) {

                                    languages
                                        .filter {
                                            it.code != "auto"
                                        }
                                        .forEach { language ->

                                            DropdownMenuItem(

                                                text = {
                                                    Text(language.name)
                                                },

                                                onClick = {

                                                    targetLanguage =
                                                        language

                                                    targetMenuExpanded =
                                                        false
                                                }
                                            )
                                        }
                                }
                            }
                        }
                    }
                }

                /*
                 * 输入框
                 */
                item {

                    OutlinedTextField(

                        value = inputText,

                        onValueChange = {

                            inputText = it
                            ocrLines = emptyList()
                        },

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp),

                        placeholder = {
                            Text("输入要翻译的文字…")
                        },

                        shape =
                            RoundedCornerShape(18.dp)
                    )
                }

                /*
                 * 翻译按钮
                 */
                item {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Button(

                            onClick = {
                                translate()
                            },

                            enabled = !isLoading,

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                if (isLoading)
                                    "处理中…"
                                else
                                    "翻译"
                            )
                        }

                        IconButton(

                            onClick = {

                                if (resultText.isNotBlank()) {

                                    clipboard.setText(
                                        AnnotatedString(
                                            resultText
                                        )
                                    )
                                }
                            }
                        ) {

                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "复制"
                            )
                        }
                    }
                }

                /*
                 * 翻译结果
                 */
                item {

                    Card(

                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(18.dp)
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(16.dp)
                        ) {

                            Text(
                                "翻译结果",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelLarge
                            )

                            Spacer(
                                Modifier.height(8.dp)
                            )

                            Text(
                                resultText.ifBlank {
                                    "翻译结果会显示在这里"
                                },

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyLarge
                            )
                        }
                    }
                }

                /*
                 * 图片功能
                 */
                item {

                    Text(
                        statusText,
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )

                    Row(

                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedButton(

                            onClick = {
                                galleryLauncher.launch("image/*")
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Icon(
                                Icons.Default.Image,
                                contentDescription = null
                            )

                            Spacer(
                                Modifier.size(6.dp)
                            )

                            Text("选择图片")
                        }

                        OutlinedButton(

                            onClick = {
                                openCamera()
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null
                            )

                            Spacer(
                                Modifier.size(6.dp)
                            )

                            Text("拍照翻译")
                        }
                    }
                }

                /*
                 * OCR 结果
                 */
                if (ocrLines.isNotEmpty()) {

                    item {

                        Text(
                            "图片文字",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Text(
                            "点击任意文字行即可单独翻译",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium
                        )
                    }

                    items(ocrLines) { line ->

                        Surface(

                            modifier =
                                Modifier.fillMaxWidth(),

                            shape =
                                RoundedCornerShape(14.dp),

                            tonalElevation = 2.dp,

                            onClick = {

                                inputText = line

                                translate(line)
                            }
                        ) {

                            Text(
                                line,
                                modifier =
                                    Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                item {

                    Spacer(
                        Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxLanguageButton(
    text: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {

    androidx.compose.foundation.layout.Box {

        OutlinedButton(
            onClick = {
                onExpandedChange(true)
            }
        ) {
            Text(text)
        }

        DropdownMenu(

            expanded = expanded,

            onDismissRequest = {
                onExpandedChange(false)
            }
        ) {

            content()
        }
    }
}