package com.morph.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morph.app.R

@Composable
fun MorphLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier.size(size)
    ) {
        Image(
            painter = painterResource(R.drawable.morph_logo),
            contentDescription = "Morph",
            modifier = Modifier.fillMaxSize()
        )
    }
}
