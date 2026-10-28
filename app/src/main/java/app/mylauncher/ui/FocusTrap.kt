package app.mylauncher.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusProperties

/** Keeps D-pad focus inside an overlay: the home rows behind it stay focusable otherwise. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.trapFocus(): Modifier = focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
