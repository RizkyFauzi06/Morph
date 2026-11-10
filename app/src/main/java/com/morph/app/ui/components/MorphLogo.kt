package com.morph.app.ui.components
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morph.app.R

@Composable
fun MorphLogo(
    size: Dp = 48.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(13.dp))
            .padding(2.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_morph_logo),
            contentDescription = "Morph",
            modifier = Modifier.fillMaxSize()
        )
    }
}