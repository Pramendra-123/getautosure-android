package com.enginesense.app

import android.app.Application
import android.content.Context
import android.widget.Toast
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enginesense.app.obd.DtcRepository
import com.enginesense.app.obd.DtcRepositoryGenericExplainer
import com.enginesense.app.obd.Elm327Client
import com.enginesense.app.obd.Mode09Reader
import com.enginesense.app.obd.ObdTransport
import com.enginesense.app.obd.SimulatedObdTransport
import com.enginesense.app.obd.SimulatedProfile
import com.enginesense.app.obd.VehicleIdInfo
import com.enginesense.app.auth.GoogleIdTokenCache
import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.DiagnosticRulesEngine
import com.enginesense.app.vehicle.DtcExplanationInput
import com.enginesense.app.vehicle.DtcReading
import com.enginesense.app.vehicle.DtcSource
import com.enginesense.app.vehicle.FuelCombustionInsight
import com.enginesense.app.vehicle.ai.CommonDtcGlossary
import com.enginesense.app.vehicle.FuelSample
import com.enginesense.app.vehicle.HealthScoreCalculator
import com.enginesense.app.vehicle.HealthScoreResult
import com.enginesense.app.vehicle.LiveDerived
import com.enginesense.app.vehicle.LiveDerivedCalculator
import com.enginesense.app.vehicle.LiveHistory
import com.enginesense.app.vehicle.LiveHistorySnapshot
import com.enginesense.app.vehicle.MileageAnalyzer
import com.enginesense.app.vehicle.MileageTrendAnalyzer
import com.enginesense.app.vehicle.MileageTrendResult
import com.enginesense.app.vehicle.PerformanceAnalyzer
import com.enginesense.app.vehicle.PerformanceResult
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReader
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.ReadinessReader
import com.enginesense.app.vehicle.ReadinessStatus
import com.enginesense.app.vehicle.SpeedSample
import com.enginesense.app.vehicle.TripMileageResult
import com.enginesense.app.vehicle.VehicleSnapshot
import com.enginesense.app.storage.PersistedTripMileage
import com.enginesense.app.storage.ScanHistoryStore
import com.enginesense.app.storage.VehicleProfile
import com.enginesense.app.storage.VehicleProfileStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.RcPrefillMapper
import com.enginesense.app.storage.TripMileageStore
import com.enginesense.app.storage.toPersistedScan
import com.enginesense.app.storage.toSnapshot
import com.enginesense.app.vehicle.ai.AiBackendConfig
import com.enginesense.app.vehicle.ai.AiCarHealthReport
import com.enginesense.app.vehicle.ai.AiCarHealthReportService
import com.enginesense.app.vehicle.ai.AiMechanicChatService
import com.enginesense.app.vehicle.ai.AiCarDetails
import com.enginesense.app.vehicle.cards.FaultCardRepository
import com.enginesense.app.vehicle.ai.AiVehicleContext
import com.enginesense.app.vehicle.ai.ChatMessage
import com.enginesense.app.vehicle.ai.FallbackAiMechanicChatService
import com.enginesense.app.vehicle.ai.LocalAutomotiveKnowledgeRetriever
import com.enginesense.app.vehicle.ai.RemoteAiMechanicChatService
import com.enginesense.app.vehicle.ai.RemoteAiNotAuthenticatedException
import com.enginesense.app.vehicle.ai.RuleBasedAiMechanicChatService
import com.enginesense.app.vehicle.ai.TemplateAiCarHealthReportService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.enginesense.app.owner.OwnerCarData
import com.enginesense.app.owner.ExpenseCategory
import com.enginesense.app.owner.ExpenseEntry
import com.enginesense.app.owner.FixedRepair
import com.enginesense.app.owner.ServiceEntry
import com.enginesense.app.owner.EntrySource
import com.enginesense.app.owner.PaymentAiSort
import com.enginesense.app.owner.PaymentAlertListener
import com.enginesense.app.owner.PendingPayment
import com.enginesense.app.owner.RemotePaymentSorter
import com.enginesense.app.vehicle.pricing.AiPriceEstimate
import com.enginesense.app.vehicle.CheckedCar
import com.enginesense.app.vehicle.CheckedCarStore
import com.enginesense.app.vehicle.PrePurchaseReport
import com.enginesense.app.vehicle.pricing.AiPriceQuery
import com.enginesense.app.vehicle.pricing.RemotePriceEstimator
import com.enginesense.app.storage.GarageCar
import com.enginesense.app.storage.GarageState
import com.enginesense.app.storage.GarageStore
import com.enginesense.app.storage.VinMatch
import com.enginesense.app.storage.ChatStore
import com.enginesense.app.storage.ActiveSince
import com.enginesense.app.storage.OwnerDataStore
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One completed scan's full result — what Home/Reports/the detail screens all read from. */
data class ScanResult(
    val timestampMillis: Long,
    val profileLabel: String,
    val isDemo: Boolean,
    val snapshot: VehicleSnapshot,
    val findings: List<DiagnosticFinding>,
    val health: HealthScoreResult,
    val aiReport: AiCarHealthReport,
    val aiContext: AiVehicleContext,
    /** Mode 09 VIN/ECU name/calibration ID — null fields where the ECU didn't answer, never guessed. */
    val vehicleId: VehicleIdInfo,
    /** Mode 01 PID 01 emissions-readiness monitors — null if the adapter couldn't answer at all. */
    val readiness: ReadinessStatus?,
    /** The same breakdown behind the Fuel & Combustion category score — computed once here so the detail screen and the home ring never disagree. */
    val fuelCombustionInsight: FuelCombustionInsight,
    /** Mode 02: the car's snapshot from when it stored a fault code — null on a healthy car or one that can't answer. */
    val freezeFrame: com.enginesense.app.vehicle.FreezeFrame? = null,
    /** Mode 06 self-test results (misfires per cylinder, catalyst, O2 sensors) — null where the car can't answer. */
    val selfTests: com.enginesense.app.vehicle.SelfTestResult? = null
)

/** A scan of a car whose VIN isn't in My Garage: ask whether it's the owner's. */
data class CarQuestion(val scanTimestampMillis: Long, val vin: String)

/** Transient state for the Connect -> Bluetooth -> Scanning sub-flow. Cleared once a [ScanResult] lands. */
sealed interface ConnectionFlowState {
    data object Idle : ConnectionFlowState
    data object Connecting : ConnectionFlowState
    data object Scanning : ConnectionFlowState
    data class Error(val message: String) : ConnectionFlowState
}

/** State for the continuous Live Data screen — separate from [ConnectionFlowState] since "Running" has no equivalent there. */
sealed interface LiveDataState {
    data object Idle : LiveDataState
    data object Connecting : LiveDataState
    data object Running : LiveDataState
    data class Error(val message: String) : LiveDataState
}

/** State for the Performance test screen (0-60, braking, 100m). */
sealed interface PerformanceState {
    data object Idle : PerformanceState
    data object Connecting : PerformanceState
    data object Recording : PerformanceState
    data class Finished(val result: PerformanceResult) : PerformanceState
    data class Error(val message: String) : PerformanceState
}

/** State for the Trip Mileage screen. [Finished.trend] is null when there's no km/L to compare (too short a trip, or an unsupported fuel type). */
sealed interface TripMileageState {
    data object Idle : TripMileageState
    data object Connecting : TripMileageState
    data object Recording : TripMileageState
    /** [signal] needs no trip history (works on trip 1); [trend] is null until there's a real personal baseline — see [MileageTrendAnalyzer]'s class doc for why those stay separate. */
    data class Finished(val result: TripMileageResult, val signal: String?, val trend: MileageTrendResult?) : TripMileageState
    data class Error(val message: String) : TripMileageState
}

/**
 * Orchestrates the existing 13-stage pipeline (transport -> PidReader -> rules engine ->
 * AI layer) and layers the premium-UI state on top: a persistent [latestResult] Home/Reports
 * read from, a [history] timeline (demo scans + whatever's run this session), and AI Mechanic
 * chat tied to the current result. None of the underlying OBD/PID/DTC/rules-engine code
 * changed to support this — this file only composes it differently for multi-screen nav.
 */
class GetAutoSureViewModel(application: Application) : AndroidViewModel(application) {

    private val dtcRepository = DtcRepository(application)
    private val scanHistoryStore = ScanHistoryStore(application)
    private val tripMileageStore = TripMileageStore(application)
    private val vehicleProfileStore = VehicleProfileStore(application)
    private val aiReportService: AiCarHealthReportService = TemplateAiCarHealthReportService()

