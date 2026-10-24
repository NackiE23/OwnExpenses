package com.nackie.ownexpenses.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nackie.ownexpenses.presentation.categories.CategoryEditor
import com.nackie.ownexpenses.presentation.categories.CategoryPalette
import com.nackie.ownexpenses.ui.components.CategoryIconBadge
import com.nackie.ownexpenses.ui.components.categoryIconKeys
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.category_editor_color
import com.nackie.ownexpenses.ui.resources.category_editor_edit
import com.nackie.ownexpenses.ui.resources.category_editor_icon
import com.nackie.ownexpenses.ui.resources.category_editor_name
import com.nackie.ownexpenses.ui.resources.category_editor_new
import com.nackie.ownexpenses.ui.resources.category_editor_new_sub
import com.nackie.ownexpenses.ui.resources.dialog_cancel
import com.nackie.ownexpenses.ui.resources.dialog_save
import com.nackie.ownexpenses.ui.resources.ic_check
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Name, color and icon of a new or existing category; a preview shows the resulting tile icon. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CategoryEditorSheet(
    editor: CategoryEditor,
    onNameChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onIconChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (editor.isNew) focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryIconBadge(icon = editor.icon, color = Color(editor.color))
                Text(
                    text =
                        when {
                            !editor.isNew -> {
                                stringResource(Res.string.category_editor_edit)
                            }

                            editor.parent != null -> {
                                stringResource(
                                    Res.string.category_editor_new_sub,
                                    editor.parent!!.name,
                                )
                            }

                            else -> {
                                stringResource(Res.string.category_editor_new)
                            }
                        },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(Res.string.category_editor_name)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                // "Done" saves: while typing, the keyboard usually covers the Save button.
                keyboardActions = KeyboardActions(onDone = { if (editor.canSave) onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            SectionLabel(stringResource(Res.string.category_editor_color))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CategoryPalette.colors.forEach { color ->
                    ColorSwatch(
                        color = Color(color),
                        selected = color == editor.color,
                        onClick = { onColorChange(color) },
                    )
                }
            }
            SectionLabel(stringResource(Res.string.category_editor_icon))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categoryIconKeys.forEach { icon ->
                    IconOption(
                        icon = icon,
                        color = Color(editor.color),
                        selected = icon == editor.icon,
                        onClick = { onIconChange(icon) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
                Button(onClick = onSave, enabled = editor.canSave) { Text(stringResource(Res.string.dialog_save)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .background(color, CircleShape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun IconOption(
    icon: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border =
        if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent)
    Box(
        modifier =
            modifier
                .size(52.dp)
                .border(border, RoundedCornerShape(14.dp))
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        CategoryIconBadge(icon = icon, color = color, size = 40.dp)
    }
}
