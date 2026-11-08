package com.jhaiian.clint.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.delegates.closeFindBar
import com.jhaiian.clint.browser.delegates.onFindNext
import com.jhaiian.clint.browser.delegates.onFindQueryChanged
import com.jhaiian.clint.ui.theme.LocalClintColors
import kotlinx.coroutines.flow.drop

/**
 * Find-in-page bar that replaces the whole address bar row, mirroring the
 * activated web search field (same size and layout as the search overlay's
 * field row): a back button plus a rounded field with the query input, a live
 * match counter and up/down navigation arrows inside. The page stays visible
 * underneath so matches are highlighted in place.
 */
@Composable
internal fun FindInPageBar(activity: MainActivity, modifier: Modifier = Modifier) {
    val colors = LocalClintColors.current
    val state = activity.uiState
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val fieldState = remember { TextFieldState(state.findQuery, TextRange(state.findQuery.length)) }

    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.drop(1).collect { activity.onFindQueryChanged(it) }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val matchCountDesc = stringResource(R.string.find_match_count_desc, state.findMatchOrdinal + 1, state.findMatchCount)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { activity.closeFindBar() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colors.iconTint
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.addressBarColor)
                .border(1.5.dp, colors.primary.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(start = 16.dp, end = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.menu_find_in_page),
                tint = colors.iconTint,
                modifier = Modifier.size(20.dp)
            )
            Box(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                if (fieldState.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.find_in_page_hint),
                        color = colors.secondaryText,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                com.jhaiian.clint.privacy.IncognitoKeyboardScope(enabled = state.isIncognito && com.jhaiian.clint.privacy.IncognitoKeyboard.isEnabled(activity)) {
                    BasicTextField(
                        state = fieldState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        onKeyboardAction = { activity.onFindNext(backwards = false) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                }
            }
            if (fieldState.text.isNotEmpty()) {
                Text(
                    text = "${state.findMatchOrdinal + 1}/${state.findMatchCount}",
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .semantics { contentDescription = matchCountDesc }
                )
                FieldIconButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.find_previous_match)) {
                    activity.onFindNext(backwards = true)
                }
                FieldIconButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.find_next_match)) {
                    activity.onFindNext(backwards = false)
                }
            }
        }
    }
}

@Composable
private fun FieldIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalClintColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.secondaryText,
            modifier = Modifier.size(20.dp)
        )
    }
}
