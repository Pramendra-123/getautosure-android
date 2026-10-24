package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.auth.signInWithGoogle
import com.enginesense.app.ui.components.BrandAvatar
import com.enginesense.app.ui.components.ModelAvatar
import com.enginesense.app.ui.components.StepProgressIndicator
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import kotlinx.coroutines.launch
import java.util.Calendar

/** Total steps shown on [StepProgressIndicator] across Login -> Phone/OTP -> Brand -> Model -> Vehicle details. */
private const val FLOW_STEPS = 5

// ---------------------------------------------------------------------------------------
// 1. Splash / value proposition
// ---------------------------------------------------------------------------------------

private val VALUE_PROPS = listOf(
    "Scan & understand car health",
    "Get AI mechanic guidance",
    "Know repair costs before you pay",
    "Talk to expert mechanics (Premium)"
)

@Composable
fun SplashScreen(onGetStarted: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GetAuto", style = GetAutoSureType.SectionTitle)
            Text("Sure", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Accent))
        }
        Text("Your AI Car Health Expert", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(GetAutoSureColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Know your car better.", style = GetAutoSureType.ScreenTitle)
        Text("Drive with confidence.", style = GetAutoSureType.ScreenTitle.copy(color = GetAutoSureColors.Accent))
        Spacer(Modifier.height(24.dp))
        VALUE_PROPS.forEach { prop -> ValuePropRow(prop) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Get Started")
        }
    }
}

@Composable
private fun ValuePropRow(text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(GetAutoSureColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = GetAutoSureType.Body)
    }
}

// ---------------------------------------------------------------------------------------
// 2. Login options — Google or phone. No Apple Sign-In: this is an Android app, that
// button doesn't apply here even though the reference mockup showed one.
// ---------------------------------------------------------------------------------------

/**
 * [onSignedIn] fires only after a real Google credential comes back — see
 * [com.enginesense.app.auth.signInWithGoogle]. [onSkip] is a temporary bypass for testing the
 * rest of the flow before a real Google Cloud client ID is configured; remove it once that's
 * done and sign-in has been verified on a real device.
 */
@Composable
fun LoginScreen(onSignedIn: (displayName: String?) -> Unit, onContinueWithPhone: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        StepProgressIndicator(current = 1, total = FLOW_STEPS)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GetAuto", style = GetAutoSureType.SectionTitle)
            Text("Sure", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Accent))
        }
        Spacer(Modifier.height(32.dp))
        Text("Let's get you started", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text("Create an account or sign in to start scanning your car.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            onClick = {
                errorMessage = null
                isSigningIn = true
                scope.launch {
                    val result = signInWithGoogle(context)
                    isSigningIn = false
                    result.onSuccess {
                        onSignedIn(it.displayName)
                    }.onFailure { e -> errorMessage = "Couldn't sign in with Google: ${e.message ?: "no Google account available"}" }
                }
            },
            enabled = !isSigningIn,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(color = GetAutoSureColors.Accent, modifier = Modifier.size(18.dp))
            } else {
                Text("Continue with Google")
            }
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMessage!!, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text("  or  ", style = GetAutoSureType.Caption)
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onContinueWithPhone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Continue with Phone")
        }

        Spacer(Modifier.weight(1f))
        // Temporary while Google Sign-In has no real client ID and phone has no real SMS
        // backend — lets the rest of the UI be tested end to end. Remove once both are real.
        Text(
            "Skip for now (testing)",
            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent),
            modifier = Modifier.clickable(onClick = onSkip)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "By continuing, you agree to our Terms of Service and Privacy Policy.",
            style = GetAutoSureType.Caption,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------------------------------
// 3+4. Phone number + OTP, merged into one screen (the OTP step just reveals in place).
// No real SMS is sent — there's no backend for that yet (see docs/premium-backend-plan.md).
// Any 6-digit code continues, clearly labeled as such, same honesty pattern already used
// for the WhatsApp expert hotline: ship the real flow, never pretend a part that isn't
// built yet actually works.
// ---------------------------------------------------------------------------------------

@Composable
fun PhoneOtpScreen(onBack: () -> Unit, onVerified: () -> Unit, onSkip: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var otp by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        StepProgressIndicator(current = 2, total = FLOW_STEPS)
        Spacer(Modifier.height(16.dp))
        Icon(
            Icons.Filled.ArrowBack, contentDescription = "Back",
            modifier = Modifier.clickable { if (otpSent) otpSent = false else onBack() }
        )
        Spacer(Modifier.height(16.dp))

        if (!otpSent) {
            Text("Enter your phone number", style = GetAutoSureType.ScreenTitle)
            Spacer(Modifier.height(8.dp))
            Text("We'll send a 6-digit code to verify your number.", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GetAutoSureColors.Surface)
                        .padding(horizontal = 14.dp, vertical = 16.dp)
                ) { Text("+91", style = GetAutoSureType.Body) }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) phone = it },
                    placeholder = { Text("98765 43210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = GetAutoSureColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Your number is safe with us and will only be used for account verification.",
                    style = GetAutoSureType.Caption
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { otpSent = true },
                enabled = phone.length == 10,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Send OTP") }
            Spacer(Modifier.height(12.dp))
            // Temporary while there's no real SMS backend — see the matching note on LoginScreen.
            Text(
                "Skip for now (testing)",
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent),
                modifier = Modifier.clickable(onClick = onSkip)
            )
        } else {
            Text("Enter OTP", style = GetAutoSureType.ScreenTitle)
            Spacer(Modifier.height(8.dp))
            Text("We've sent a 6-digit code to +91 $phone", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otp = it },
                placeholder = { Text("123456") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = GetAutoSureType.ScreenTitle.copy(letterSpacing = 8.sp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Demo mode — this isn't wired to a real SMS provider yet, so any 6 digits will continue.",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onVerified,
                enabled = otp.length == 6,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Verify & Continue") }
        }
    }
}

