package com.bangdream.pet.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bangdream.pet.I18n
import com.bangdream.pet.llm.CatalogFailure
import com.bangdream.pet.llm.CatalogModel
import com.bangdream.pet.llm.InvalidCatalog
import com.bangdream.pet.llm.LlmModel
import com.bangdream.pet.llm.LlmModelCatalog
import com.bangdream.pet.llm.LlmProvider
import com.bangdream.pet.llm.catalogUrl
import com.bangdream.pet.llm.modelId
import com.bangdream.pet.ui.design.standardTween
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CatalogView { INITIAL, LOADING, EMPTY, ERROR, RESULTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPicker(provider: LlmProvider, onDismiss: () -> Unit, onAdd: (List<LlmModel>) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val catalog = remember { LlmModelCatalog() }
    val connection = provider.connection
    val alreadyAdded = remember(provider.models, connection.protocol) {
        provider.models.mapTo(HashSet()) { modelId(connection.protocol, it.id) }
    }
    var entries by remember(connection, provider.modelsUrl) { mutableStateOf<List<CatalogModel>?>(null) }
    var error by remember(connection, provider.modelsUrl) { mutableStateOf<String?>(null) }
    var request by remember(connection, provider.modelsUrl) { mutableIntStateOf(0) }
    var loading by remember(connection, provider.modelsUrl) { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(connection, provider.modelsUrl) { mutableStateOf(arrayListOf<String>()) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val canFetch = provider.catalogUrl() != null && connection.apiKey.isNotBlank()
    val filtered = remember(entries, query) {
        val search = query.trim()
        entries.orEmpty().filter { it.model.id.contains(search, true) || it.model.label.contains(search, true) }
    }
    val selectedModels = remember(entries, selected, alreadyAdded) {
        val ids = selected.toSet() - alreadyAdded
        entries.orEmpty().filter { it.compatible != false && it.model.id in ids }.map(CatalogModel::model)
    }
    val view = when {
        entries?.isNotEmpty() == true -> CatalogView.RESULTS
        loading -> CatalogView.LOADING
        error != null -> CatalogView.ERROR
        entries != null -> CatalogView.EMPTY
        else -> CatalogView.INITIAL
    }

    fun fetch() {
        if (canFetch && !loading && !closing) {
            loading = true
            error = null
            request++
        }
    }

    fun addAndClose(models: List<LlmModel>) {
        if (closing) return
        closing = true
        onAdd(models)
        scope.launch {
            sheet.hide()
            onDismiss()
        }
    }

    LaunchedEffect(connection, provider.modelsUrl, request) {
        if (request == 0) return@LaunchedEffect
        try {
            entries = withContext(Dispatchers.Default) {
                catalog.fetch(provider).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.model.label })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = when (failure) {
                is CatalogFailure -> when (failure.status) {
                    401, 403 -> I18n.t("llm_catalog_auth")
                    404, 405, 501 -> I18n.t("llm_catalog_unsupported")
                    else -> I18n.t("llm_catalog_http", failure.status)
                }
                is InvalidCatalog, is IllegalArgumentException -> I18n.t("llm_catalog_invalid")
                else -> I18n.t("llm_catalog_network")
            }
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(I18n.t("llm_model_add"), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = ::fetch, enabled = canFetch && !loading && !closing) {
                    Text(I18n.t(if (entries == null) "llm_catalog_get" else "llm_catalog_refresh"))
                }
                TextButton(onClick = { manual = true }, enabled = !closing) { Text(I18n.t("llm_model_manual")) }
            }
            if (!canFetch) Text(I18n.t("llm_catalog_requires_connection"), style = MaterialTheme.typography.bodySmall)
            if (entries?.isNotEmpty() == true) LlmField(query, { query = it }, "llm_catalog_search")
            AnimatedVisibility(loading, enter = fadeIn(standardTween()), exit = fadeOut(standardTween())) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            error?.takeIf { view == CatalogView.RESULTS }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            AnimatedContent(
                targetState = view,
                transitionSpec = { fadeIn(standardTween()) togetherWith fadeOut(standardTween()) },
                label = "catalogState",
            ) { state ->
                when (state) {
                    CatalogView.INITIAL -> Text(I18n.t("llm_catalog_hint"))
                    CatalogView.LOADING -> Text(I18n.t("llm_catalog_loading"))
                    CatalogView.EMPTY -> Text(I18n.t("llm_catalog_empty"))
                    CatalogView.ERROR -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    CatalogView.RESULTS -> Box {
                        if (filtered.isEmpty()) Text(I18n.t("llm_catalog_no_results"))
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(filtered, key = { it.model.id }) { item ->
                                val model = item.model
                                val existing = model.id in alreadyAdded
                                val checked = existing || model.id in selected
                                val enabled = !existing && item.compatible != false && !closing
                                ListItem(
                                    modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox) {
                                        selected = ArrayList(if (it) selected + model.id else selected - model.id)
                                    },
                                    headlineContent = { Text(model.label) },
                                    supportingContent = {
                                        val detail = when {
                                            existing -> I18n.t("llm_model_already_added")
                                            item.compatible == false -> I18n.t("llm_model_incompatible")
                                            item.compatible == null -> I18n.t("llm_model_unverified")
                                            else -> ""
                                        }
                                        Text(listOf(model.id.takeIf { model.label != model.id }.orEmpty(), detail)
                                            .filter(String::isNotBlank).joinToString(" · "))
                                    },
                                    trailingContent = { Checkbox(checked, onCheckedChange = null, enabled = enabled) },
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(selectedModels.isNotEmpty(), enter = fadeIn(standardTween()), exit = fadeOut(standardTween())) {
                Button(
                    onClick = { addAndClose(selectedModels) },
                    enabled = !closing && selectedModels.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(I18n.t("llm_model_add_selected", selectedModels.size)) }
            }
        }
    }

    if (manual) {
        ModelEditor(provider, onDismiss = { manual = false }) {
            manual = false
            addAndClose(listOf(it))
        }
    }
}

@Composable
internal fun ModelEditor(
    provider: LlmProvider,
    original: LlmModel? = null,
    onDismiss: () -> Unit,
    onSave: (LlmModel) -> Unit,
) {
    var id by rememberSaveable(original?.id) { mutableStateOf(original?.id.orEmpty()) }
    var name by rememberSaveable(original?.id) { mutableStateOf(original?.name.orEmpty()) }
    val protocol = provider.connection.protocol
    val normalized = modelId(protocol, id)
    val duplicate = provider.models.any { it.id != original?.id && modelId(protocol, it.id) == normalized }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(I18n.t(if (original == null) "llm_model_manual" else "llm_model_edit")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LlmField(id, { id = it }, "settings_llm_model")
                LlmField(name, { name = it }, "llm_model_name")
                AnimatedVisibility(duplicate, enter = fadeIn(standardTween()), exit = fadeOut(standardTween())) {
                    Text(I18n.t("llm_model_already_added"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(LlmModel(normalized, name.trim())) },
                enabled = normalized.isNotBlank() && !duplicate,
            ) { Text(I18n.t(if (original == null) "llm_model_add" else "settings_llm_save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(I18n.t("cancel")) } },
    )
}
