package com.masar.portal.ui.screens

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masar.portal.model.IFormItem
import com.masar.portal.network.MasarApi
import com.masar.portal.ui.components.*
import com.masar.portal.ui.theme.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private val TYPE_LABELS = mapOf(
    "leave"    to ("📅 طلب إجازة"   to Color(0xFF8AA6E8)),
    "warning"  to ("⚠️ إنذار رسمي"   to Amber),
    "advance"  to ("💰 طلب سلفة"    to Green),
)

@Composable
fun IFormsScreen(
    baseUrl: String,
    nid: String,
    token: String,
) {
    val scope = rememberCoroutineScope()
    var forms by remember { mutableStateOf<List<IFormItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedForm by remember { mutableStateOf<IFormItem?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        val resp = MasarApi(baseUrl).fetchIForms(nid, token)
        forms = if (resp.ok) resp.forms else emptyList()
        loading = false
    }

    if (selectedForm != null) {
        IFormDetailScreen(
            baseUrl = baseUrl,
            nid = nid,
            token = token,
            form = selectedForm!!,
            onBack = { selectedForm = null },
            onSubmitted = {
                selectedForm = null
                refreshKey++
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("النماذج التفاعلية", style = MaterialTheme.typography.headlineSmall,
            color = TxtPrimary, fontWeight = FontWeight.Bold)
        Text("اضغط على نموذج لمراجعته وتوقيعه أو رفضه.",
            style = MaterialTheme.typography.bodySmall, color = TxtDim)

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandRed)
            }
            return@Column
        }

        if (forms.isEmpty()) {
            EmptyFormsState()
            return@Column
        }

        val pending = forms.filter { it.status == "pending" }
        if (pending.isNotEmpty()) {
            MasarCard {
                SectionTitle("⏰ تنتظر التوقيع (${pending.size})")
                Spacer(Modifier.height(10.dp))
                pending.forEach { f ->
                    FormCard(f, onClick = { selectedForm = f })
                    if (f != pending.last()) Spacer(Modifier.height(8.dp))
                }
            }
        }

        val signed = forms.filter { it.status != "pending" }
        if (signed.isNotEmpty()) {
            MasarCard {
                SectionTitle("📋 السجل (${signed.size})")
                Spacer(Modifier.height(10.dp))
                signed.forEach { f ->
                    FormCard(f, onClick = { selectedForm = f })
                    if (f != signed.last()) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun FormCard(form: IFormItem, onClick: () -> Unit) {
    val (label, color) = TYPE_LABELS[form.type] ?: ("📄 ${form.type}" to TxtSoft)
    val (badge, badgeColor) = when (form.status) {
        "pending"  -> ("بانتظار توقيعك" to Amber)
        "filled"   -> ("وُقّع - بانتظار اعتماد" to Color(0xFF8AA6E8))
        "approved" -> ("✅ معتمد" to Green)
        "rejected" -> ("❌ مرفوض" to Red)
        else -> (form.status to TxtSoft)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Ink2,
        border = androidx.compose.foundation.BorderStroke(1.dp, LineDim),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall,
                    color = color, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(
                    color = badgeColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor, fontWeight = FontWeight.Bold)
                }
            }
            if (!form.template_title.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(form.template_title, style = MaterialTheme.typography.bodySmall, color = TxtPrimary)
            }
            if (!form.created_at.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("أُرسل: ${form.created_at}", style = MaterialTheme.typography.labelSmall, color = TxtDim)
            }
        }
    }
}

@Composable
private fun EmptyFormsState() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Description, contentDescription = null,
                tint = TxtDim, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("لا توجد نماذج بعد", style = MaterialTheme.typography.bodyMedium, color = TxtDim)
            Spacer(Modifier.height(4.dp))
            Text("عند إرسال نموذج من المشرف ستظهر هنا.",
                style = MaterialTheme.typography.labelSmall, color = TxtDim)
        }
    }
}

