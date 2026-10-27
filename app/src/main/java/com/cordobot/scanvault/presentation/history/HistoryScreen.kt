package com.cordobot.scanvault.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cordobot.scanvault.domain.FilterHistoryUseCase
import com.cordobot.scanvault.domain.Scan
import com.cordobot.scanvault.domain.ScanRepository
import com.cordobot.scanvault.presentation.components.TypeIcon
import com.cordobot.scanvault.presentation.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryUiState(
    val scans: List<Scan> = emptyList(),
    val total: Int = 0,
    val query: String = "",
    val favoritesOnly: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: ScanRepository,
    filter: FilterHistoryUseCase
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)

    val uiState: StateFlow<HistoryUiState> = combine(repository.observeHistory(), query, favoritesOnly) { all, q, fav ->
        HistoryUiState(filter(all, q, fav), all.size, q, fav)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFavoritesOnlyChange(value: Boolean) {
        favoritesOnly.value = value
    }
}

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Historial",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp)
        )
        Text(
            if (state.total == 1) "1 código guardado en este teléfono" else "${state.total} códigos guardados en este teléfono",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Buscar en el historial") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        FilterChip(
            selected = state.favoritesOnly,
            onClick = { viewModel.onFavoritesOnlyChange(!state.favoritesOnly) },
            label = { Text("Solo favoritos") },
            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (state.scans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (state.total == 0) "Todavía no has escaneado nada. Los códigos que leas aparecerán aquí."
                    else "Nada coincide con la búsqueda",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.scans, key = { it.id }) { scan -> HistoryItem(scan, onClick = { onOpen(scan.id) }) }
            }
        }
    }
}

@Composable
private fun HistoryItem(scan: Scan, onClick: () -> Unit) {
    val content = scan.content
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypeIcon(content.type)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(content.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${content.type.label} · ${scan.format} · ${formatDate(scan.scannedAt)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        if (scan.isFavorite) Icon(Icons.Default.Star, contentDescription = "Favorito", tint = Color(0xFFFFC107))
    }
}
