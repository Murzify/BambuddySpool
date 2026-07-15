@file:Suppress("FunctionNaming") // Compose entry points use UpperCamelCase by convention.

package com.murzify.bambuddyspool.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.murzify.bambuddyspool.app.root.RootTransientWorkflow
import com.murzify.bambuddyspool.shared.resources.Res
import com.murzify.bambuddyspool.shared.resources.workflow_confirmation
import com.murzify.bambuddyspool.shared.resources.workflow_error
import com.murzify.bambuddyspool.shared.resources.workflow_processing
import com.murzify.bambuddyspool.shared.resources.workflow_success
import com.murzify.bambuddyspool.shared.resources.workflow_tag_mutation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Shared transient feedback surface. Terminal success and failure deliberately use the same focus treatment. */
@Composable
fun WorkflowFeedbackSurface(workflow: RootTransientWorkflow, onProcessingComposed: () -> Unit) {
    val label = stringResource(workflow.label())
    when (workflow) {
        RootTransientWorkflow.Processing -> {
            LaunchedEffect(Unit) { onProcessingComposed() }
            Text(label, modifier = Modifier.processingAnnouncement())
        }
        RootTransientWorkflow.Success,
        RootTransientWorkflow.Error -> FocusedStatusTitle(label)
        RootTransientWorkflow.Confirmation,
        RootTransientWorkflow.TagMutation -> Text(label)
    }
}

private fun RootTransientWorkflow.label(): StringResource = when (this) {
    RootTransientWorkflow.Processing -> Res.string.workflow_processing
    RootTransientWorkflow.Confirmation -> Res.string.workflow_confirmation
    RootTransientWorkflow.Success -> Res.string.workflow_success
    RootTransientWorkflow.Error -> Res.string.workflow_error
    RootTransientWorkflow.TagMutation -> Res.string.workflow_tag_mutation
}
