package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.services.SultanRules
import com.ottoman.empire1800.ui.theme.AppColors

@Composable
fun SuccessionDialog(
    state: GameState,
    onChoose: (Int) -> Unit,
) {
    val current = state.sultan
    val candidates = SultanRules.candidatesFor(current)

    AlertDialog(
        onDismissRequest = {},
        containerColor = AppColors.Card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = AppColors.Gold,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "انتقال العرش العثماني",
                        color = AppColors.Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "انتهت سلطنة ${current.name}. اختر السلطان الجديد (+4 شرعية، +2 استقرار سياسي)",
                        color = AppColors.Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                candidates.forEachIndexed { idx, cand ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(idx) },
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.Navy,
                        border = BorderStroke(1.dp, AppColors.Gold.copy(alpha = 0.5f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = cand.name,
                                    color = AppColors.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.5.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppColors.Gold.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, AppColors.Gold.copy(alpha = 0.45f)),
                                ) {
                                    Text(
                                        text = SultanRules.traitLabel(cand.trait),
                                        color = AppColors.Gold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cand.summary,
                                color = AppColors.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = AppColors.Card,
                            ) {
                                Text(
                                    text = cand.bonus,
                                    color = AppColors.Good,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}
