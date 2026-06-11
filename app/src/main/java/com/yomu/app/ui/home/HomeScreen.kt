package com.yomu.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToModels: () -> Unit = {},
    onRequestScreenCapture: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Yomu",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Manga Translation",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Service Status", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = state.isServiceRunning,
                        onCheckedChange = { viewModel.toggleService() }
                    )
                }
                
                if (state.isServiceRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the orange button to translate manga",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text("Mode: ${state.translationMode.replaceFirstChar { it.uppercase() }}",
                    fontSize = 14.sp)
                
                Text("Pages today: ${state.pagesTranslatedToday}",
                    fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text("Models: ${state.modelStatus}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedButton(
            onClick = onNavigateToModels,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Models")
        }
    }
}
