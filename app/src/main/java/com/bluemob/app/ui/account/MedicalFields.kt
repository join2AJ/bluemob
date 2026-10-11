package com.bluemob.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bluemob.app.account.BloodGroups
import com.bluemob.app.account.Medical
import com.bluemob.app.account.Profile
import com.bluemob.app.account.Sexes
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.theme.Extra

/** The medical part of "About you", as the user is filling it in. */
class MedicalDraft(
    val dob: MutableState<String>,
    val sex: MutableState<String?>,
    val blood: MutableState<String?>,
    val allergies: MutableState<String>,
    val conditions: MutableState<String>,
) {
    /** "yyyy-MM-dd" from what was typed ("12/03/1990"), or null if empty or not a real date. */
    val dobIso: String? get() = DateOfBirth.iso(dob.value)
    val dobOk: Boolean get() = dob.value.isBlank() || dobIso != null
}

@Composable
fun rememberMedicalDraft(p: Profile?, bloodRequired: Boolean = true) = MedicalDraft(
    rememberSaveable { mutableStateOf(DateOfBirth.display(p?.dob)) },
    rememberSaveable { mutableStateOf(p?.sex) },
    rememberSaveable { mutableStateOf(p?.bloodGroup ?: if (p != null && !bloodRequired) BloodGroups.UNKNOWN else null) },
    rememberSaveable { mutableStateOf(p?.allergies ?: "") },
    rememberSaveable { mutableStateOf(p?.conditions ?: "") },
)

/** Dates of birth typed as DD/MM/YYYY. */
object DateOfBirth {
    fun iso(typed: String): String? {
        val m = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").find(typed.trim()) ?: return null
        val (d, mo, y) = m.destructured
        val iso = "%04d-%02d-%02d".format(y.toInt(), mo.toInt(), d.toInt())
        return iso.takeIf { Medical.ageFrom(it) != null }
    }

    fun display(iso: String?): String = iso?.split("-")?.takeIf { it.size == 3 }?.let { (y, m, d) -> "$d/$m/$y" } ?: ""

    /** Keeps digits and adds the slashes as they type: "12031990" → "12/03/1990". */
    fun format(input: String): String {
        val digits = input.filter { it.isDigit() }.take(8)
        return buildString {
            digits.forEachIndexed { i, c -> if (i == 2 || i == 4) append('/'); append(c) }
        }
    }
}

/** Date of birth, sex, blood group, allergies and conditions: what helpers and medics need in an SOS. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicalFields(d: MedicalDraft) {
    Column {
        val age = d.dobIso?.let { Medical.ageFrom(it) }
        OutlinedTextField(
            value = d.dob.value, onValueChange = { d.dob.value = DateOfBirth.format(it) }, label = { Text("Date of birth (optional)") },
            placeholder = { Text("DD/MM/YYYY") }, singleLine = true, isError = !d.dobOk,
            supportingText = { Text(if (!d.dobOk) "Check the date, e.g. 12/03/1990" else age?.let { "Age $it" } ?: "Helpers see your age, not the date") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Label("Sex (optional)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Sexes.ALL.forEach { s -> Chip(s, d.sex.value == s) { d.sex.value = if (d.sex.value == s) null else s } }
        }
        Label("Blood group")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (BloodGroups.ALL + BloodGroups.UNKNOWN).forEach { g -> Chip(if (g == BloodGroups.UNKNOWN) g else "🩸 $g", d.blood.value == g) { d.blood.value = g } }
        }
        Label("Allergies")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Medical.NONE, Medical.PRIVATE).forEach { o -> Chip(o, d.allergies.value == o) { d.allergies.value = if (d.allergies.value == o) "" else o } }
        }
        val typed = d.allergies.value.takeIf { it != Medical.NONE && it != Medical.PRIVATE } ?: ""
        OutlinedTextField(
            value = typed, onValueChange = { d.allergies.value = it.take(120) }, label = { Text("Or type them") },
            placeholder = { Text("e.g. penicillin, peanuts, bee stings") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        OutlinedTextField(
            value = d.conditions.value, onValueChange = { d.conditions.value = it.take(160) }, label = { Text("Medical conditions or medicines (optional)") },
            placeholder = { Text("e.g. asthma, diabetes (insulin), epilepsy") },
            supportingText = { Text("Shared only inside your SOS, so medics know") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = Extra.ink2, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
