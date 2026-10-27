package com.enginesense.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.enginesense.app.consultation.ConsultationViewModel
import com.enginesense.app.ui.screens.*
import com.enginesense.app.vehicle.HealthCategory
import com.enginesense.app.ui.theme.GetAutoSureTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GetAutoSureViewModel by viewModels()

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var hasPermission by remember { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants -> hasPermission = grants.values.all { it } }

            LaunchedEffect(Unit) { launcher.launch(requiredPermissions()) }

            GetAutoSureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (hasPermission) {
                        GetAutoSureApp(viewModel)
                    } else {
                        PermissionRequiredScreen { launcher.launch(requiredPermissions()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredScreen(onRequest: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Bluetooth permission is needed to talk to your OBD2 dongle.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

/** Brand/model names contain spaces ("Maruti Suzuki") and need to survive as a single nav-route segment. */
private fun encodeRouteArg(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
private fun decodeRouteArg(value: String?): String = java.net.URLDecoder.decode(value ?: "", "UTF-8")

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val BOTTOM_TABS = listOf(
    BottomTab("home", "Home", Icons.Filled.Home),
    BottomTab("connect", "Scan", Icons.Filled.Build),
    BottomTab("reports", "Reports", Icons.Filled.List),
    BottomTab("aiMechanic", "AI Mechanic", Icons.Filled.Chat)
)

@Composable
fun GetAutoSureApp(viewModel: GetAutoSureViewModel) {
    val consultationViewModel: ConsultationViewModel = viewModel()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = BOTTOM_TABS.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo("home") { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(padding)
        ) {
            composable("splash") {
                SplashScreen(onGetStarted = { navController.navigate("login") })
            }
            composable("login") {
                LoginScreen(
                    // A real Google credential already identifies the user, so it skips
                    // straight past phoneOtp. "Skip" is the temporary testing bypass — see
                    // the note on LoginScreen — and lands in the same place.
                    onSignedIn = { navController.navigate("enterRegistration") },
                    onContinueWithPhone = { navController.navigate("phoneOtp") },
                    onSkip = { navController.navigate("enterRegistration") }
                )
            }
            composable("phoneOtp") {
                PhoneOtpScreen(
                    onBack = { navController.popBackStack() },
                    onVerified = { navController.navigate("enterRegistration") },
                    onSkip = { navController.navigate("enterRegistration") }
                )
            }
            composable("enterRegistration") {
                EnterRegistrationScreen(
                    // Straight to the details screen — brand/model came from the RTO record and
                    // were just confirmed, so the two picker screens are skipped.
                    onConfirmed = { prefill ->
                        val query = buildString {
                            append("?plate=${encodeRouteArg(prefill.registrationNumber)}")
                            prefill.year?.let { append("&year=$it") }
                            prefill.fuelType?.let { append("&fuel=${encodeRouteArg(it)}") }
                            prefill.engineCc?.let { append("&cc=$it") }
                        }
                        navController.navigate("addVehicleDetails/${encodeRouteArg(prefill.brand)}/${encodeRouteArg(prefill.model)}$query")
                    },
                    onEnterManually = { navController.navigate("selectBrand") },
                    onSkip = { navController.navigate("connect") }
                )
            }
            composable("selectBrand") {
                SelectBrandScreen(
                    onSelect = { brand -> navController.navigate("selectModel/${encodeRouteArg(brand)}") },
                    onSkip = { navController.navigate("connect") }
                )
            }
            composable(
                "selectModel/{brand}",
                arguments = listOf(navArgument("brand") { type = NavType.StringType })
            ) { entry ->
                val brand = decodeRouteArg(entry.arguments?.getString("brand"))
                SelectModelScreen(
                    brand = brand,
                    onBack = { navController.popBackStack() },
                    onSelect = { model -> navController.navigate("addVehicleDetails/${encodeRouteArg(brand)}/${encodeRouteArg(model)}") }
                )
            }
            composable(
                "addVehicleDetails/{brand}/{model}?plate={plate}&year={year}&fuel={fuel}&cc={cc}",
                arguments = listOf(
                    navArgument("brand") { type = NavType.StringType },
                    navArgument("model") { type = NavType.StringType },
                    // Optional — only set when the car came from an RTO lookup (enterRegistration).
                    navArgument("plate") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("year") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("fuel") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("cc") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                val brand = decodeRouteArg(entry.arguments?.getString("brand"))
                val model = decodeRouteArg(entry.arguments?.getString("model"))
                AddVehicleDetailsScreen(
                    viewModel, brand, model,
                    onBack = { navController.popBackStack() },
                    onContinue = { navController.navigate("addVehicleSuccess/${encodeRouteArg(brand)}/${encodeRouteArg(model)}") },
                    initialYear = entry.arguments?.getString("year")?.toIntOrNull(),
                    initialFuelType = entry.arguments?.getString("fuel")?.let { decodeRouteArg(it) },
                    initialPlate = entry.arguments?.getString("plate")?.let { decodeRouteArg(it) },
                    initialEngineCc = entry.arguments?.getString("cc")?.toIntOrNull()
                )
            }
            composable(
                "addVehicleSuccess/{brand}/{model}",
                arguments = listOf(
                    navArgument("brand") { type = NavType.StringType },
                    navArgument("model") { type = NavType.StringType }
                )
            ) { entry ->
                val brand = decodeRouteArg(entry.arguments?.getString("brand"))
                val model = decodeRouteArg(entry.arguments?.getString("model"))
                AddVehicleSuccessScreen(
                    brand, model,
                    onConnectObd = { navController.navigate("connect") },
                    onGoHome = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onAddAnother = { navController.navigate("enterRegistration") }
                )
            }
            composable("connect") {
                ConnectScreen(
                    viewModel,
                    onScanStarted = { navController.navigate("scanning") },
                    onLiveDataStarted = { navController.navigate("liveData") },
                    onPerformanceTestStarted = { navController.navigate("performance") },
                    onTripMileageStarted = { navController.navigate("tripMileage") }
                )
            }
            composable("liveData") {
                LiveDataScreen(viewModel, onStop = { navController.popBackStack() })
            }
            composable("performance") {
                PerformanceScreen(viewModel, onStop = { navController.popBackStack() })
            }
            composable("tripMileage") {
                TripMileageScreen(viewModel, onStop = { navController.popBackStack() })
            }
            composable("scanning") {
                val connectionState by viewModel.connectionState.collectAsState()
                val result by viewModel.latestResult.collectAsState()
                val isPrePurchase by viewModel.scanIsPrePurchaseCheck.collectAsState()

                LaunchedEffect(connectionState, result) {
                    when (connectionState) {
                        is ConnectionFlowState.Idle -> if (result != null) {
                            if (isPrePurchase) {
                                navController.navigate("prePurchaseResult") {
                                    popUpTo("prePurchaseCheck") { inclusive = false }
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate("scanResults") {
                                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                        is ConnectionFlowState.Error -> navController.popBackStack("connect", inclusive = false)
                        else -> Unit
                    }
                }
                ScanningScreen()
            }
            composable("scanResults") {
                ScanResultsScreen(
                    viewModel,
                    onOpenIssue = { id -> navController.navigate("issueDetail/$id") },
                    onOpenFullReport = { navController.navigate("aiReport") },
                    onOpenAiMechanic = { navController.navigate("aiMechanic") },
                    onDone = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("home") {
                // Reset any stale pre-purchase-check intent — covers the case where a
                // pre-purchase scan failed and the user navigated away instead of retrying;
                // without this, the next unrelated "Run a new scan" from Home would
                // incorrectly land on the pre-purchase result screen.
                LaunchedEffect(Unit) { viewModel.clearPrePurchaseCheck() }
                HomeScreen(
                    viewModel,
                    onRunScan = { navController.navigate("connect") },
                    onOpenIssues = { navController.navigate("issues") },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("issues") {
                IssuesScreen(viewModel, onOpenIssue = { id -> navController.navigate("issueDetail/$id") })
            }
            composable(
                "issueDetail/{issueId}",
                arguments = listOf(navArgument("issueId") { type = NavType.StringType })
            ) { entry ->
                val issueId = entry.arguments?.getString("issueId") ?: ""
                IssueDetailScreen(viewModel, issueId)
            }
            composable("aiReport") { AiReportScreen(viewModel, onPublishReport = { navController.navigate("publishReport") }) }
            composable("publishReport") {
                val latestResult by viewModel.latestResult.collectAsState()
                val vehicleBrand by viewModel.vehicleBrand.collectAsState()
                val vehicleNickname by viewModel.vehicleNickname.collectAsState()
                val vehicleModel by viewModel.vehicleModel.collectAsState()
                val vehicleYear by viewModel.vehicleYear.collectAsState()
                val vehicleFuelType by viewModel.vehicleFuelType.collectAsState()
                val vehicleOdometerKm by viewModel.vehicleOdometerKm.collectAsState()
                val vehiclePlate by viewModel.vehiclePlate.collectAsState()
                PublishReportScreen(
                    scanResult = latestResult,
                    vehicleInfo = com.enginesense.app.report.VehicleReportInfo(
                        brand = vehicleBrand, nickname = vehicleNickname, model = vehicleModel,
                        year = vehicleYear, fuelType = vehicleFuelType,
                        odometerKm = vehicleOdometerKm, plate = vehiclePlate
                    )
                )
            }
            composable("vehicleDetails") { VehicleDetailsScreen(viewModel) }
            composable(
                "categoryDetail/{category}",
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { entry ->
                val categoryName = entry.arguments?.getString("category") ?: HealthCategory.ENGINE.name
                val category = HealthCategory.entries.firstOrNull { it.name == categoryName } ?: HealthCategory.ENGINE
                CategoryDetailScreen(viewModel, category, onOpenAiMechanic = { navController.navigate("aiMechanic") })
            }
            composable("healthBreakdown") {
                HealthBreakdownScreen(viewModel, onNavigate = { route -> navController.navigate(route) })
            }
            composable("warningLightGuide") {
                WarningLightGuideScreen(onRunScan = { navController.navigate("connect") })
            }
            composable("guidesAndTools") {
                GuidesAndToolsScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable("understandRepair") {
                UnderstandRepairScreen(
                    viewModel,
                    onAsk = { navController.navigate("aiMechanic") },
                    onSeeAllCosts = { navController.navigate("repairCostReference") }
                )
            }
            composable("repairCostReference") {
                RepairCostReferenceScreen()
            }
            composable("prepareForDrive") {
                PrepareForDriveScreen(
                    viewModel,
                    onRunScan = { navController.navigate("connect") },
                    onAskAiMechanic = { navController.navigate("aiMechanic") }
                )
            }
            composable("aiMechanic") {
                AiMechanicScreen(viewModel, onNavigate = { route -> navController.navigate(route) })
            }
            composable("aiMechanicAdvanced") {
                AiMechanicScreen(viewModel, advancedMode = true, onNavigate = { route -> navController.navigate(route) })
            }
            composable("issueForecast") { IssueForecastScreen(viewModel) }
            composable("emissionsTrend") { EmissionsTrendScreen(viewModel) }
            composable("detailedDiagnosis") { DetailedDiagnosisScreen(viewModel) }
            composable("prePurchaseCheck") {
                PrePurchaseCheckScreen(viewModel, onStartScan = { navController.navigate("connect") })
            }
            composable("prePurchaseResult") {
                PrePurchaseResultScreen(
                    viewModel,
                    onOpenDealer = { navController.navigate("dealer") },
                    onDone = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("reports") {
                ReportsScreen(viewModel, onNavigate = { route -> navController.navigate(route) })
            }
            composable("workshop") { WorkshopScreen(viewModel) }
            composable("vehicleInfo") { VehicleInfoScreen(viewModel) }
            composable("allSensors") { AllSensorsScreen(viewModel) }
            composable("milStatus") { MilStatusScreen(viewModel) }
            composable("battery") { BatteryScreen(viewModel) }
            composable("warranty") { WarrantyScreen() }
            composable("marketplace") { MarketplaceScreen() }
            composable("pro") { ProScreen() }
            composable("dealer") {
                DealerScreen(
                    onSetUpDealerProfile = { navController.navigate("dealerProfile") },
                    onOpenPricingCalculator = { navController.navigate("dealerPricing") },
                    onPublishWebReport = { navController.navigate("publishReport") }
                )
            }
            composable("dealerProfile") { DealerProfileScreen(onSaved = { navController.popBackStack() }) }
            composable("dealerPricing") { DealerPricingScreen() }

            // "Ask a Real Expert" — Phase 2 (preview/mock data, see ConsultationViewModel's doc).
            composable("expertList") {
                ExpertListScreen(
                    consultationViewModel,
                    onSelectExpert = { expertId -> navController.navigate("expertProfile/${encodeRouteArg(expertId)}") },
                    onMyConsultations = { navController.navigate("myConsultations") }
                )
            }
            composable(
                "expertProfile/{expertId}",
                arguments = listOf(navArgument("expertId") { type = NavType.StringType })
            ) { entry ->
                val expertId = decodeRouteArg(entry.arguments?.getString("expertId"))
                val expert = consultationViewModel.expertById(expertId)
                if (expert == null) {
                    // Only reachable by navigating here before the expert list has loaded
                    // (e.g. a deep link) — bounce back rather than crash on a null expert.
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    ExpertProfileScreen(
                        consultationViewModel, expert,
                        onSlotSelected = { slot -> navController.navigate("booking/${encodeRouteArg(expertId)}/${encodeRouteArg(slot.id)}") }
                    )
                }
            }
            composable(
                "booking/{expertId}/{slotId}",
                arguments = listOf(
                    navArgument("expertId") { type = NavType.StringType },
                    navArgument("slotId") { type = NavType.StringType }
                )
            ) { entry ->
                val expertId = decodeRouteArg(entry.arguments?.getString("expertId"))
                val slotId = decodeRouteArg(entry.arguments?.getString("slotId"))
                val expert = consultationViewModel.expertById(expertId)
                val slot = consultationViewModel.slotById(slotId)
                if (expert == null || slot == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    BookingScreen(
                        consultationViewModel, expert, slot,
                        onBooked = { navController.navigate("bookingConfirmation") { popUpTo("expertList") } }
                    )
                }
            }
            composable("bookingConfirmation") {
                val booking by consultationViewModel.activeBooking.collectAsState()
                booking?.let {
                    BookingConfirmationScreen(
                        consultationViewModel, it,
                        onJoin = { /* Phase 5: real 100ms join flow — no-op until then */ },
                        onViewMyConsultations = { navController.navigate("myConsultations") { popUpTo("expertList") } }
                    )
                } ?: LaunchedEffect(Unit) { navController.popBackStack() }
            }
            composable("myConsultations") {
                MyConsultationsScreen(
                    consultationViewModel,
                    onSelectBooking = { booking ->
                        consultationViewModel.selectBooking(booking)
                        navController.navigate("bookingConfirmation")
                    }
                )
            }
        }
    }
}
