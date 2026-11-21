package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.services.GazetteRules
import com.ottoman.empire1800.ui.theme.AppColors

@Composable
fun GazetteCard(
    state: GameState,
    showHistory: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val unlocked = GazetteRules.isUnlocked(state)
    val currentPeriod = GameEngine.periodLabel(state.round, state.ppy)
    val issue = GazetteRules.latestOrPreview(state, currentPeriod)

    if (!unlocked) {
        if (!showHistory) return
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = AppColors.Card,
            border = BorderStroke(1.dp, AppColors.CardBorder),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = AppColors.Gold.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "وقائع إسطنبول",
                        color = AppColors.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "صحيفة السلطنة الرسمية مغلقة حاليا. تُفتح بعد إتمام مشروع المطبعة، أو بلوغ التعليم 25، أو إنشاء أول مطحنة ورق.",
                    color = AppColors.Muted,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                )
            }
        }
        return
    }

    if (issue == null) return
    val archivedIssues = if (showHistory && state.gazette.size > 1) {
        state.gazette.drop(1).take(4)
    } else {
        emptyList()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Card,
        border = BorderStroke(1.dp, AppColors.Gold.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            HorizontalDivider(color = AppColors.Gold.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Newspaper,
                        contentDescription = null,
                        tint = AppColors.Gold,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "وقائع إسطنبول",
                        color = AppColors.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                }
                Text(
                    text = GazetteRules.policyLabel(issue.policy),
                    color = AppColors.Gold,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "العدد الرسمي · ${issue.period}",
                color = AppColors.Muted,
                fontSize = 11.5.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.Gold.copy(alpha = 0.35f))

            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                issue.headlines.forEach { headline ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = AppColors.Navy,
                        border = BorderStroke(1.dp, AppColors.CardBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "◆",
                                color = AppColors.Gold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = headline,
                                color = AppColors.White,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                            )
                        }
                    }
                }
            }

            if (archivedIssues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = AppColors.CardBorder.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "الأعداد السابقة",
                    color = AppColors.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    archivedIssues.forEach { past ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.Navy.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, AppColors.CardBorder),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text(
                                    text = "${past.period} · ${GazetteRules.policyLabel(past.policy)}",
                                    color = AppColors.Muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                past.headlines.forEach { h ->
                                    Text(
                                        text = "• $h",
                                        color = AppColors.White,
                                        fontSize = 11.5.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
