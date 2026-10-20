package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*

@Composable
fun HomeSearchInput(value: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != text.text) text = TextFieldValue(value, TextRange(value.length))
    }
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        Modifier.fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .74f))
            .border(1.dp, Rule, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxSize()
                .padding(start = 20.dp, end = if (value.isBlank()) 20.dp else 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(StakIcons.Search, null, Modifier.size(16.dp), tint = Muted)
            BasicTextField(
                text,
                {
                    text = it
                    // Retain the IME's composition; don't issue searches for unfinished
                    // composition.
                    if (it.composition == null) onChange(it.text)
                },
                Modifier.weight(1f).semantics { contentDescription = "Search" },
                singleLine = true,
                textStyle =
                    TextStyle(
                        fontFamily = BodyFont,
                        fontSize = 14.sp,
                        lineHeight = 19.6.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    Box {
                        if (text.text.isEmpty())
                            Text(
                                "Movie, series or IMDb ID",
                                fontSize = 14.sp,
                                lineHeight = 19.6.sp,
                                color =
                                    if (MaterialTheme.colorScheme.background.red < .5f)
                                        Color(0xff909090)
                                    else Color(0xff737373),
                            )
                        inner()
                    }
                },
            )
        }
        if (value.isNotEmpty())
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(40.dp, 44.dp)
                    .semantics { contentDescription = "Clear search" }
                    .clickable {
                        text = TextFieldValue("")
                        onClear()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(StakIcons.X, null, Modifier.size(14.dp), tint = Muted)
            }
    }
}