    // In-memory only, never persisted. A real-device test showed getFreshGoogleIdToken's
    // Credential Manager call triggering an account-picker prompt on every single chat message
    // without this cache — fetching once and reusing until shortly before expiry (per the
    // token's own `exp` claim) cuts that down to roughly once an hour instead.
    private val tokenCache = GoogleIdTokenCache()

    // AiBackendConfig.isConfigured is false until a real backend is deployed and its URL set
    // there — this stays local-only (unchanged default behavior) until that happens.
    private val aiChatService: AiMechanicChatService = run {
        val local = RuleBasedAiMechanicChatService(genericDtcExplainer = DtcRepositoryGenericExplainer(dtcRepository))
        if (AiBackendConfig.isConfigured) {
            FallbackAiMechanicChatService(
                remote = RemoteAiMechanicChatService(
                    endpointUrl = AiBackendConfig.ENDPOINT_URL,
                    // Fetched fresh per request (subject to cachedIdToken above), never captured
                    // once — see RemoteAiMechanicChatService's @param doc for why. Mapped to
                    // RemoteAiNotAuthenticatedException so FallbackAiMechanicChatService can
                    // tell "not signed in" apart from a network failure and surface it to the
                    // user instead of silently substituting a local answer.
                    //
                    // Deliberately reads authUiContext, NOT `application`, on a cache miss: a
                    // real-device test showed getFreshGoogleIdToken failing on every single call
                    // when given the ViewModel's Application context — Credential Manager needs
                    // an Activity-rooted context to host UI even for a "silent" request (verified
                    // against developer.android.com). See authUiContext's own doc for why that
                    // can't just be captured here the same way endpointUrl is.
                    idTokenProvider = {
                        // The app-wide cache, so the sign-in from onboarding is reused here too.
                        val uiContext = authUiContext
                            ?: throw RemoteAiNotAuthenticatedException(IllegalStateException("no UI context available for sign-in"))
                        try {
                            tokenCache.get(uiContext)
                        } catch (e: Exception) {
                            throw RemoteAiNotAuthenticatedException(e)
                        }
                    },
                    knowledgeRetriever = LocalAutomotiveKnowledgeRetriever(),
                    // Read per request, so a change in Profile → Language applies to the next answer.
                    replyLanguageProvider = {
                        com.enginesense.app.storage.LanguagePrefs.get(getApplication())
                            .takeIf { it != com.enginesense.app.storage.AppLanguage.ENGLISH }?.englishName
                    }
                ),
                local = local
            )
        } else {
            local
        }
    }

    // Set only for the duration of one askAiMechanic() call, then cleared — never held longer
    // than that single in-flight request. A ViewModel normally must never cache an Activity
    // context (it would leak across configuration changes), but Credential Manager's
    // getCredential() needs an Activity-rooted one, not `application` (see aiChatService's
    // idTokenProvider above). Bounding the lifetime to one request is the tradeoff: the window
    // for a stale reference is at most how long one token fetch takes, not the ViewModel's
    // lifetime, and it's nulled out in askAiMechanic's `finally` regardless of outcome.
    private var authUiContext: Context? = null

    private var transport: ObdTransport? = null

    private val _connectionState = MutableStateFlow<ConnectionFlowState>(ConnectionFlowState.Idle)
    val connectionState: StateFlow<ConnectionFlowState> = _connectionState.asStateFlow()

    private val _latestResult = MutableStateFlow<ScanResult?>(null)
    val latestResult: StateFlow<ScanResult?> = _latestResult.asStateFlow()

    /** Every own-car scan, for all cars in the garage. */
    private val _history = MutableStateFlow<List<ScanResult>>(emptyList())

    private val _garage = MutableStateFlow(GarageState.EMPTY)
    val garage: StateFlow<GarageState> = _garage.asStateFlow()

