package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudienceRecord
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Slate800
import com.example.util.VcfGenerator

@Composable
fun AudienceDetailDialog(
    audience: AudienceRecord,
    onDismiss: () -> Unit,
    onSaveLatinName: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var latinNameInput by remember { mutableStateOf(audience.fullNameLatin) }
    val clipboardManager = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Emerald400.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Contact icon",
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Audience vCard Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Row #${audience.rowNumber} in CSV",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Persian original vs Latin
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Persian Name (Original):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = audience.fullNamePersian.ifBlank { "N/A" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Cyan400
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Latin Transliteration (vCard FN):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isEditing) {
                            OutlinedTextField(
                                value = latinNameInput,
                                onValueChange = { latinNameInput = it },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_latin_name_input")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    onSaveLatinName(latinNameInput)
                                    isEditing = false
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("save_latin_name_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                            ) {
                                Text("Save Name", color = Color.Black)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = audience.fullNameLatin,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                OutlinedButton(
                                    onClick = { isEditing = true },
                                    modifier = Modifier.testTag("toggle_edit_name_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit transliterated name",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit")
                                }
                            }
                        }
                    }
                }

                // Attributes List
                AttributeRow(
                    icon = Icons.Default.Badge,
                    label = "National Code",
                    value = audience.nationalCode,
                    highlight = audience.matchedPrefix
                )

                AttributeRow(
                    icon = Icons.Default.Phone,
                    label = "Mobile",
                    value = audience.mobile
                )

                if (audience.cardNo.isNotBlank()) {
                    AttributeRow(
                        icon = Icons.Default.CreditCard,
                        label = "Card Number",
                        value = audience.cardNo
                    )
                }

                if (audience.birthDate.isNotBlank()) {
                    AttributeRow(
                        icon = Icons.Default.CalendarMonth,
                        label = "Birth Date",
                        value = audience.birthDate
                    )
                }

                // vCard Preview Snippet
                Text(
                    text = "Generated vCard 3.0 Snippet:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF070E1A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val snippet = remember(audience) { VcfGenerator.recordToVcard(audience) }
                    Text(
                        text = snippet,
                        modifier = Modifier.padding(10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF86EFAC)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val snippet = VcfGenerator.recordToVcard(audience)
                    clipboardManager.setText(AnnotatedString(snippet))
                    copiedNotice = true
                },
                modifier = Modifier.testTag("copy_single_vcf_button")
            ) {
                Icon(
                    imageVector = if (copiedNotice) Icons.Default.Check else Icons.Default.Badge,
                    contentDescription = "Copy vCard"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (copiedNotice) "Copied!" else "Copy vCard")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_audience_detail_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AttributeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    highlight: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Slate800.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Cyan400,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))

        if (highlight != null && value.startsWith(highlight)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Amber400.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = highlight,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Amber400
                    )
                }
                Text(
                    text = value.substring(highlight.length),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        } else {
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
