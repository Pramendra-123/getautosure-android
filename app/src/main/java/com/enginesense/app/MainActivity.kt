package com.enginesense.app

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.enginesense.app.storage.AppMode
import com.enginesense.app.storage.AppModePrefs
import com.enginesense.app.storage.LanguagePrefs
import com.enginesense.app.storage.OnboardingPrefs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.enginesense.app.consultation.ConsultationViewModel
import com.enginesense.app.consultation.LoadState
import com.enginesense.app.ui.components.LocalMechanicAvailable
import com.enginesense.app.ui.components.ModeBottomBar
import com.enginesense.app.ui.components.ModeTabs
import com.enginesense.app.ui.components.AiAssistantButton
import com.enginesense.app.ui.components.AiAssistantSheet
import com.enginesense.app.ui.components.aiContextFor
import com.enginesense.app.ui.components.showsAssistant
import com.enginesense.app.consultation.MechanicViewModel
import com.enginesense.app.ui.screens.*
import com.enginesense.app.vehicle.HealthCategory
import com.enginesense.app.ui.theme.GetAutoSureTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** A notification can ask for a screen to open, e.g. "wallet". */
        const val EXTRA_OPEN_ROUTE = "open_route"
    }

    private val viewModel: GetAutoSureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Free renewal reminders: a daily on-phone check of every car's saved dates.
        com.enginesense.app.owner.RenewalReminders.schedule(this)
        receiveShare(intent)
        // Bluetooth permission is asked for on the Scan screen, when the user first connects a
        // scanner, so the app opens straight into onboarding or Home.
        setContent {
            GetAutoSureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GetAutoSureApp(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    /** A payment SMS shared from the SMS app (Share → GetAutoSure) opens the "Add this payment?" dialog. */
    private fun receiveShare(intent: android.content.Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_ROUTE)?.let { viewModel.requestRoute(it) }
        if (intent?.action == android.content.Intent.ACTION_SEND && intent.type == "text/plain") {
            viewModel.receiveSharedText(intent.getStringExtra(android.content.Intent.EXTRA_TEXT))
        }
    }
}

