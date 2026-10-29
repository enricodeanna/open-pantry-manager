package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.costs.MeasureUnit

/** An amount of money to type, in this language's way ("1,99"); [onChange] gets the cents, or null when empty or not a number. */
@Composable
fun MoneyField(cents: Long?, label: String, modifier: Modifier = Modifier, onChange: (Long?) -> Unit) {
    val texts = strings
    var text by remember { mutableStateOf(cents?.let { texts.moneyField(it) }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(parseMoney(it))
        },
        label = { Text(label) },
        trailingIcon = { Text("€") },
        singleLine = true,
        isError = text.isNotBlank() && parseMoney(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/**
 * What a package holds, to type: a number, and its unit, grams or millilitres, which a tap on it
 * changes. [onChange] gets the amount, or null when empty.
 */
@Composable
fun SizeField(size: Measure?, label: String, modifier: Modifier = Modifier, onChange: (Measure?) -> Unit) {
    val texts = strings
    var text by remember { mutableStateOf(size?.let { texts.number(it.value) }.orEmpty()) }
    var unit by remember { mutableStateOf(size?.unit ?: MeasureUnit.Gram) }
    fun changed() = onChange(parseNumber(text)?.takeIf { it > 0 }?.let { Measure(it, unit) })
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            changed()
        },
        label = { Text(label) },
        trailingIcon = {
            TextButton(onClick = {
                unit = if (unit == MeasureUnit.Gram) MeasureUnit.Millilitre else MeasureUnit.Gram
                changed()
            }) { Text(unit.symbol()) }
        },
        singleLine = true,
        isError = text.isNotBlank() && parseNumber(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** A number of grams or millilitres to type, in [unit]; [onChange] gets it, or null when empty. */
@Composable
fun AmountField(amount: Double?, unit: MeasureUnit, label: String, modifier: Modifier = Modifier, onChange: (Double?) -> Unit) {
    val texts = strings
    var text by remember { mutableStateOf(amount?.let { texts.number(it) }.orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(parseNumber(it))
            },
            label = { Text(label) },
            trailingIcon = { Text(unit.symbol()) },
            singleLine = true,
            isError = text.isNotBlank() && parseNumber(text) == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = modifier,
        )
    }
}
