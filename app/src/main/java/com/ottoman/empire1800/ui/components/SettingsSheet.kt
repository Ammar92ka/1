package com.ottoman.empire1800.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.services.AiClient
import com.ottoman.empire1800.services.AiException
import com.ottoman.empire1800.services.AiSettings
import com.ottoman.empire1800.services.aiProviderById
import com.ottoman.empire1800.services.aiProviders
import com.ottoman.empire1800.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSettingsSheet(
    initial: AiSettings,
    soundMuted: Boolean,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit,
    onSave: suspend (AiSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var providerId by remember(initial) { mutableStateOf(initial.provider) }
    var apiKey by remember(initial) { mutableStateOf(initial.apiKey) }
    var model by remember(initial) { mutableStateOf(initial.model) }
    var useSearch by remember(initial) { mutableStateOf(initial.useSearch) }
    var showKey by remember { mutableStateOf(false) }
    var showCustomModel by remember(initial) { mutableStateOf(initial.model.trim().isNotEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testDiagnostic by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }

    fun current() = AiSettings(
        provider = providerId,
        apiKey = apiKey.trim(),
        model = model.trim(),
        useSearch = useSearch,
    )

    val provider = aiProviderById(providerId)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "الذكاء الاصطناعي (مطلوب للعب)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Gold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "أدخل مفتاح API فقط وسيختار التطبيق تلقائيا أسرع وأفضل نموذج متوفر على مفتاحك مع تبديل تلقائي عند الازدحام. يحفظ مفتاحك على هذا الجهاز فقط.",
                color = AppColors.Muted,
                lineHeight = 22.sp,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                aiProviders.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = providerId == p.id,
                        onClick = {
                            providerId = p.id
                            model = ""
                            testMessage = null
                            testDiagnostic = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = aiProviders.size,
                        ),
                    ) {
                        Text(p.name)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testMessage = null
                        testDiagnostic = null
                    },
                    label = { Text("مفتاح API") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "إخفاء المفتاح" else "إظهار المفتاح",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Navy,
                border = BorderStroke(1.dp, AppColors.CardBorder),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "اختيار النموذج:",
                            color = AppColors.Muted,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = if (model.trim().isNotEmpty()) {
                                "مخصّص (${model.trim()})"
                            } else {
                                "تلقائي (الأفضل والأسرع المتوفر)"
                            },
                            color = AppColors.Good,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomModel = !showCustomModel }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "تخصيص النموذج (اختياري للمتقدمين)",
                            color = AppColors.Gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (showCustomModel) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = AppColors.Gold,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    AnimatedVisibility(visible = showCustomModel) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                OutlinedTextField(
                                    value = model,
                                    onValueChange = {
                                        model = it
                                        testMessage = null
                                        testDiagnostic = null
                                    },
                                    label = { Text("اسم النموذج المخصص") },
                                    placeholder = { Text(provider.defaultModel) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            if (provider.supportsSearch) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التحقق من الوقائع عبر بحث الويب", color = AppColors.White)
                        Text(
                            text = "المرجع التاريخي المدمج (1800–1970) يضبط الدقة تلقائيا؛ بحث الويب يتطلب باقة Gemini داعمة للبحث أو مدفوعة (ويتراجع تلقائيا عند 429) ويزيد زمن الانتظار.",
                            color = AppColors.Muted,
                            fontSize = 12.5.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = useSearch, onCheckedChange = { useSearch = it })
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("المؤثرات الصوتية للديوان", color = AppColors.White)
                    Text(
                        text = "نغمة ختم الفرمان وتنبيهات الأزمات.",
                        color = AppColors.Muted,
                        fontSize = 12.5.sp,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(checked = !soundMuted, onCheckedChange = { onToggleMute() })
            }

            if (testMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = if (testOk) Icons.Filled.CheckCircleOutline else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (testOk) AppColors.Good else AppColors.Bad,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = testMessage ?: "",
                            color = if (testOk) AppColors.Good else AppColors.Bad,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!testDiagnostic.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = testDiagnostic ?: "",
                                color = AppColors.Muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = !testing,
                    onClick = {
                        scope.launch {
                            onSave(current())
                            onDismiss()
                        }
                    },
                ) {
                    Text("حفظ")
                }

                OutlinedButton(
                    enabled = !testing && apiKey.trim().isNotEmpty(),
                    onClick = {
                        val s = current()
                        scope.launch {
                            testing = true
                            testMessage = null
                            testDiagnostic = null
                            try {
                                val (_, usedModel) = AiClient.fromSettings(s)
                                    .generateTextWithModel("أجب بكلمة واحدة فقط: جاهز")
                                testOk = true
                                testMessage = "الاتصال ناجح • النموذج: $usedModel"
                            } catch (e: AiException) {
                                testOk = false
                                testMessage = e.message
                                testDiagnostic = e.diagnostic
                            } catch (_: Throwable) {
                                testOk = false
                                testMessage = "فشل الاختبار لسبب غير متوقع."
                            } finally {
                                testing = false
                            }
                        }
                    },
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Text("اختبار الاتصال")
                    }
                }

                TextButton(
                    enabled = !testing,
                    onClick = {
                        apiKey = ""
                        testDiagnostic = null
                        val s = current()
                        scope.launch {
                            onSave(s)
                            testMessage = "تم مسح المفتاح من هذا الجهاز."
                            testOk = true
                        }
                    },
                ) {
                    Text("مسح المفتاح")
                }
            }
        }
    }
}
