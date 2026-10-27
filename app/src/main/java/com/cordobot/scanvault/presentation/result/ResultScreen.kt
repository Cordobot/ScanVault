package com.cordobot.scanvault.presentation.result

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cordobot.scanvault.domain.CodeType
import com.cordobot.scanvault.domain.Scan
import com.cordobot.scanvault.domain.ScanRepository
import com.cordobot.scanvault.presentation.components.TypeIcon
import com.cordobot.scanvault.presentation.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScanRepository
) : ViewModel() {
    private val id: Long = checkNotNull(savedStateHandle["id"])

    val scan: StateFlow<Scan?> = repository.observeScan(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite() = viewModelScope.launch { repository.toggleFavorite(id) }
    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repository.delete(id)
        onDeleted()
    }
}

@Composable
fun ResultScreen(onBack: () -> Unit, viewModel: ResultViewModel = hiltViewModel()) {
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Text("Resultado", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            scan?.let { current ->
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        if (current.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (current.isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                        tint = if (current.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { viewModel.delete(onBack) }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
            }
        }
        val current = scan
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            ResultContent(current, context)
        }
    }
}

@Composable
private fun ResultContent(scan: Scan, context: Context) {
    val content = scan.content
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeIcon(content.type, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(content.type.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(content.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text("${scan.format} · ${formatDate(scan.scannedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content.details.forEach { (label, value) ->
                    Column {
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        content.openUri?.let { uri ->
            Button(onClick = { context.open(uri) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (content.type) {
                        CodeType.URL -> "Abrir enlace"
                        CodeType.PHONE -> "Llamar"
                        CodeType.EMAIL -> "Escribir correo"
                        CodeType.SMS -> "Enviar SMS"
                        CodeType.GEO -> "Ver en el mapa"
                        CodeType.PRODUCT -> "Buscar producto"
                        else -> "Abrir"
                    }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // For a Wi-Fi network the useful thing to copy is the password
            val copyValue = content.details.firstOrNull { it.first == "Contraseña" }?.second ?: scan.rawValue
            FilledTonalButton(onClick = { context.copy(copyValue) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (content.type == CodeType.WIFI) "Copiar clave" else "Copiar")
            }
            FilledTonalButton(onClick = { context.share(scan.rawValue) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Compartir")
            }
        }
    }
}

private fun Context.open(uri: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No hay una app para abrir esto", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.copy(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ScanVault", text))
    Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
}

private fun Context.share(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, "Compartir"))
}