/** Brand/model names contain spaces ("Maruti Suzuki") and need to survive as a single nav-route segment. */
private fun encodeRouteArg(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
private fun decodeRouteArg(value: String?): String = java.net.URLDecoder.decode(value ?: "", "UTF-8")

@Composable
fun GetAutoSureApp(viewModel: GetAutoSureViewModel) {
    val consultationViewModel: ConsultationViewModel = viewModel()
    val mechanicViewModel: MechanicViewModel = viewModel()
    val navController = rememberNavController()
    val context = LocalContext.current
    // Chosen once: returning users skip the welcome and sign-in screens.
    val startDestination = remember { if (OnboardingPrefs.isDone(context)) "home" else "splash" }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Own car / buying / dealer, picked once on "Who are you?" — it decides Home and the bottom tabs.
    var appMode by remember { mutableStateOf(AppModePrefs.get(context)) }
    fun switchMode(mode: AppMode) {
        AppModePrefs.set(context, mode)
        appMode = mode
    }
    val modeTabs = ModeTabs.forMode(appMode)
    val showBottomBar = currentRoute in modeTabs.tabRoutes
    /** Leaves onboarding for Home, clearing the welcome and sign-in screens from Back. */
    fun finishOnboarding() {
        OnboardingPrefs.markDone(context)
        navController.navigate("home") {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
    /** After sign-in, each mode sets up something different: the car, nothing, or the showroom. */
    fun afterSignIn() {
        when (appMode) {
            AppMode.OWN_CAR -> navController.navigate("enterRegistration")
            AppMode.BUYING -> finishOnboarding()
            AppMode.DEALER -> navController.navigate("dealerOnboarding")
        }
    }
    // A plate typed on the lookup screen before choosing to enter the car by hand.
    var manualPlate by rememberSaveable { mutableStateOf<String?>(null) }

    // Until the expert list has loaded, assume someone is free rather than hide the prompt.
    val experts by consultationViewModel.experts.collectAsState()
    val mechanicAvailable = (experts as? LoadState.Success)?.data?.any { it.nextSlotEpochMillis != null } ?: true

    // The AI Mechanic button in the corner of every screen; its sheet knows which screen it was opened from.
    var assistantOpen by remember { mutableStateOf(false) }
    val latestResult by viewModel.latestResult.collectAsState()
    val screenFindingId = backStackEntry?.arguments?.let { it.getString("issueId") ?: it.getString("findingId")?.let(::decodeRouteArg) }
    val screenFinding = latestResult?.findings?.firstOrNull { it.id == screenFindingId }

    CompositionLocalProvider(LocalMechanicAvailable provides mechanicAvailable) {
    CarQuestionDialog(viewModel, onAddDetails = { navController.navigate("enterRegistration") }, onUsedCarCheck = { navController.navigate("prePurchaseResult") })
    SharedPaymentDialog(viewModel)
    FixConfirmationDialog(viewModel, onExpert = { navController.navigate("expertList") })
    val openRoute by viewModel.openRoute.collectAsState()
    LaunchedEffect(openRoute) {
        openRoute?.let { route -> viewModel.routeOpened(); navController.navigate(route) { popUpTo("home") } }
    }
    if (assistantOpen) {
        AiAssistantSheet(
            viewModel = viewModel,
            screenContext = aiContextFor(currentRoute, screenFinding?.title),
            onDismiss = { assistantOpen = false },
            onOpenFullChat = { assistantOpen = false; navController.navigate("aiMechanic") }
        )
    }
    Scaffold(
        floatingActionButton = {
            // AI Mechanic has no tab any more: this button is its way in, on every screen.
            if (showsAssistant(currentRoute)) AiAssistantButton(onClick = { assistantOpen = true })
        },
        bottomBar = {
            if (showBottomBar) {
                ModeBottomBar(modeTabs, currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable("splash") {
                SplashScreen(onGetStarted = { navController.navigate("chooseLanguage") })
            }
            composable("chooseLanguage") {
                ChooseLanguageScreen(initial = LanguagePrefs.suggested(context)) { language ->
                    LanguagePrefs.set(context, language)
                    navController.navigate("chooseMode")
                }
            }
            composable("chooseMode") {
                ChooseModeScreen(initial = null) { mode ->
                    switchMode(mode)
                    navController.navigate("login")
                }
            }
            composable("login") {
                LoginScreen(
                    // A real Google credential already identifies the user, so it skips
                    // straight past phoneOtp. "Skip" is the temporary testing bypass — see
                    // the note on LoginScreen — and lands in the same place.
                    onSignedIn = { afterSignIn() },
                    onContinueWithPhone = { navController.navigate("phoneOtp") },
                    onSkip = { afterSignIn() },
                    modeTitle = appMode.title,
                    savesWhat = when (appMode) {
                        AppMode.OWN_CAR -> "your car"
                        AppMode.BUYING -> "the cars you check"
                        AppMode.DEALER -> "your reports"
                    }
                )
            }
            composable("phoneOtp") {
                PhoneOtpScreen(
                    onBack = { navController.popBackStack() },
                    onVerified = { afterSignIn() },
                    onSkip = { afterSignIn() }
                )
            }
            composable("enterRegistration") {
                EnterRegistrationScreen(
                    // The customer just confirmed the RTO record, so the car is saved as is — no
                    // pickers and no details form asking for what they've already seen. Anything
                    // missing can be changed later from Edit vehicle on Home.
                    onConfirmed = { prefill, rc ->
                        // Kept for the reports' RTO section; dropped again if the plate is later changed by hand.
                        viewModel.setVehicleRc(rc)
                        viewModel.setVehicle(prefill.brand, nickname = "", model = prefill.model)
                        viewModel.setVehicleDetails(prefill.year, prefill.fuelType, odometerKm = null, plate = prefill.registrationNumber, engineCc = prefill.engineCc)
                        navController.navigate("addVehicleSuccess/${encodeRouteArg(prefill.brand)}/${encodeRouteArg(prefill.model)}")
                    },
                    onEnterManually = { plate ->
                        manualPlate = plate.ifBlank { null }
                        navController.navigate("selectBrand")
                    },
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
                    initialPlate = entry.arguments?.getString("plate")?.let { decodeRouteArg(it) } ?: manualPlate,
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
                            // The whole graph, not the start destination: once onboarding is done the
                            // splash screen is gone, so popping to it cleared nothing and old screens piled up.
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onAddAnother = { viewModel.addCar(); navController.navigate("enterRegistration") }
                )
            }
            composable("connect") {
                LaunchedEffect(Unit) { OnboardingPrefs.markDone(context) }
                ConnectScreen(
                    viewModel,
                    onScanStarted = { navController.navigate("scanning") },
                    onLiveDataStarted = { navController.navigate("liveData") },
                    onPerformanceTestStarted = { navController.navigate("performance") },
                    onTripMileageStarted = { navController.navigate("tripMileage") },
                    onOpenDriveLab = { navController.navigate("driveLab") }
                )
            }
            composable("driveLab") { DriveLabScreen() }
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
                val prePurchaseResult by viewModel.prePurchaseResult.collectAsState()
                val isDealer by viewModel.scanIsDealerReport.collectAsState()
                val dealerResult by viewModel.dealerResult.collectAsState()

                LaunchedEffect(connectionState, result, prePurchaseResult, dealerResult) {
                    when (connectionState) {
                        is ConnectionFlowState.Idle -> if (isDealer) {
                            // Back to the dealer flow, which picks up where it left off.
                            if (dealerResult != null) navController.navigate("dealerFlow") {
                                popUpTo("dealerFlow") { inclusive = false }
                                launchSingleTop = true
                            }
                        } else if ((if (isPrePurchase) prePurchaseResult else result) != null) {
                            if (isPrePurchase) {
                                navController.navigate("prePurchaseResult") {
                                    popUpTo("prePurchaseCheck") { inclusive = false }
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate("scanResults") {
                                    popUpTo(navController.graph.id) { inclusive = true }
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
                // Results replace the whole back stack, so without this, back would close the app.
                BackHandler {
                    navController.navigate("home") {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                ScanResultsScreen(
                    viewModel,
                    onOpenIssue = { id -> navController.navigate("issueDetail/$id") },
                    onOpenFullReport = { navController.navigate("aiReport") },
                    onOpenAiMechanic = { navController.navigate("aiMechanic") },
                    onTalkToMechanic = { navController.navigate("expertList") },
                    onOpenChanges = { navController.navigate("sinceLastScan") },
                    onDone = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.id) { inclusive = true }
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
                LaunchedEffect(Unit) {
                    viewModel.clearPrePurchaseCheck()
                    viewModel.clearDealerScan()
                    OnboardingPrefs.markDone(context)
                }
                when (appMode) {
                    AppMode.OWN_CAR -> GarageScreen(
                        viewModel,
                        onRunScan = { navController.navigate("connect") },
                        onAddCar = { viewModel.addCar(); navController.navigate("enterRegistration") },
                        onNavigate = { route -> navController.navigate(route) }
                    )
                    AppMode.BUYING -> BuyerHomeScreen(
                        viewModel,
                        onStartCheck = { navController.navigate("prePurchaseCheck") },
                        onOpenLastCheck = { navController.navigate("prePurchaseResult") },
                        onExpert = { navController.navigate("expertList") },
                        onBoughtIt = { switchMode(AppMode.OWN_CAR) }
                    )
                    AppMode.DEALER -> DealerScreen(
                        onSetUpDealerProfile = { navController.navigate("dealerProfile") },
                        onStartReport = { navController.navigate("dealerFlow") },
                        onUsedCarCheck = { navController.navigate("prePurchaseCheck") },
                        onBuyPack = { cars -> navController.navigate("dealerCheckout/$cars") },
                        onMyReports = { navController.navigate("dealerReports") }
                    )
                }
            }
            composable("profile") {
                ProfileScreen(
                    appMode,
                    onSwitchMode = { mode ->
                        switchMode(mode)
                        navController.navigate("home") {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            // Dealer mode, first launch: the showroom profile is the setup step, then Dealer Saathi home.
            composable("dealerOnboarding") { DealerProfileScreen(onSaved = { finishOnboarding() }) }
            composable("issues") {
                IssuesScreen(viewModel, onOpenIssue = { id -> navController.navigate("issueDetail/$id") })
            }
            composable(
                "issueDetail/{issueId}",
                arguments = listOf(navArgument("issueId") { type = NavType.StringType })
            ) { entry ->
                val issueId = entry.arguments?.getString("issueId") ?: ""
                IssueDetailScreen(viewModel, issueId, onOpenAiMechanic = { navController.navigate("aiMechanic") })
            }
            composable("aiReport") {
                AiReportScreen(
                    viewModel,
                    onOpenIssue = { id -> navController.navigate("issueDetail/$id") },
                    onViewPdf = { file -> navController.navigate("pdfViewer/${encodeRouteArg(file.absolutePath)}") },
                    onOpenAiMechanic = { navController.navigate("aiMechanic") }
                )
            }
            composable(
                "pdfViewer/{path}",
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                PdfViewerScreen(decodeRouteArg(entry.arguments?.getString("path")))
            }
            composable("publishReport") {
                val latestResult by viewModel.latestResult.collectAsState()
                val vehicleBrand by viewModel.vehicleBrand.collectAsState()
                val vehicleNickname by viewModel.vehicleNickname.collectAsState()
                val vehicleModel by viewModel.vehicleModel.collectAsState()
                val vehicleYear by viewModel.vehicleYear.collectAsState()
                val vehicleFuelType by viewModel.vehicleFuelType.collectAsState()
                val vehicleOdometerKm by viewModel.vehicleOdometerKm.collectAsState()
                val vehiclePlate by viewModel.vehiclePlate.collectAsState()
                val vehicleRc by viewModel.vehicleRc.collectAsState()
                PublishReportScreen(
                    scanResult = latestResult,
                    vehicleInfo = com.enginesense.app.report.VehicleReportInfo(
                        brand = vehicleBrand, nickname = vehicleNickname, model = vehicleModel,
                        year = vehicleYear, fuelType = vehicleFuelType,
                        odometerKm = vehicleOdometerKm, plate = vehiclePlate, rc = vehicleRc
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
                    onOpenFinding = { id -> navController.navigate("repairGuidance/${encodeRouteArg(id)}") },
                    onRunScan = { navController.navigate("connect") }
                )
            }
            composable(
                "repairGuidance/{findingId}",
                arguments = listOf(navArgument("findingId") { type = NavType.StringType })
            ) { entry ->
                RepairGuidanceDetailScreen(
                    viewModel,
                    findingId = decodeRouteArg(entry.arguments?.getString("findingId")),
                    onAsk = { navController.navigate("aiMechanic") },
                    onSeeAllCosts = { navController.navigate("repairCostReference") },
                    onRunScan = { navController.navigate("connect") },
                    onExpert = { navController.navigate("expertList") }
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
            composable("sinceLastScan") {
                SinceLastScanScreen(viewModel, onAskAi = { navController.navigate("aiMechanic") }, onExpert = { navController.navigate("expertList") })
            }
            composable("attention") {
                AttentionScreen(viewModel, onOpen = { route ->
                    if (route in GARAGE_CHIP_ROUTES) { garageChipRequest.value = route; navController.popBackStack() } else navController.navigate(route)
                })
            }
            composable("learning") { LearningScreen(viewModel) }
            composable("emissionsTrend") { EmissionsTrendScreen(viewModel) }
            composable("detailedDiagnosis") { DetailedDiagnosisScreen(viewModel) }
            composable("prePurchaseCheck") {
                PrePurchaseCheckScreen(viewModel, onStartScan = { navController.navigate("connect") })
            }
            composable("prePurchaseResult") {
                PrePurchaseResultScreen(
                    viewModel,
                    onViewPdf = { file -> navController.navigate("pdfViewer/${encodeRouteArg(file.absolutePath)}") },
                    onTalkToMechanic = { navController.navigate("expertList") },
                    onDone = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("reports") {
                ReportsScreen(viewModel, onNavigate = { route -> navController.navigate(route) })
            }
            composable("allSensors") { AllSensorsScreen(viewModel) }
            composable("milStatus") { MilStatusScreen(viewModel) }
            composable("battery") { BatteryScreen(viewModel, onNavigate = { route -> navController.navigate(route) }) }
            composable("pucCheck") { PucCheckScreen(viewModel) }
            composable("wallet") { WalletScreen(viewModel, onNavigate = { route -> navController.navigate(route) }) }
            composable("autoCapture") { AutoCaptureScreen(onDone = { navController.popBackStack() }) }
            composable("warranty") { WarrantyScreen() }
            composable("marketplace") { MarketplaceScreen() }
            composable("pro") { ProScreen() }
            composable("dealer") {
                DealerScreen(
                    onSetUpDealerProfile = { navController.navigate("dealerProfile") },
                    onStartReport = { navController.navigate("dealerFlow") },
                    onUsedCarCheck = { navController.navigate("prePurchaseCheck") },
                    onBuyPack = { cars -> navController.navigate("dealerCheckout/$cars") },
                    onMyReports = { navController.navigate("dealerReports") }
                )
            }
            composable("dealerCheckout/{cars}") { entry ->
                DealerCheckoutScreen(
                    cars = entry.arguments?.getString("cars")?.toIntOrNull() ?: 5,
                    onDone = { navController.popBackStack() },
                    onStartReport = { navController.navigate("dealerFlow") { popUpTo("dealer") } }
                )
            }
            // Dealer Sathi v2 — scan, car number, inspection, photos; see docs/dealer-sathi-v2.md.
            composable("dealerFlow") {
                DealerFlowScreen(
                    viewModel,
                    onStartScan = { navController.navigate("connect") },
                    onSetUpDealerProfile = { navController.navigate("dealerProfile") },
                    onMyReports = { navController.navigate("dealerReports") },
                    onBuyPack = { navController.navigate("dealerCheckout/5") },
                    onViewPdf = { file -> navController.navigate("pdfViewer/${encodeRouteArg(file.absolutePath)}") }
                )
            }
            // Dealer Saathi "My reports": every report made, credits left, send again.
            composable("dealerReports") { MyReportsScreen(onBuy = { navController.navigate("dealerCheckout/5") }) }
            composable("dealerProfile") { DealerProfileScreen(onSaved = { navController.popBackStack() }) }
            composable("dealerPricing") { DealerPricingScreen() }

            // "Ask a Real Expert" — Phase 2 (preview/mock data, see ConsultationViewModel's doc).
            composable("expertList") {
                ExpertListScreen(
                    consultationViewModel,
                    onSelectExpert = { expertId -> navController.navigate("expertProfile/${encodeRouteArg(expertId)}") },
                    onMyConsultations = { navController.navigate("myConsultations") },
                    onMechanicMode = { navController.navigate("mechanic") }
                )
            }
            // Mechanic mode: a mechanic signed in with their linked Google account manages their own
            // hours, leave and calls.
            composable("mechanic") {
                MechanicHomeScreen(mechanicViewModel, onEditHours = { navController.navigate("mechanicHours") })
            }
            composable("mechanicHours") {
                MechanicHoursScreen(mechanicViewModel, onSaved = { navController.popBackStack() })
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
}
