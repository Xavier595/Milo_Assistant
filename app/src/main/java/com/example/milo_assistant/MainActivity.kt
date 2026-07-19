package com.example.milo_assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiloScreen()
        }
    }
}

@Composable
private fun MiloScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05080C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiloEye()
                MiloEye()
            }
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "MILO",
                color = Color(0xFF9FE7FF),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "En espera",
                color = Color(0xFF77838E),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun MiloEye() {
    Box(
        modifier = Modifier
            .size(
                width = 82.dp,
                height = 110.dp
            )
            .clip(RoundedCornerShape(42.dp))
            .background(Color(0xFFE1F8FF)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(Color(0xFF07151C))
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 720
)
@Composable
private fun MiloScreenPreview() {
    MiloScreen()
}