@Composable
private fun IFormDetailScreen(
    baseUrl: String,
    nid: String,
    token: String,
    form: IFormItem,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var detailLoading by remember { mutableStateOf(true) }
    var templateHtml by remember { mutableStateOf("") }
    var formData by remember { mutableStateOf("") }

    // قائمة النقاط للتوقيع (قائمة بدل Path معقّد)
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var showRejectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(form.id) {
        detailLoading = true
        val resp = MasarApi(baseUrl).fetchIFormDetail(nid, token, form.id)
        if (resp.ok) {
            templateHtml = resp.template_html ?: ""
            formData = resp.form?.data_json ?: ""
        }
        detailLoading = false
    }

    val (label, color) = TYPE_LABELS[form.type] ?: ("📄 نموذج" to TxtSoft)
    val isPending = form.status == "pending"
    val hasSignature = strokes.isNotEmpty() || currentStroke.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = TxtPrimary)
            }
            Text(label, style = MaterialTheme.typography.titleLarge,
                color = color, fontWeight = FontWeight.Bold)
        }

        if (detailLoading) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandRed)
            }
            return@Column
        }

        MasarCard {
            SectionTitle("📄 محتوى النموذج")
            Spacer(Modifier.height(10.dp))
            if (formData.isNotBlank() && formData != "{}") {
                Text(formData, style = MaterialTheme.typography.bodySmall, color = TxtPrimary)
                Spacer(Modifier.height(8.dp))
            }
            if (templateHtml.isNotBlank()) {
                val plainText = templateHtml
                    .replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                Text(plainText.take(800), style = MaterialTheme.typography.bodySmall, color = TxtPrimary)
            }
            if (!form.rejection_reason.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(color = Red.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("سبب الرفض:", style = MaterialTheme.typography.labelSmall, color = Red, fontWeight = FontWeight.Bold)
                        Text(form.rejection_reason, style = MaterialTheme.typography.bodySmall, color = TxtPrimary)
                    }
                }
            }
        }

        if (isPending) {
            MasarCard {
                SectionTitle("✍️ توقيعك الإلكتروني")
                Spacer(Modifier.height(8.dp))
                Text(
                    "ارسم توقيعك بإصبعك داخل المربع أدناه.",
                    style = MaterialTheme.typography.labelSmall, color = TxtDim
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, LineDim, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes.add(currentStroke.toList())
                                        currentStroke = emptyList()
                                    }
                                },
                                onDragCancel = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes.add(currentStroke.toList())
                                        currentStroke = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        canvasWidth = size.width.toInt()
                        canvasHeight = size.height.toInt()
                        // ارسم كل الـ strokes
                        val allStrokes = strokes + listOf(currentStroke)
                        allStrokes.forEach { stroke ->
                            for (i in 1 until stroke.size) {
                                drawLine(
                                    color = Color.Black,
                                    start = stroke[i - 1],
                                    end = stroke[i],
                                    strokeWidth = 5f,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }
                    if (!hasSignature) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("اضغط هنا وارسم توقيعك", color = Color.Gray)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            strokes.clear()
                            currentStroke = emptyList()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("مسح") }

                    Button(
                        onClick = {
                            if (!hasSignature) {
                                result = "⚠️ يجب رسم التوقيع أولاً"
                                return@Button
                            }
                            scope.launch {
                                submitting = true
                                val base64 = strokesToBase64(strokes.toList(), canvasWidth, canvasHeight)
                                val resp = MasarApi(baseUrl).signIForm(nid, token, form.id, base64, "sign")
                                submitting = false
                                if (resp.ok) {
                                    result = "✅ تم توقيع النموذج بنجاح وأُرسل للمشرف."
                                    kotlinx.coroutines.delay(1500)
                                    onSubmitted()
                                } else {
                                    result = "❌ ${resp.error ?: "فشل الإرسال"}"
                                }
                            }
                        },
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Green),
                        enabled = !submitting,
                    ) {
                        Text(if (submitting) "جارٍ الإرسال..." else "✓ توقيع وإرسال")
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                    enabled = !submitting,
                ) { Text("رفض النموذج") }

                if (result != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(result!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result!!.startsWith("✅")) Green else Red)
                }
            }
        }
    }

    if (showRejectDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("رفض النموذج") },
            text = {
                Column {
                    Text("يرجى ذكر سبب الرفض:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("السبب...") },
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (reason.isBlank()) return@TextButton
                        scope.launch {
                            submitting = true
                            val resp = MasarApi(baseUrl).signIForm(nid, token, form.id, reason, "reject")
                            submitting = false
                            showRejectDialog = false
                            if (resp.ok) {
                                result = "✅ تم رفض النموذج وإبلاغ المشرف."
                                kotlinx.coroutines.delay(1500)
                                onSubmitted()
                            } else {
                                result = "❌ ${resp.error ?: "فشل الإرسال"}"
                            }
                        }
                    }
                ) { Text("رفض", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("إلغاء") }
            },
        )
    }
}

// تحويل قائمة الـ strokes إلى Base64 PNG (باستخدام Android Canvas مباشرة)
private fun strokesToBase64(strokes: List<List<Offset>>, width: Int, height: Int): String {
    if (width <= 0 || height <= 0 || strokes.isEmpty()) return ""

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 5f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }

    strokes.forEach { stroke ->
        for (i in 1 until stroke.size) {
            canvas.drawLine(
                stroke[i - 1].x, stroke[i - 1].y,
                stroke[i].x, stroke[i].y,
                paint
            )
        }
    }

    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    val bytes = output.toByteArray()
    bitmap.recycle()
    return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}