// ---------------------------------------------------------------------------------------
// 5. Add your car — brand grid. Generic colored initials, not real logos (see BrandAvatar).
// ---------------------------------------------------------------------------------------

@Composable
fun SelectBrandScreen(onSelect: (String) -> Unit, onSkip: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val brands = VEHICLE_MAKES.drop(1) // "Other / not sure" goes last, handled separately below
    val filtered = if (query.isBlank()) brands else brands.filter { it.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        StepProgressIndicator(current = 3, total = FLOW_STEPS)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add your car", style = GetAutoSureType.ScreenTitle, modifier = Modifier.weight(1f))
            Text("Skip", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Accent), modifier = Modifier.clickable(onClick = onSkip))
        }
        Spacer(Modifier.height(4.dp))
        Text("Get personalised insights for your vehicle.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by make (e.g. Tata)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Text("All brands", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(filtered) { brand -> BrandTile(brand, onClick = { onSelect(brand) }) }
            item { BrandTile("Other / not sure", onClick = { onSelect(VEHICLE_MAKES.first()) }) }
        }
    }
}

@Composable
private fun BrandTile(brand: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GetAutoSureColors.Surface)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) { BrandAvatar(brand, size = 44.dp) }
        Spacer(Modifier.height(6.dp))
        Text(brand, style = GetAutoSureType.Caption, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------
// 6. Select your model
// ---------------------------------------------------------------------------------------

@Composable
fun SelectModelScreen(brand: String, onBack: () -> Unit, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val models = modelsFor(brand)
    val filtered = if (query.isBlank()) models else models.filter { it.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        StepProgressIndicator(current = 4, total = FLOW_STEPS)
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(16.dp))
        Text("Select your model", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(brand, style = GetAutoSureType.Caption)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search model (e.g. Creta)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered) { model -> ModelRow(brand, model, onClick = { onSelect(model) }) }
        }
    }
}

@Composable
private fun ModelRow(brand: String, model: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelAvatar(brand, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Text(model, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GetAutoSureColors.TextSecondary)
    }
}

// ---------------------------------------------------------------------------------------
// 7. Vehicle details — year, fuel type, odometer, nickname.
// ---------------------------------------------------------------------------------------

private val FUEL_TYPES = listOf("Petrol", "Diesel", "CNG", "Electric", "Hybrid")
private val YEARS = (Calendar.getInstance().get(Calendar.YEAR) downTo 1990).map { it.toString() }

@Composable
fun AddVehicleDetailsScreen(
    viewModel: GetAutoSureViewModel,
    brand: String,
    model: String,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(YEARS.first()) }
    var fuelType by remember { mutableStateOf(FUEL_TYPES.first()) }
    var odometer by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var showYearSheet by remember { mutableStateOf(false) }
    var showFuelSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        StepProgressIndicator(current = 5, total = FLOW_STEPS)
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(16.dp))
        Text("Vehicle details", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(20.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(GetAutoSureColors.Surface),
                contentAlignment = Alignment.Center
            ) { ModelAvatar(brand, size = 56.dp) }
            Spacer(Modifier.height(10.dp))
            Text("$brand $model", style = GetAutoSureType.SectionTitle)
        }
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Nickname (optional)") },
            placeholder = { Text("e.g. My Creta") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Year", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showYearSheet = true }, modifier = Modifier.fillMaxWidth()) { Text(year) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Fuel Type", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showFuelSheet = true }, modifier = Modifier.fillMaxWidth()) { Text(fuelType) }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = odometer,
            onValueChange = { if (it.all(Char::isDigit)) odometer = it },
            label = { Text("Odometer (optional)") },
            placeholder = { Text("42000") },
            suffix = { Text("km") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = plate,
            onValueChange = { plate = it.uppercase() },
            label = { Text("Registration number (optional)") },
            placeholder = { Text("HR26AB1234") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                viewModel.setVehicle(brand.takeUnless { it == VEHICLE_MAKES.first() }, nickname, model.takeUnless { it == OTHER_MODEL } ?: "")
                viewModel.setVehicleDetails(year.toIntOrNull(), fuelType, odometer.toIntOrNull(), plate.takeIf { it.isNotBlank() })
                onContinue()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Save & Continue") }
    }

    if (showYearSheet) {
        PickerBottomSheet(
            title = "Select year", items = YEARS, itemLabel = { it },
            onDismiss = { showYearSheet = false },
            onPick = { year = it; showYearSheet = false }
        )
    }
    if (showFuelSheet) {
        PickerBottomSheet(
            title = "Select fuel type", items = FUEL_TYPES, itemLabel = { it },
            onDismiss = { showFuelSheet = false },
            onPick = { fuelType = it; showFuelSheet = false }
        )
    }
}

// ---------------------------------------------------------------------------------------
// 8. Success
// ---------------------------------------------------------------------------------------

@Composable
fun AddVehicleSuccessScreen(
    brand: String,
    model: String,
    onConnectObd: () -> Unit,
    onGoHome: () -> Unit,
    onAddAnother: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(GetAutoSureColors.Good.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = GetAutoSureColors.Good, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("All set!", style = GetAutoSureType.ScreenTitle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your $brand $model is added to your account.",
            style = GetAutoSureType.Caption,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onConnectObd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Connect OBD Now")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onGoHome, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Go to Home")
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onAddAnother) { Text("Add another car") }
    }
}
