// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.TripActivity
import rs.lausevic.stow.domain.WizardRules
import rs.lausevic.stow.data.repo.TripComposer
import rs.lausevic.stow.ui.components.ButtonStyle
import rs.lausevic.stow.ui.components.FieldBox
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.ProgressStrip
import rs.lausevic.stow.ui.components.SegmentedControl
import rs.lausevic.stow.ui.components.StowButton
import rs.lausevic.stow.ui.components.StowScreen
import rs.lausevic.stow.ui.components.StowTopBar
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme
import java.time.LocalDate

private const val STEPS = 3

/**
 * Čarobnjak. Pravila se prikazuju PRE generisanja, ne posle.
 *
 * To je cela razlika u odnosu na AI konkurenciju: lista se ne pojavljuje kao pogodak
 * nego kao ishod odgovora koje vidiš i možeš da promeniš.
 */
@Composable
fun WizardScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var accommodation by remember { mutableStateOf(Accommodation.APARTMENT) }
    var nightsText by remember { mutableStateOf("7") }
    var activities by remember { mutableStateOf(emptySet<TripActivity>()) }
    var working by remember { mutableStateOf(false) }

    val nights = nightsText.toIntOrNull()?.takeIf { it in 1..365 }
    val rules = remember(accommodation, nights, activities) {
        WizardRules.rulesFor(WizardRules.Answers(accommodation, nights, activities))
    }
    val nameOk = name.isNotBlank() || destination.isNotBlank()

    StowScreen(
        topBar = {
            StowTopBar(
                title = stringResource(R.string.wizard_title),
                subtitle = stringResource(R.string.wizard_step, step + 1, STEPS),
                onBack = onBack,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            ProgressStrip(done = step + 1, trackable = STEPS, segments = STEPS)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (step) {
                    0 -> {
                        TextFieldBox(
                            label = stringResource(R.string.wizard_destination),
                            value = destination,
                            onValueChange = { destination = it },
                        )
                        TextFieldBox(
                            label = stringResource(R.string.wizard_name),
                            value = name,
                            onValueChange = { name = it },
                        )
                        Column(Modifier.padding(top = 6.dp)) {
                            MicroLabel(stringResource(R.string.wizard_accommodation))
                            Box(Modifier.padding(top = 7.dp)) {
                                SegmentedControl(
                                    options = Accommodation.entries,
                                    selected = accommodation,
                                    label = { accommodationLabel(it) },
                                    onSelect = { accommodation = it },
                                )
                            }
                        }
                    }

                    1 -> {
                        TextFieldBox(
                            label = stringResource(R.string.wizard_nights),
                            value = nightsText,
                            onValueChange = { text -> nightsText = text.filter { it.isDigit() }.take(3) },
                            numeric = true,
                        )
                        if (nights == null) {
                            Text(
                                text = stringResource(R.string.error_nights_range),
                                style = MaterialTheme.typography.bodySmall,
                                color = StowTheme.state.alert,
                            )
                        }
                        Text(
                            text = stringResource(R.string.wizard_no_dates),
                            style = MaterialTheme.typography.bodySmall,
                            color = StowTheme.state.ink2,
                        )
                    }

                    else -> {
                        MicroLabel(stringResource(R.string.wizard_activities))
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TripActivity.entries.forEach { activity ->
                                val on = activity in activities
                                ActivityChip(
                                    label = activityLabel(activity),
                                    selected = on,
                                    onClick = {
                                        activities = if (on) activities - activity else activities + activity
                                    },
                                )
                            }
                        }
                        RulePreview(rules)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StowButton(
                    text = stringResource(R.string.wizard_back),
                    onClick = { if (step == 0) onBack() else step-- },
                    style = ButtonStyle.GHOST,
                    modifier = Modifier.weight(1f),
                )
                StowButton(
                    text = if (step == STEPS - 1) {
                        stringResource(R.string.wizard_compose)
                    } else {
                        stringResource(R.string.wizard_next)
                    },
                    onClick = {
                        if (step < STEPS - 1) {
                            step++
                        } else if (!working) {
                            working = true
                            scope.launch {
                                val today = LocalDate.now().toEpochDay()
                                val id = container.composer.compose(
                                    TripComposer.Request(
                                        name = name.ifBlank { destination },
                                        destination = destination.ifBlank { null },
                                        startDate = nights?.let { today },
                                        endDate = nights?.let { today + it },
                                        accommodation = accommodation,
                                        activities = activities,
                                    ),
                                )
                                onCreated(id)
                            }
                        }
                    },
                    style = ButtonStyle.ACCENT,
                    enabled = if (step == 0) nameOk else !working,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Pregled pravila koja će se primeniti. Ista funkcija koja i generiše. */
@Composable
private fun RulePreview(rules: List<WizardRules.Rule>) {
    val c = StowTheme.state
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(StowShapes.card)
            .background(c.accentSoft)
            .border(1.dp, c.accent, StowShapes.card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MicroLabel(stringResource(R.string.wizard_rules_preview), color = c.accent)
        rules.forEach { rule ->
            Text(
                text = ruleSentence(rule.id),
                style = MaterialTheme.typography.bodySmall,
                color = c.ink2,
            )
        }
    }
}

@Composable
fun ruleSentence(ruleId: String?): String = when (ruleId) {
    WizardRules.RULE_CORE -> stringResource(R.string.rule_core)
    WizardRules.RULE_HOTEL -> stringResource(R.string.rule_accommodation_hotel)
    WizardRules.RULE_APARTMENT, WizardRules.RULE_CAMPING ->
        stringResource(R.string.rule_accommodation_apartment)
    WizardRules.RULE_NIGHTS_GTE5 -> stringResource(R.string.rule_nights_gte5)
    WizardRules.RULE_NIGHTS_GTE14 -> stringResource(R.string.rule_nights_gte14)
    WizardRules.RULE_BEACH -> stringResource(R.string.rule_activity_beach)
    WizardRules.RULE_HIKING -> stringResource(R.string.rule_activity_hiking)
    WizardRules.RULE_BUSINESS -> stringResource(R.string.rule_activity_business)
    WizardRules.RULE_DRIVING -> stringResource(R.string.rule_activity_driving)
    WizardRules.RULE_GYM -> stringResource(R.string.rule_activity_gym)
    WizardRules.RULE_FORMAL -> stringResource(R.string.rule_activity_formal)
    null -> stringResource(R.string.why_manual)
    else -> stringResource(R.string.why_template)
}

@Composable
fun accommodationLabel(accommodation: Accommodation): String = when (accommodation) {
    Accommodation.HOTEL -> stringResource(R.string.accommodation_hotel)
    Accommodation.APARTMENT -> stringResource(R.string.accommodation_apartment)
    Accommodation.CAMPING -> stringResource(R.string.accommodation_camping)
    Accommodation.FAMILY -> stringResource(R.string.accommodation_family)
}

@Composable
private fun activityLabel(activity: TripActivity): String = when (activity) {
    TripActivity.BEACH -> stringResource(R.string.activity_beach)
    TripActivity.HIKING -> stringResource(R.string.activity_hiking)
    TripActivity.BUSINESS -> stringResource(R.string.activity_business)
    TripActivity.DRIVING -> stringResource(R.string.activity_driving)
    TripActivity.GYM -> stringResource(R.string.activity_gym)
    TripActivity.FORMAL -> stringResource(R.string.activity_formal)
}

@Composable
private fun ActivityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = StowTheme.state
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = if (selected) c.accent else c.ink2,
        modifier = Modifier
            .clip(StowShapes.pill)
            .background(if (selected) c.accentSoft else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) c.accent else c.line2, StowShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
    )
}

@Composable
private fun TextFieldBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
) {
    val c = StowTheme.state
    FieldBox(label = label, modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            ),
            singleLine = true,
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
