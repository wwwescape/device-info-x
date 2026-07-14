package com.wwwescape.deviceinfox.console.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.db.PartnerGender
import com.wwwescape.deviceinfox.console.ui.components.DateTextField

/** Its own destination now (was inline at the top of the old single Settings page) — matches
 * WhatsApp/Signal's own "profile row navigates to a dedicated Edit Profile page" pattern, per the
 * Settings-restructure TODO writeup. Field-for-field identical to the old `ProfileSection`, just
 * hosted in its own [Scaffold] instead of a titled card inside a longer scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ConsoleSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveProfileSuccessMessage = stringResource(R.string.console_settings_save_profile_success)
    val genericErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.saveProfileSucceeded.collect {
            Toast.makeText(context, saveProfileSuccessMessage, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.saveProfileFailed.collect { detail ->
            Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show()
        }
    }

    var displayName by remember(uiState.displayName) { mutableStateOf(uiState.displayName) }
    var firstName by remember(uiState.firstName) { mutableStateOf(uiState.firstName) }
    var lastName by remember(uiState.lastName) { mutableStateOf(uiState.lastName) }
    var photoUri by remember(uiState.photoUri) { mutableStateOf(uiState.photoUri) }
    var birthdayEpochMillis by remember(uiState.birthdayEpochMillis) { mutableStateOf(uiState.birthdayEpochMillis) }
    var gender by remember(uiState.gender) { mutableStateOf(uiState.gender) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        viewModel.endPickerLaunch()
        if (uri != null) photoUri = uri.toString()
    }
    fun launchPhotoPicker() {
        viewModel.beginPickerLaunch()
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_settings_section_profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { launchPhotoPicker() },
                    contentAlignment = Alignment.Center,
                ) {
                    val currentPhotoUri = photoUri
                    if (currentPhotoUri != null) {
                        AsyncImage(
                            model = currentPhotoUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = stringResource(R.string.console_settings_change_photo),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = { launchPhotoPicker() },
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text(stringResource(R.string.console_settings_change_photo))
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.console_settings_display_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text(stringResource(R.string.console_settings_first_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text(stringResource(R.string.console_settings_last_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DateTextField(
                label = stringResource(R.string.console_settings_birthday_label),
                dateEpochMillis = birthdayEpochMillis,
                onDateChange = { birthdayEpochMillis = it },
                resetKey = uiState.birthdayEpochMillis,
            )

            Text(stringResource(R.string.console_settings_gender_label), style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    PartnerGender.MALE to stringResource(R.string.console_settings_gender_male),
                    PartnerGender.FEMALE to stringResource(R.string.console_settings_gender_female),
                    PartnerGender.UNSPECIFIED to stringResource(R.string.console_settings_gender_unspecified),
                )
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = gender == value,
                        onClick = { gender = value },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    viewModel.saveProfile(displayName, firstName, lastName, photoUri, birthdayEpochMillis, gender)
                },
                enabled = firstName.isNotBlank() && lastName.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.console_settings_save_profile))
            }
        }
    }
}