    /** Scans of the car picked in My Garage only. */
    val history: StateFlow<List<ScanResult>> = combine(_history, _garage) { all, g ->
        val active = g.active?.id
        all.filter { g.carOfScan(it.timestampMillis) == active }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ownerData = MutableStateFlow(OwnerCarData())
    /** Renewals, services, fill-ups and spending the owner entered for the picked car. */
    val ownerData: StateFlow<OwnerCarData> = _ownerData.asStateFlow()

    /** Set right after a scan of a car that isn't in My Garage — the "Is this your car?" pop-up. */
    private val _carQuestion = MutableStateFlow<CarQuestion?>(null)
    val carQuestion: StateFlow<CarQuestion?> = _carQuestion.asStateFlow()

    /** Text shared into the app from another app — a payment SMS to add as a cost. */
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    fun receiveSharedText(text: String?) {
        _sharedText.value = text?.takeIf { it.isNotBlank() }
    }

    private val _openRoute = MutableStateFlow<String?>(null)
    /** A screen a notification asked to open (e.g. the wallet after a payment was added). */
    val openRoute: StateFlow<String?> = _openRoute.asStateFlow()
    fun requestRoute(route: String?) { _openRoute.value = route }
    fun routeOpened() { _openRoute.value = null }

    fun clearSharedText() {
        _sharedText.value = null
    }

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    /** True while awaiting a reply — the rule-based service is effectively instant, but a future remote implementation won't be. */
    private val _aiMechanicLoading = MutableStateFlow(false)
    val aiMechanicLoading: StateFlow<Boolean> = _aiMechanicLoading.asStateFlow()

    /** Raw AT command/response pairs from the last real-dongle scan — see "View raw log" on the report screen. */
    private val _lastRawLog = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val lastRawLog: StateFlow<List<Pair<String, String>>> = _lastRawLog.asStateFlow()

    private val _vehicleBrand = MutableStateFlow<String?>(null)
    val vehicleBrand: StateFlow<String?> = _vehicleBrand.asStateFlow()

    private val _vehicleNickname = MutableStateFlow("")
    val vehicleNickname: StateFlow<String> = _vehicleNickname.asStateFlow()

    private val _vehicleModel = MutableStateFlow("")
    val vehicleModel: StateFlow<String> = _vehicleModel.asStateFlow()

    /** Captured during Add Vehicle but not consumed by the scan pipeline yet — year/fuel type could refine DTC explanations later, odometer could drive maintenance reminders. Nothing invents a use for them before that's actually built. */
    private val _vehicleYear = MutableStateFlow<Int?>(null)
    val vehicleYear: StateFlow<Int?> = _vehicleYear.asStateFlow()

    private val _vehicleFuelType = MutableStateFlow<String?>(null)
    val vehicleFuelType: StateFlow<String?> = _vehicleFuelType.asStateFlow()

    private val _vehicleOdometerKm = MutableStateFlow<Int?>(null)
    val vehicleOdometerKm: StateFlow<Int?> = _vehicleOdometerKm.asStateFlow()

    private val _vehiclePlate = MutableStateFlow<String?>(null)
    val vehiclePlate: StateFlow<String?> = _vehiclePlate.asStateFlow()

    /** Engine size — from the RTO lookup or typed in. Needed to estimate fuel use on cars without a MAF sensor (see [AirFlowEstimator]). */
    private val _vehicleEngineCc = MutableStateFlow<Int?>(null)
    val vehicleEngineCc: StateFlow<Int?> = _vehicleEngineCc.asStateFlow()

    fun setVehicleEngineCc(cc: Int?) {
        _vehicleEngineCc.value = cc
        persistVehicle()
    }

    /** The RTO record confirmed during Add Vehicle — shown in the advanced and dealer reports. Null when the car was entered by hand. */
    private val _vehicleRc = MutableStateFlow<RcLookupResult?>(null)
    val vehicleRc: StateFlow<RcLookupResult?> = _vehicleRc.asStateFlow()

    fun setVehicleRc(rc: RcLookupResult?) {
        _vehicleRc.value = rc
        persistVehicle()
    }

    /** User-entered only — this app has no vehicle spec catalog, so the manufacturer-claimed (ARAI) figure is never looked up, only typed in. Shown on the Trip Mileage screen as a labeled lab-test reference, never used to judge a "drop". */
    private val _vehicleClaimedMileageKmPerLitre = MutableStateFlow<Double?>(null)
    val vehicleClaimedMileageKmPerLitre: StateFlow<Double?> = _vehicleClaimedMileageKmPerLitre.asStateFlow()

    fun setVehicleClaimedMileage(kmPerLitre: Double?) {
        _vehicleClaimedMileageKmPerLitre.value = kmPerLitre
        persistVehicle()
    }

    /** Set right before navigating into a scan so the post-scan screen knows whether this is "my car" or a car being considered for purchase. Reset once the Pre-Purchase result screen is shown, so a later "Run a new scan" from Home doesn't redirect there by accident. */
    private val _scanIsPrePurchaseCheck = MutableStateFlow(false)
    val scanIsPrePurchaseCheck: StateFlow<Boolean> = _scanIsPrePurchaseCheck.asStateFlow()

    /** The seller's car from the last Used Car Check. Kept apart from [latestResult] and [history], which are the user's own car. */
    private val _prePurchaseResult = MutableStateFlow<ScanResult?>(null)
    val prePurchaseResult: StateFlow<ScanResult?> = _prePurchaseResult.asStateFlow()

    fun startPrePurchaseCheck() {
        _scanIsDealerReport.value = false
        _scanIsPrePurchaseCheck.value = true
        _prePurchaseResult.value = null
    }

    fun clearPrePurchaseCheck() {
        _scanIsPrePurchaseCheck.value = false
        _aiPrice.value = AiPriceState.Idle
    }

    /** The Used Car Check report as the buyer last saw it, in plain lines for the AI Mechanic (see [PrePurchaseReport.aiSummaryLines]). */
    @Volatile private var usedCarReportLines: List<String> = emptyList()

    fun setUsedCarReport(lines: List<String>) {
        usedCarReportLines = lines
    }

    private val checkedCarStore = CheckedCarStore(application)
    private val _checkedCars = MutableStateFlow(checkedCarStore.load())
    /** The last few Used Car Checks, newest first, kept after the app closes. */
    val checkedCars: StateFlow<List<CheckedCar>> = _checkedCars.asStateFlow()

    /** Saves (or updates) this check for the buyer's home screen, once they've reached the price. */
    fun rememberCheckedCar(report: PrePurchaseReport, name: String?, scan: ScanResult) {
        val car = CheckedCar.from(report, name, scan.timestampMillis, scan.health.score)
        if (_checkedCars.value.firstOrNull { it.scanMillis == car.scanMillis } == car) return
        viewModelScope.launch(Dispatchers.IO) { _checkedCars.value = checkedCarStore.save(car) }
    }

    /** The AI market price for the car in the Used Car Check; our formula is used whenever this isn't Ready. */
    sealed interface AiPriceState {
        data object Idle : AiPriceState
        data class Loading(val query: AiPriceQuery) : AiPriceState
        data class Ready(val query: AiPriceQuery, val estimate: AiPriceEstimate) : AiPriceState
        data class Failed(val query: AiPriceQuery) : AiPriceState
    }

    private val _aiPrice = MutableStateFlow<AiPriceState>(AiPriceState.Idle)
    val aiPrice: StateFlow<AiPriceState> = _aiPrice.asStateFlow()
    private val priceEstimator = RemotePriceEstimator()
    private var aiPriceJob: Job? = null

    /**
     * Asks for the AI market price as soon as the car is known (make, model, year), so it's usually
     * ready by the time the buyer reaches the price. The same banded car isn't asked twice, and a
     * changed car cancels the old request. Failure is quiet: the report just keeps our formula's price.
     */
    fun requestAiPrice(query: AiPriceQuery, uiContext: Context, force: Boolean = false) {
        val q = query.banded()
        val current = _aiPrice.value
        val asked = when (current) {
            is AiPriceState.Loading -> current.query
            is AiPriceState.Ready -> current.query
            is AiPriceState.Failed -> if (force) null else current.query
            AiPriceState.Idle -> null
        }
        if (asked == q) return
        aiPriceJob?.cancel()
        _aiPrice.value = AiPriceState.Loading(q)
        aiPriceJob = viewModelScope.launch {
            _aiPrice.value = try {
                AiPriceState.Ready(q, priceEstimator.estimate(q, tokenCache.get(uiContext)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AiPriceState.Failed(q)
            }
        }
    }

    /** Set right before a Dealer Sathi scan, so the result goes to [dealerResult] and the scan screen returns to the dealer flow. */
    private val _scanIsDealerReport = MutableStateFlow(false)
    val scanIsDealerReport: StateFlow<Boolean> = _scanIsDealerReport.asStateFlow()

    /** A car on the dealer's lot. Like [prePurchaseResult], never the user's own car or history. */
    private val _dealerResult = MutableStateFlow<ScanResult?>(null)
    val dealerResult: StateFlow<ScanResult?> = _dealerResult.asStateFlow()

    fun startDealerScan() {
        _scanIsPrePurchaseCheck.value = false
        _scanIsDealerReport.value = true
        _dealerResult.value = null
    }

    fun clearDealerScan() {
        _scanIsDealerReport.value = false
    }

    private val _liveState = MutableStateFlow<LiveDataState>(LiveDataState.Idle)
    val liveState: StateFlow<LiveDataState> = _liveState.asStateFlow()

    private val _liveReadings = MutableStateFlow<Map<PidId, PidReading>>(emptyMap())
    val liveReadings: StateFlow<Map<PidId, PidReading>> = _liveReadings.asStateFlow()

    private val _liveDerived = MutableStateFlow(LiveDerived())
    val liveDerived: StateFlow<LiveDerived> = _liveDerived.asStateFlow()

    /** The last minute of readings per sensor, for Live Data's scrolling graphs. */
    private val _liveHistory = MutableStateFlow(LiveHistorySnapshot())
    val liveHistory: StateFlow<LiveHistorySnapshot> = _liveHistory.asStateFlow()

    private var liveJob: Job? = null

    private val _performanceState = MutableStateFlow<PerformanceState>(PerformanceState.Idle)
    val performanceState: StateFlow<PerformanceState> = _performanceState.asStateFlow()

    private val _performanceLiveSpeed = MutableStateFlow(0)
    val performanceLiveSpeed: StateFlow<Int> = _performanceLiveSpeed.asStateFlow()

    private var performanceJob: Job? = null

    private val _tripMileageState = MutableStateFlow<TripMileageState>(TripMileageState.Idle)
    val tripMileageState: StateFlow<TripMileageState> = _tripMileageState.asStateFlow()

    private val _tripMileageLiveKmPerLitre = MutableStateFlow<Double?>(null)
    val tripMileageLiveKmPerLitre: StateFlow<Double?> = _tripMileageLiveKmPerLitre.asStateFlow()

    private var tripMileageJob: Job? = null
    private var persistedTripMileage: List<PersistedTripMileage> = emptyList()

    fun setVehicle(brand: String?, nickname: String, model: String = "") {
        _vehicleBrand.value = brand
        _vehicleNickname.value = nickname
        _vehicleModel.value = model
        persistVehicle()
    }

    fun setVehicleDetails(year: Int?, fuelType: String?, odometerKm: Int?, plate: String? = null, engineCc: Int? = null) {
        _vehicleYear.value = year
        _vehicleFuelType.value = fuelType
        _vehicleOdometerKm.value = odometerKm
        _vehiclePlate.value = plate
        _vehicleEngineCc.value = engineCc
        // An RTO record for a different plate isn't this car's any more.
        val rc = _vehicleRc.value
        if (rc != null && RcPrefillMapper.normalizePlate(plate.orEmpty()) != RcPrefillMapper.normalizePlate(rc.registrationNumber)) {
            _vehicleRc.value = null
        }
        persistVehicle()
    }

    private val vehicleSaveLock = Mutex()

    /**
     * Saves the car after every change, so it's still there the next time the app opens. The
     * values are read inside the lock, so whichever save runs last writes the newest car — an
     * older save finishing late can't overwrite details set just after it.
     */
    private fun persistVehicle() {
        viewModelScope.launch(Dispatchers.IO) {
            vehicleSaveLock.withLock {
                val profile = currentProfile()
                vehicleProfileStore.save(profile)
                // The picked car in My Garage gets the change; the first car ever added is created here.
                _garage.update { g ->
                    val active = g.active
                    if (active == null) {
                        val car = GarageCar(GarageStore.newCarId(), profile)
                        g.copy(cars = listOf(car), activeId = car.id)
                    } else {
                        g.copy(cars = g.cars.map { if (it.id == active.id) it.copy(profile = profile) else it })
                    }
                }
                garageStore.save(_garage.value)
            }
        }
    }

    private fun currentProfile() = VehicleProfile(
        brand = _vehicleBrand.value, nickname = _vehicleNickname.value, model = _vehicleModel.value,
        year = _vehicleYear.value, fuelType = _vehicleFuelType.value, odometerKm = _vehicleOdometerKm.value,
        plate = _vehiclePlate.value, engineCc = _vehicleEngineCc.value,
        claimedMileageKmPerLitre = _vehicleClaimedMileageKmPerLitre.value, rc = _vehicleRc.value
    )

    private val garageStore = GarageStore(application)
    private val ownerDataStore = OwnerDataStore(application)
    private val chatStore = ChatStore(application)

    private suspend fun loadVehicle() {
        val g = withContext(Dispatchers.IO) { garageStore.load() }
        _garage.value = g
        val p = g.active?.profile ?: return
        showProfile(p)
        _ownerData.value = withContext(Dispatchers.IO) { ownerDataStore.load(g.active!!.id) }
        _chatMessages.value = withContext(Dispatchers.IO) { chatStore.load(g.active!!.id) }
    }

    /** Puts a car's details into the flows every screen reads. */
    private fun showProfile(p: VehicleProfile) {
        _vehicleBrand.value = p.brand
        _vehicleNickname.value = p.nickname
        _vehicleModel.value = p.model
        _vehicleYear.value = p.year
        _vehicleFuelType.value = p.fuelType
        _vehicleOdometerKm.value = p.odometerKm
        _vehiclePlate.value = p.plate
        _vehicleEngineCc.value = p.knownEngineCc
        _vehicleClaimedMileageKmPerLitre.value = p.claimedMileageKmPerLitre
        _vehicleRc.value = p.rc
    }

    init {
        viewModelScope.launch {
            loadVehicle()
            loadPersistedHistory()
            // Example timeline scans help while testing; customers only ever see their own.
            if (BuildConfig.DEBUG) seedDemoHistory()
            persistedTripMileage = withContext(Dispatchers.IO) { tripMileageStore.load() }
        }
        // A payment added by auto-capture lands on disk; show it in the open wallet.
        viewModelScope.launch {
            com.enginesense.app.owner.PaymentAlertListener.captures.collect { stamp ->
                if (stamp == 0L) return@collect
                val id = _garage.value.active?.id ?: return@collect
                _ownerData.value = withContext(Dispatchers.IO) { ownerDataStore.load(id) }
            }
        }
    }

    /** Real scans from previous app sessions — demo scans are never persisted, only the illustration they're meant to be. */
    private suspend fun loadPersistedHistory() {
        val persisted = withContext(Dispatchers.IO) { scanHistoryStore.load() }
        if (persisted.isEmpty()) return
        val results = persisted.map { p ->
            computeScanResult(p.toSnapshot(), p.vehicleId, p.readiness, p.profileLabel, p.isDemo, p.timestampMillis, p.freezeFrame, p.selfTests)
        }
        _history.update { results + it }
        _latestResult.update { newestScanOf(_garage.value.active?.id) }
    }

    /** Saves every real (non-demo) scan currently in [history] — called after any change to it, so disk always matches what's on screen. */
    private fun persistHistory() {
        val realScans = _history.value.filter { !it.isDemo }.map { it.toPersistedScan() }
        viewModelScope.launch(Dispatchers.IO) { scanHistoryStore.save(realScans) }
    }

    private fun newestScanOf(carId: String?): ScanResult? =
        _history.value.filter { !it.isDemo && _garage.value.carOfScan(it.timestampMillis) == carId }.maxByOrNull { it.timestampMillis }

    private fun saveGarage() {
        val snapshot = _garage.value
        viewModelScope.launch(Dispatchers.IO) { vehicleSaveLock.withLock { garageStore.save(snapshot) } }
    }

    /** Picks another car in My Garage: its details, its last scan and its own records come up everywhere. */
    fun switchCar(carId: String) {
        val car = _garage.value.cars.firstOrNull { it.id == carId } ?: return
        if (car.id == _garage.value.active?.id) return
        _garage.update { it.copy(activeId = car.id, activeLog = it.activeLog + ActiveSince(System.currentTimeMillis(), car.id)) }
        saveGarage()
        showProfile(car.profile)
        vehicleProfileStore.let { store -> viewModelScope.launch(Dispatchers.IO) { store.save(car.profile) } }
        _latestResult.value = newestScanOf(car.id)
        // Each car keeps its own conversation.
        _chatMessages.value = emptyList()
        viewModelScope.launch {
            _ownerData.value = withContext(Dispatchers.IO) { ownerDataStore.load(car.id) }
            _chatMessages.value = withContext(Dispatchers.IO) { chatStore.load(car.id) }
        }
    }

    /** Saves the picked car's conversation; a used-car check's chat isn't the owner's, so it isn't kept. */
    private fun saveChat() {
        val carId = _garage.value.active?.id ?: return
        val messages = _chatMessages.value
        viewModelScope.launch(Dispatchers.IO) { chatStore.save(carId, messages) }
    }

    /** "New chat": clears the picked car's conversation. */
    fun clearChat() {
        _chatMessages.value = emptyList()
        saveChat()
    }

    /** After a new scan the conversation goes on, with a line saying later answers use the new scan. */
    private fun noteNewScanInChat() {
        // From the saved file, not the screen's list: the scan may have just switched cars, whose chat is still loading.
        val carId = _garage.value.active?.id ?: return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { chatStore.load(carId) }
            if (saved.isEmpty()) return@launch
            val updated = (saved + ChatMessage(fromUser = false, text = "— New scan taken. Answers from here use it. —")).takeLast(MAX_CHAT_MESSAGES)
            withContext(Dispatchers.IO) { chatStore.save(carId, updated) }
            if (_garage.value.active?.id == carId) _chatMessages.value = updated
        }
    }

    /** Adds an empty car and picks it; the add-car screens then fill in its details. */
    fun addCar(vin: String? = null): String {
        val car = GarageCar(GarageStore.newCarId(), VehicleProfile(null, "", "", null, null, null, null, null, null, null), vin)
        _garage.update { it.copy(cars = it.cars + car) }
        saveGarage()
        // An empty car must not inherit the previous car's details.
        switchCar(car.id)
        return car.id
    }

    /** Removes a car and its records; its scans stay on disk but no longer show. */
    fun removeCar(carId: String) {
        val g = _garage.value
        if (g.cars.size <= 1) return
        val remaining = g.cars.filter { it.id != carId }
        _garage.update { it.copy(cars = remaining) }
        if (g.active?.id == carId) {
            _garage.update { it.copy(activeId = null) }
            switchCar(remaining.first().id)
        }
        saveGarage()
        viewModelScope.launch(Dispatchers.IO) { ownerDataStore.delete(carId); chatStore.delete(carId) }
    }

    /** Changes the picked car's owner records (renewal dates, services, fill-ups, spending) and saves them. */
    fun updateOwnerData(change: (OwnerCarData) -> OwnerCarData) {
        val carId = _garage.value.active?.id ?: run {
            // No car yet: save the empty one first so the records have somewhere to live.
            persistVehicle(); null
        }
        _ownerData.update(change)
        val data = _ownerData.value
        viewModelScope.launch(Dispatchers.IO) {
            val id = carId ?: vehicleSaveLock.withLock { _garage.value.active?.id } ?: return@launch
            ownerDataStore.save(id, data)
        }
    }

    private val paymentSorter = RemotePaymentSorter()

    /** Wallet switch: AI sorts payment alerts the rules can't. Off by default; turning it off drops the queue. */
    fun setAiSorting(on: Boolean) {
        updateOwnerData { it.copy(aiSortingOn = on, pendingPayments = if (on) it.pendingPayments else emptyList()) }
    }

    /**
     * Sorts the picked car's queued payment alerts — called when the Wallet opens, where a signed-in
     * context is at hand. Clear car payments are added (tagged AI) and clear non-car ones dropped;
     * both teach the merchant. Unsure ones wait for the owner. Failures leave the queue for next time.
     */
    fun sortPendingPayments(uiContext: Context) {
        val queue = _ownerData.value.pendingPayments.filter { it.suggestion == null }.take(RemotePaymentSorter.BATCH)
        if (queue.isEmpty() || !_ownerData.value.aiSortingOn) return
        viewModelScope.launch {
            val sorted = try {
                paymentSorter.sort(queue, tokenCache.get(uiContext))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            val byId = sorted.associateBy { it.id }
            updateOwnerData { d ->
                var data = d
                val keep = mutableListOf<PendingPayment>()
                d.pendingPayments.forEach { p ->
                    val s = byId[p.id]
                    when {
                        s == null -> keep += p
                        s.confidence == "high" && s.category == PaymentAiSort.NOT_CAR ->
                            data = data.copy(merchantCategories = data.merchantCategories + (PaymentAiSort.merchantKey(p.merchant) to PaymentAiSort.NOT_CAR))
                        s.confidence == "high" -> {
                            data = PaymentAlertListener.addSorted(data, s.category, System.currentTimeMillis() + keep.size, p.date, p.amountInr, p.merchant, EntrySource.AI)
                            data = data.copy(merchantCategories = data.merchantCategories + (PaymentAiSort.merchantKey(p.merchant) to s.category))
                        }
                        s.category == PaymentAiSort.NOT_CAR -> Unit // a guess that it isn't for the car: not worth asking about
                        else -> keep += p.copy(suggestion = s.category, confidence = s.confidence)
                    }
                }
                data.copy(pendingPayments = keep)
            }
        }
    }

    /** Undo on a payment AI added: it's removed, and its shop is remembered as not for the car. */
    fun undoAiEntry(id: Long) {
        updateOwnerData { d ->
            val e = d.expenses.firstOrNull { it.id == id && it.source == EntrySource.AI }
            val learned = e?.note?.takeIf { it.isNotBlank() }?.let { d.merchantCategories + (PaymentAiSort.merchantKey(it) to PaymentAiSort.NOT_CAR) } ?: d.merchantCategories
            d.copy(
                expenses = d.expenses.filterNot { it.id == id && it.source == EntrySource.AI },
                fuel = d.fuel.filterNot { it.id == id && it.source == EntrySource.AI },
                merchantCategories = learned
            )
        }
    }

    /** The owner's answer to "Was this for your car?": [category] to add it, null for "not for my car". Either way the merchant is remembered. */
    fun answerPendingPayment(id: String, category: String?) {
        updateOwnerData { d ->
            val p = d.pendingPayments.firstOrNull { it.id == id } ?: return@updateOwnerData d
            val key = PaymentAiSort.merchantKey(p.merchant)
            val rest = d.copy(pendingPayments = d.pendingPayments - p)
            if (category == null) {
                rest.copy(merchantCategories = rest.merchantCategories + (key to PaymentAiSort.NOT_CAR))
            } else {
                PaymentAlertListener.addSorted(rest, category, System.currentTimeMillis(), p.date, p.amountInr, p.merchant, EntrySource.ALERT)
                    .let { it.copy(merchantCategories = it.merchantCategories + (key to category)) }
            }
        }
    }

    /**
     * "Mark as fixed" on a repair: what was paid goes into the Wallet as a Repair and into the
     * service log, and the fault is remembered so the next scan can confirm it's really gone.
     */
    fun markRepairFixed(code: String, title: String, paidInr: Int?) {
        val today = java.time.LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val afterScan = _latestResult.value?.timestampMillis ?: now
        updateOwnerData { d ->
            d.copy(
                expenses = if (paidInr != null && paidInr > 0) d.expenses + ExpenseEntry(now, today, ExpenseCategory.REPAIR, paidInr, "$title ($code)") else d.expenses,
                // The cost lives on the Wallet expense; the log entry carries none, or the year's spend would count it twice.
                services = d.services + ServiceEntry(now, today, _vehicleOdometerKm.value, "Repair: $title ($code)", null),
                fixedRepairs = d.fixedRepairs.filter { it.code != code || it.verified } + FixedRepair(code, title, today, paidInr, afterScan)
            )
        }
    }

    /** What the scan after a "Mark as fixed" showed: the fault gone (fixed) or back. Shown once, then cleared. */
    data class FixConfirmation(val repair: FixedRepair, val fixed: Boolean, val carTitle: String, val testsDone: Int?, val testsTotal: Int?)

    private val _fixConfirmation = MutableStateFlow<FixConfirmation?>(null)
    val fixConfirmation: StateFlow<FixConfirmation?> = _fixConfirmation.asStateFlow()

    fun dismissFixConfirmation() { _fixConfirmation.value = null }

    /** After an own-car scan: any repair marked before it is confirmed (its code is gone) or flagged (it's back). */
    private fun checkFixedRepairs(result: ScanResult) {
        val carId = _garage.value.carOfScan(result.timestampMillis) ?: return
        val car = _garage.value.cars.firstOrNull { it.id == carId } ?: return
        if (result.isDemo) return
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { ownerDataStore.load(carId) }
            val pending = data.fixedRepairs.filter { !it.verified && it.afterScanMillis < result.timestampMillis }
            if (pending.isEmpty()) return@launch
            val present = result.snapshot.dtcs.map { it.code.uppercase() }.toSet() + result.findings.flatMap { it.relatedDtcCodes }.map { it.uppercase() }
            val first = pending.first()
            val fixed = first.code.uppercase() !in present
            // A fix stays pending until a scan shows the code gone; a fault that came back is dropped so it can be marked again.
            val updated = data.copy(fixedRepairs = data.fixedRepairs.mapNotNull { r ->
                when {
                    r !in pending -> r
                    r.code.uppercase() in present -> null
                    else -> r.copy(verified = true)
                }
            })
            withContext(Dispatchers.IO) { ownerDataStore.save(carId, updated) }
            if (_garage.value.active?.id == carId) _ownerData.value = updated
            val monitors = result.readiness?.monitors?.filter { it.supported }
            _fixConfirmation.value = FixConfirmation(first, fixed, car.title, monitors?.count { it.ready }, monitors?.size)
        }
    }

    /**
     * Ties a new own-car scan to a car in My Garage by its VIN: the car with that VIN, else the one
     * car without a VIN whose RTO chassis number confirms it (it learns the VIN), switching to it
     * either way. The picked car learns a VIN only if its chassis number and maker don't rule it out
     * — a scan taken in another car once made a Kia "learn" a Skoda's VIN. Anything else asks
     * "Is this your car?".
     */
    private fun assignScanToCar(result: ScanResult) {
        val g = _garage.value
        val vin = result.vehicleId.vin?.trim()?.takeIf { it.length >= 11 && !result.isDemo }
        val active = g.active
        val match = vin?.let { v ->
            g.cars.firstOrNull { it.vin == v }
                ?: g.cars.filter { it.vin == null && VinMatch.fits(it, v) == true }.singleOrNull()
        }
        val activeCanLearn = vin != null && active != null && active.vin == null && VinMatch.fits(active, vin) != false
        val carId = when {
            match != null -> match.id
            activeCanLearn -> active!!.id
            else -> active?.id
        }
        val learner = when {
            match != null && match.vin == null -> match.id
            match == null && activeCanLearn -> active!!.id
            else -> null
        }
        if (learner != null) _garage.update { s -> s.copy(cars = s.cars.map { if (it.id == learner) it.copy(vin = vin) else it }) }
        if (carId != null) _garage.update { it.copy(scanCars = it.scanCars + (result.timestampMillis to carId)) }
        saveGarage()
        if (vin != null && match == null && !activeCanLearn && active?.vin != vin) {
            _carQuestion.value = CarQuestion(result.timestampMillis, vin)
        }
        if (match != null && match.id != active?.id) {
            switchCar(match.id)
            Toast.makeText(getApplication(), "This scan is from your ${match.title}. Switched to it.", Toast.LENGTH_LONG).show()
        }
    }

    /** "Yes, add to My Garage": the scan moves to a new car, which is picked so its details can be added. */
    fun answerCarIsMine() {
        val q = _carQuestion.value ?: return
        _carQuestion.value = null
        val scan = _history.value.firstOrNull { it.timestampMillis == q.scanTimestampMillis }
        val id = addCar(q.vin)
        _garage.update { it.copy(scanCars = it.scanCars + (q.scanTimestampMillis to id)) }
        saveGarage()
        _latestResult.value = scan
    }

    /** "I'm checking it before buying": the scan leaves the owner's history and opens as a used-car check. */
    fun answerCarIsForSale() {
        val q = _carQuestion.value ?: return
        _carQuestion.value = null
        val scan = _history.value.firstOrNull { it.timestampMillis == q.scanTimestampMillis } ?: return
        _history.update { list -> list.filter { it.timestampMillis != q.scanTimestampMillis } }
        persistHistory()
        _garage.update { it.copy(scanCars = it.scanCars - q.scanTimestampMillis) }
        saveGarage()
        _prePurchaseResult.value = scan
        _latestResult.value = newestScanOf(_garage.value.active?.id)
    }

    /** "It's this car": the picked car's VIN is updated (an engine computer can be replaced). */
    fun answerCarIsSame() {
        val q = _carQuestion.value ?: return
        _carQuestion.value = null
        val activeId = _garage.value.active?.id ?: return
        _garage.update { s -> s.copy(cars = s.cars.map { if (it.id == activeId) it.copy(vin = q.vin) else it }) }
        saveGarage()
    }

    fun pairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun scanWithDevice(device: BluetoothDevice, vehicleBrand: String?) {
        runScan(profileLabel = "Real dongle", isDemo = false, vehicleBrand = vehicleBrand) { Elm327Client(device) }
    }

    /** Runs the full flow against the simulator — the "test mode" that needs no physical hardware. */
    fun scanSimulated(profile: SimulatedProfile, vehicleBrand: String?) {
        runScan(profileLabel = profile.name, isDemo = false, vehicleBrand = vehicleBrand) { SimulatedObdTransport(profile) }
    }

    fun resetConnectionState() {
        _connectionState.update { ConnectionFlowState.Idle }
    }

    private fun runScan(
        profileLabel: String,
        isDemo: Boolean,
        vehicleBrand: String?,
        makeTransport: () -> ObdTransport
    ) {
        // Guard against a second scan starting while one is already in flight — tapping
        // through the Connect flow again before the first scan's screen transition finishes
        // was firing two concurrent navigate("home") calls, which is what produced the
        // overlapping/garbled text seen on a real device (two crossfades stacking). Error
        // isn't "in flight" though — retrying after a failed scan must still be allowed.
        val current = _connectionState.value
        if (current is ConnectionFlowState.Connecting || current is ConnectionFlowState.Scanning) return

        viewModelScope.launch {
            _connectionState.update { ConnectionFlowState.Connecting }
            try {
                val result = buildScanResult(profileLabel, isDemo, vehicleBrand, System.currentTimeMillis(), makeTransport)
                _connectionState.update { ConnectionFlowState.Scanning }
                if (_scanIsDealerReport.value) {
                    _dealerResult.update { result }
                } else if (_scanIsPrePurchaseCheck.value) {
                    // Someone else's car: never let it replace the user's own scan or history.
                    _prePurchaseResult.update { result }
                } else {
                    _latestResult.update { result }
                    _history.update { listOf(result) + it }
                    assignScanToCar(result)
                    checkFixedRepairs(result)
                    persistHistory()
                    noteNewScanInChat()
                }
                _lastRawLog.update { transport?.commandLog() ?: emptyList() }
                _connectionState.update { ConnectionFlowState.Idle }
            } catch (e: Exception) {
                _connectionState.update { ConnectionFlowState.Error(e.message ?: "Couldn't complete the scan.") }
            }
        }
    }

    private suspend fun buildScanResult(
        profileLabel: String,
        isDemo: Boolean,
        vehicleBrand: String?,
        timestampMillis: Long,
        makeTransport: () -> ObdTransport
    ): ScanResult {
        val t = withContext(Dispatchers.IO) {
            val newTransport = makeTransport()
            newTransport.connect()
            newTransport
        }
        if (!isDemo) {
            transport?.close()
            transport = t
        }

        val result = readScanResult(t, profileLabel, isDemo, vehicleBrand, timestampMillis)

        if (isDemo) withContext(Dispatchers.IO) { t.close() }
        return result
    }

    /** Reads PIDs + DTCs and builds the full result from an already-connected transport — no (re)connect. */
    private suspend fun readScanResult(
        t: ObdTransport,
        profileLabel: String,
        isDemo: Boolean,
        vehicleBrand: String?,
        timestampMillis: Long
    ): ScanResult {
        val snapshot = withContext(Dispatchers.IO) {
            val reader = PidReader(t)
            val readings = reader.readAll()
            val dtcCodes = Elm327Client.parseDtcResponse(t.sendCommand("03"))
            // Pending/permanent are extras for the pre-purchase check — an adapter or ECU that
            // chokes on them must not cost the user the rest of the scan.
            val pendingCodes = runCatching { Elm327Client.parseDtcResponse(t.sendCommand("07"), modeEcho = "47") }.getOrDefault(emptyList())
            val permanentCodes = runCatching { Elm327Client.parseDtcResponse(t.sendCommand("0A"), modeEcho = "4A") }.getOrDefault(emptyList())

            VehicleSnapshot(
                timestampMillis = timestampMillis,
                vehicleBrand = vehicleBrand,
                readings = readings,
                dtcs = dtcCodes.map { DtcReading(it, DtcSource.STORED) } +
                    pendingCodes.map { DtcReading(it, DtcSource.PENDING) } +
                    permanentCodes.map { DtcReading(it, DtcSource.PERMANENT) }
            )
        }

        val vehicleId = withContext(Dispatchers.IO) { Mode09Reader.read(t) }
        val readiness = withContext(Dispatchers.IO) { ReadinessReader.read(t) }
        // Extras: a car or adapter that chokes on these must not cost the user the rest of the scan.
        val freezeFrame = withContext(Dispatchers.IO) { runCatching { com.enginesense.app.vehicle.FreezeFrameReader.read(t) }.getOrNull() }
        val selfTests = withContext(Dispatchers.IO) { runCatching { com.enginesense.app.vehicle.SelfTests.read(t) }.getOrNull() }

        return computeScanResult(snapshot, vehicleId, readiness, profileLabel, isDemo, timestampMillis, freezeFrame, selfTests)
    }

    /**
     * The pure-compute half of a scan — everything after the transport reads. Pulled out so
     * [ScanHistoryStore]'s persisted scans (which only keep the raw snapshot/vehicleId/
     * readiness, not the derived findings/health/AI report — see that class's doc) can be
     * replayed through the exact same pipeline a live scan uses, without needing a transport.
     * This also means a historical scan always reflects current scoring logic, not whatever
     * it was the day it was taken.
     */
    private suspend fun computeScanResult(
        snapshot: VehicleSnapshot,
        vehicleId: VehicleIdInfo,
        readiness: ReadinessStatus?,
        profileLabel: String,
        isDemo: Boolean,
        timestampMillis: Long,
        freezeFrame: com.enginesense.app.vehicle.FreezeFrame? = null,
        selfTests: com.enginesense.app.vehicle.SelfTestResult? = null
    ): ScanResult {
        val explanations = snapshot.dtcs.associate { dtc ->
            val explained = dtcRepository.explain(dtc.code, snapshot.vehicleBrand)
            dtc.code.uppercase() to DtcExplanationInput(explained.plainLanguage, explained.severity, dtcTitle(dtc.code, explained.rawDescription))
        }

        val findings = DiagnosticRulesEngine.evaluate(snapshot, explanations)
        val health = HealthScoreCalculator.calculate(snapshot, findings, readiness)
        val aiContext = AiVehicleContext.from(snapshot, findings, vehicleId, readiness)
        val aiReport = aiReportService.generateReport(aiContext)

        return ScanResult(timestampMillis, profileLabel, isDemo, snapshot, findings, health, aiReport, aiContext, vehicleId, readiness, health.fuelCombustionInsight, freezeFrame, selfTests)
    }

    /**
     * Sends Mode 04 (clear stored DTCs/MIL) on the currently connected transport, then
     * re-reads everything so the UI reflects the cleared state immediately. Only available
     * after a real or test-mode scan (not a demo history entry, which has no live transport).
     * This changes the vehicle's actual ECU state — the UI gates this behind an explicit
     * confirmation dialog, same as every reference OBD2 app does.
     */
    fun clearDtcsAndRescan() {
        val t = transport ?: return
        val previous = _latestResult.value ?: return
        val current = _connectionState.value
        if (current is ConnectionFlowState.Connecting || current is ConnectionFlowState.Scanning) return

        viewModelScope.launch {
            _connectionState.update { ConnectionFlowState.Connecting }
            try {
                withContext(Dispatchers.IO) { t.sendCommand("04") }
                _connectionState.update { ConnectionFlowState.Scanning }
                val result = readScanResult(t, previous.profileLabel, isDemo = false, vehicleBrand = _vehicleBrand.value, timestampMillis = System.currentTimeMillis())
                _latestResult.update { result }
                _history.update { listOf(result) + it }
                assignScanToCar(result)
                checkFixedRepairs(result)
                persistHistory()
                noteNewScanInChat()
                _lastRawLog.update { t.commandLog() }
                _connectionState.update { ConnectionFlowState.Idle }
            } catch (e: Exception) {
                _connectionState.update { ConnectionFlowState.Error(e.message ?: "Couldn't clear codes.") }
            }
        }
    }

    /** Demo/test data only, clearly labeled — illustrates the Car Health Timeline without needing five real scans. */
    private suspend fun seedDemoHistory() {
        val dayMillis = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val profiles = listOf(
            SimulatedProfile.HEALTHY to 2,
            SimulatedProfile.MISFIRE_DTC to 6,
            SimulatedProfile.OVERHEATING to 10,
            SimulatedProfile.LOW_BATTERY_VOLTAGE to 15,
            SimulatedProfile.VOLTAGE_PID_UNSUPPORTED to 21
        )
        val demoResults = profiles.map { (profile, daysAgo) ->
            buildScanResult(
                profileLabel = profile.name,
                isDemo = true,
                vehicleBrand = null,
                timestampMillis = now - daysAgo * dayMillis
            ) { SimulatedObdTransport(profile) }
        }
        _history.update { demoResults + it }
    }

    /**
     * Continuously re-reads PIDs for as long as the Live Data screen is open — a one-shot scan
     * can't show RPM climbing as you accelerate, only a repeated-read loop can. Every PID is a
     * real Bluetooth round-trip, so to stay "live" with a longer list:
     * - [LIVE_FAST_PIDS] (what moves with the pedal) are read every cycle; everything else only
     *   every [LIVE_SLOW_EVERY]th cycle — temperatures and fuel level don't change in 200ms.
     * - A PID the car reports as unsupported is never asked for again this session (and gets no
     *   tile), instead of costing a round-trip every cycle forever.
     * Mileage, trip average and boost are worked out from these readings — see [LiveDerivedCalculator].
     */
    fun startLiveData(makeTransport: () -> ObdTransport) {
        stopLiveData()
        liveJob = viewModelScope.launch {
            _liveState.update { LiveDataState.Connecting }
            try {
                val t = withContext(Dispatchers.IO) {
                    val newTransport = makeTransport()
                    newTransport.connect()
                    newTransport
                }
                transport?.close()
                transport = t

                val reader = PidReader(t)
                _liveState.update { LiveDataState.Running }

                val unsupported = mutableSetOf<PidId>()
                val latest = mutableMapOf<PidId, PidReading>()
                val tripSamples = mutableListOf<FuelSample>()
                val history = LiveHistory()
                val startNanos = System.nanoTime()
                var turboSeen = false
                var cycle = 0

                while (isActive) {
                    val toRead = LIVE_DATA_PIDS.filter { pid ->
                        pid !in unsupported && (pid in LIVE_FAST_PIDS || cycle % LIVE_SLOW_EVERY == 0)
                    }
                    val fresh = withContext(Dispatchers.IO) { toRead.associateWith { reader.read(it) } }
                    fresh.forEach { (pid, reading) ->
                        if (reading.status == PidStatus.UNSUPPORTED) unsupported += pid
                        latest[pid] = reading
                    }
                    history.add(fresh, (System.nanoTime() - startNanos) / 1_000_000)

                    val air = LiveDerivedCalculator.airFlow(latest, _vehicleEngineCc.value)
                    val speed = LiveDerivedCalculator.value(latest, PidId.SPEED_KMH)
                    if (air != null && speed != null) {
                        tripSamples.add(FuelSample((System.nanoTime() - startNanos) / 1_000_000, speed.toInt(), air.gramsPerSec))
                    }
                    val derived = LiveDerivedCalculator.compute(latest, _vehicleEngineCc.value, _vehicleFuelType.value, tripSamples, turboSeen)
                    turboSeen = derived.turboSeen

                    _liveReadings.update { latest.toMap() }
                    _liveDerived.update { derived }
                    _liveHistory.update { history.snapshot() }
                    cycle++
                    // A breather for the adapter, not a refresh-rate cap — the reads themselves set the pace.
                    delay(LIVE_LOOP_PAUSE_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _liveState.update { LiveDataState.Error(e.message ?: "Live data stopped unexpectedly.") }
            }
        }
    }

    fun stopLiveData() {
        liveJob?.cancel()
        liveJob = null
        _liveState.update { LiveDataState.Idle }
        _liveReadings.update { emptyMap() }
        _liveDerived.update { LiveDerived() }
        _liveHistory.update { LiveHistorySnapshot() }
    }

    /** From the Live Data screen when mileage needs details onboarding didn't collect (e.g. onboarding was skipped). */
    fun setVehicleFuelType(fuelType: String?) {
        _vehicleFuelType.value = fuelType
        persistVehicle()
    }

    /**
     * Polls vehicle speed as fast as the adapter round-trip allows (no artificial delay,
     * unlike [startLiveData]'s 600ms pace) so the 0-60/braking/100m measurements in
     * [PerformanceAnalyzer] have enough time resolution to be meaningful. Stopping the test
     * (button tap, or leaving the screen) cancels the loop; the samples collected up to that
     * point are analyzed and published as [PerformanceState.Finished] rather than thrown
     * away, so a short/incomplete run still shows whatever it actually measured — nulls for
     * anything it didn't reach, never a guess.
     */
    fun startPerformanceTest(makeTransport: () -> ObdTransport) {
        stopPerformanceTest()
        val samples = mutableListOf<SpeedSample>()
        performanceJob = viewModelScope.launch {
            _performanceState.update { PerformanceState.Connecting }
            var recording = false
            try {
                val t = withContext(Dispatchers.IO) {
                    val newTransport = makeTransport()
                    newTransport.connect()
                    newTransport
                }
                transport?.close()
                transport = t

                val reader = PidReader(t)
                _performanceState.update { PerformanceState.Recording }
                recording = true
                val startNanos = System.nanoTime()

                while (isActive) {
                    val reading = withContext(Dispatchers.IO) { reader.read(PidId.SPEED_KMH) }
                    if (reading.status == PidStatus.VERIFIED && reading.value != null) {
                        val speed = reading.value.toInt()
                        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                        samples.add(SpeedSample(elapsedMs, speed))
                        _performanceLiveSpeed.update { speed }
                    }
                }
            } catch (e: CancellationException) {
                if (recording) {
                    _performanceState.update { PerformanceState.Finished(PerformanceAnalyzer.analyze(samples)) }
                }
                throw e
            } catch (e: Exception) {
                _performanceState.update { PerformanceState.Error(e.message ?: "Performance test stopped unexpectedly.") }
            }
        }
    }

    fun stopPerformanceTest() {
        performanceJob?.cancel()
        performanceJob = null
    }

    /**
     * Polls MAF + vehicle speed together (no artificial delay, same as [startPerformanceTest])
     * so [MileageAnalyzer] can integrate fuel economy over the trip. On stop, two independent
     * things are computed — see [MileageTrendAnalyzer]'s class doc for why they're kept separate:
     * a mileage-affecting signal from the latest scan (works on trip 1, no history needed), and
     * a trend vs this car's own prior trips (needs at least two to mean anything). This trip is
     * then persisted to [tripMileageStore] to extend that baseline for next time.
     */
    fun startTripMileage(makeTransport: () -> ObdTransport) {
        stopTripMileage()
        val samples = mutableListOf<FuelSample>()
        tripMileageJob = viewModelScope.launch {
            _tripMileageState.update { TripMileageState.Connecting }
            var recording = false
            try {
                val t = withContext(Dispatchers.IO) {
                    val newTransport = makeTransport()
                    newTransport.connect()
                    newTransport
                }
                transport?.close()
                transport = t

                val reader = PidReader(t)
                _tripMileageState.update { TripMileageState.Recording }
                recording = true
                val startNanos = System.nanoTime()

                // Cars without a MAF sensor (common in India) get air flow estimated from manifold
                // pressure instead — see AirFlowEstimator. Intake temperature barely moves, so it's
                // refreshed every 10th sample rather than costing a round-trip each time.
                var hasMaf = true
                var intakeAirTemp: PidReading? = null
                var sampleCount = 0
                while (isActive) {
                    val readings = withContext(Dispatchers.IO) {
                        val r = mutableMapOf(PidId.SPEED_KMH to reader.read(PidId.SPEED_KMH))
                        if (hasMaf) {
                            val maf = reader.read(PidId.MAF_AIR_FLOW_RATE)
                            r[PidId.MAF_AIR_FLOW_RATE] = maf
                            if (maf.status == PidStatus.UNSUPPORTED) hasMaf = false
                        }
                        if (!hasMaf) {
                            r[PidId.INTAKE_MANIFOLD_PRESSURE] = reader.read(PidId.INTAKE_MANIFOLD_PRESSURE)
                            r[PidId.RPM] = reader.read(PidId.RPM)
                            if (intakeAirTemp == null || sampleCount % 10 == 0) intakeAirTemp = reader.read(PidId.INTAKE_AIR_TEMP_C)
                            intakeAirTemp?.let { r[PidId.INTAKE_AIR_TEMP_C] = it }
                        }
                        r
                    }
                    sampleCount++
                    val air = LiveDerivedCalculator.airFlow(readings, _vehicleEngineCc.value)
                    val speed = LiveDerivedCalculator.value(readings, PidId.SPEED_KMH)
                    if (air != null && speed != null) {
                        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                        samples.add(FuelSample(elapsedMs, speed.toInt(), air.gramsPerSec))
                        _tripMileageLiveKmPerLitre.update { MileageAnalyzer.analyze(samples, _vehicleFuelType.value).averageKmPerLitre }
                    }
                }
            } catch (e: CancellationException) {
                if (recording) {
                    val result = MileageAnalyzer.analyze(samples, _vehicleFuelType.value)
                    val avg = result.averageKmPerLitre

                    // Needs no trip history at all — an elevated fuel trim or active misfire is
                    // a real signal on its own, so this can answer on trip 1.
                    val signal = _latestResult.value?.let { r ->
                        MileageTrendAnalyzer.currentScanSignal(
                            fuelCombustionState = r.fuelCombustionInsight.state,
                            fuelCombustionInterpretation = r.fuelCombustionInsight.interpretation,
                            activeFindings = r.findings
                        )
                    }

                    val trend = avg?.let {
                        val t = MileageTrendAnalyzer.analyze(currentKmPerLitre = it, priorTrips = persistedTripMileage)
                        val newTrip = PersistedTripMileage(System.currentTimeMillis(), result.distanceKm ?: 0.0, result.fuelLitres ?: 0.0, it)
                        persistedTripMileage = listOf(newTrip) + persistedTripMileage
                        val toSave = persistedTripMileage
                        // The coroutine is already cancelling at this point (we're in the
                        // CancellationException branch) — a plain launch/withContext here would
                        // be cancelled before it runs, so this save needs NonCancellable.
                        withContext(NonCancellable + Dispatchers.IO) { tripMileageStore.save(toSave) }
                        t
                    }
                    _tripMileageState.update { TripMileageState.Finished(result, signal, trend) }
                }
                throw e
            } catch (e: Exception) {
                _tripMileageState.update { TripMileageState.Error(e.message ?: "Trip mileage stopped unexpectedly.") }
            }
        }
    }

    fun stopTripMileage() {
        tripMileageJob?.cancel()
        tripMileageJob = null
    }

    /** Starts an AI Mechanic chat about one problem, so "ask about this" opens with the answer already coming. */
    fun askAiMechanicAbout(finding: DiagnosticFinding, uiContext: Context) {
        askAiMechanic(
            "My scan shows \"${finding.title}\". What does it mean for my car, is it safe to drive, and roughly what will it cost to fix?",
            uiContext
        )
    }

    /**
     * The fault cards for codes the question names, else for the scan's own faults (at most three),
     * so AI Mechanic's answer agrees with the card the owner sees. Empty if the cards can't load.
     */
    private suspend fun faultCardHints(question: String, scan: ScanResult?, brand: String?): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val asked = Regex("""\b[PBCU][0-9A-F]{4}\b""").findAll(question.uppercase()).map { it.value }.toList()
                val codes = asked.ifEmpty { scan?.findings.orEmpty().flatMap { it.relatedDtcCodes } }.distinct().take(3)
                if (codes.isEmpty()) return@runCatching emptyList()
                val cards = FaultCardRepository.load(getApplication())
                codes.mapNotNull { FaultCardRepository.find(cards, it, brand)?.toAiHint() }
            }.getOrDefault(emptyList())
        }

    /** [aboutUsedCar]: answer from the seller's car being checked (the Used Car Check), not the user's own. */
    fun askAiMechanic(question: String, uiContext: Context, aboutUsedCar: Boolean = false) {
        val scan = if (aboutUsedCar) _prePurchaseResult.value else _latestResult.value
        val ownCar = if (aboutUsedCar) null else _garage.value.active?.profile
        val usedCarLines = if (aboutUsedCar) usedCarReportLines else emptyList()
        val context = (scan?.aiContext ?: AiVehicleContext.NO_SCAN).copy(usedCarCheck = usedCarLines).let { c ->
            ownCar?.let { p ->
                c.copy(car = AiCarDetails(p.brand, p.model.ifBlank { null }, p.year, p.fuelType, p.odometerKm), vehicleBrand = p.brand ?: c.vehicleBrand)
            } ?: c
        }
        // History must be the conversation BEFORE this question, not including it — passing
        // the just-asked question as its own "history" confused the follow-up/clarification
        // resolution logic, which walks history looking for what was already discussed.
        // Bounded to MAX_CHAT_MESSAGES below, same as the stored list — a remote call further
        // caps what it actually sends (see RemoteAiProtocol.MAX_HISTORY_TURNS), but the service
        // interface always receives a chronologically-ordered list, never the current question
        // appended to it twice.
        val priorHistory = _chatMessages.value
        val askedForCar = _garage.value.active?.id
        viewModelScope.launch {
            _chatMessages.update { (it + ChatMessage(fromUser = true, text = question)).takeLast(MAX_CHAT_MESSAGES) }
            _aiMechanicLoading.value = true
            authUiContext = uiContext
            try {
                val withCards = context.copy(faultCards = faultCardHints(question, scan, if (aboutUsedCar) null else ownCar?.brand))
                val reply = aiChatService.ask(withCards, priorHistory, question)
                _chatMessages.update { (it + reply).takeLast(MAX_CHAT_MESSAGES) }
            } catch (e: CancellationException) {
                // Must always propagate — e.g. this screen was navigated away from while a
                // remote call was in flight. Swallowing this here would both break structured
                // concurrency for whoever cancelled the parent coroutine, and push a confusing
                // "couldn't get an answer" message into a conversation nobody's looking at anymore.
                throw e
            } catch (e: Exception) {
                // The rule-based service can't throw by construction, but a remote implementation
                // can (network failure, timeout, malformed response) — degrade to an honest
                // message in the conversation rather than leaving the question hanging with no
                // reply, or crashing the screen via an uncaught coroutine exception. In practice
                // FallbackAiMechanicChatService already falls back to the local service before
                // this would ever fire; this is the last line of defense if it's ever bypassed.
                _chatMessages.update {
                    (it + ChatMessage(fromUser = false, text = "Sorry, I couldn't get an answer just now. Please try again.")).takeLast(MAX_CHAT_MESSAGES)
                }
            } finally {
                authUiContext = null
                _aiMechanicLoading.value = false
                // Kept for the car it was asked about — not if the owner switched cars while waiting.
                if (!aboutUsedCar && _garage.value.active?.id == askedForCar) saveChat()
            }
        }
    }

    override fun onCleared() {
        liveJob?.cancel()
        performanceJob?.cancel()
        tripMileageJob?.cancel()
        transport?.close()
        super.onCleared()
    }

    /** A short plain name for a fault code ("Cylinder 1 misfire detected"), or null when the code isn't recognized. */
    private fun dtcTitle(code: String, rawDescription: String): String? {
        CommonDtcGlossary.lookup(code)?.let { return it.meaning }
        if (rawDescription.startsWith("Unrecognized code")) return null
        val text = if (rawDescription == rawDescription.uppercase()) rawDescription.lowercase() else rawDescription
        return text.replaceFirstChar { it.uppercase() }
    }

    companion object {
        /** Keeps the AI Mechanic chat's in-memory history from growing unbounded for the life of the ViewModel. */
        const val MAX_CHAT_MESSAGES = 40

        /**
         * Everything Live Data reads. Deliberately the agreed short list (2026-10-27 mockup) — more
         * tiles were tried and cut as clutter. MAF and barometric pressure are read but have no
         * tile: they feed mileage and turbo boost. Unsupported ones are dropped at runtime.
         */
        val LIVE_DATA_PIDS = listOf(
            PidId.RPM,
            PidId.SPEED_KMH,
            PidId.ACCELERATOR_PEDAL_POSITION_D,
            PidId.THROTTLE_POSITION,
            PidId.ENGINE_LOAD,
            PidId.MAF_AIR_FLOW_RATE,
            PidId.INTAKE_MANIFOLD_PRESSURE,
            PidId.TIMING_ADVANCE,
            PidId.COOLANT_TEMP_C,
            PidId.INTAKE_AIR_TEMP_C,
            PidId.SHORT_TERM_FUEL_TRIM_1,
            PidId.LONG_TERM_FUEL_TRIM_1,
            PidId.FUEL_LEVEL,
            PidId.CONTROL_MODULE_VOLTAGE,
            PidId.BAROMETRIC_PRESSURE,
        )

        /** Read every cycle — the values that visibly react to the accelerator, plus what instant mileage needs. */
        val LIVE_FAST_PIDS = setOf(
            PidId.RPM,
            PidId.SPEED_KMH,
            PidId.ACCELERATOR_PEDAL_POSITION_D,
            PidId.THROTTLE_POSITION,
            PidId.ENGINE_LOAD,
            PidId.MAF_AIR_FLOW_RATE,
            PidId.INTAKE_MANIFOLD_PRESSURE,
        )

        /** Slow PIDs are read on every Nth cycle only. */
        const val LIVE_SLOW_EVERY = 5

        private const val LIVE_LOOP_PAUSE_MS = 50L
    }
}
