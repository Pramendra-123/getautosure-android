// Tests for the pure logic in index.ts — request parsing/plate validation, Surepass response
// checks, and per-mode field filtering (the part that decides whose personal data reaches which
// phone, so it's the part most worth pinning down). Run with `deno test` from this directory.
//
// Not covered, same reasoning as ai-mechanic/index.test.ts: Google token verification, the rate
// limit RPC, the cache and the Surepass HTTP call all need real infrastructure to test honestly.

import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import {
  isUsableSurepassResponse,
  isValidIndianRegistration,
  normalizeRegistrationNumber,
  parseLookupRequest,
  shapeRcData,
  surepassOutcome,
} from "./index.ts";

// Field names copied from a real Surepass RC V2 sandbox response (2026-10-27); every VALUE is
// made up, so no real vehicle's or person's data lives in the repo.
const FIXTURE = {
  rc_number: "XX00ZZ0000",
  registration_date: "2015-04-01",
  fit_up_to: "2030-03-31",
  owner_name: "TEST OWNER",
  father_name: "TEST FATHER",
  present_address: "TEST ADDRESS 1",
  permanent_address: "TEST ADDRESS 2",
  mobile_number: "",
  vehicle_category: "LMV",
  vehicle_chasi_number: "TESTCHASSIS0001",
  vehicle_engine_number: "TESTENGINE0001",
  maker_description: "TEST MOTORS LTD",
  maker_model: "TESTMODEL VX",
  body_type: "SALOON",
  fuel_type: "PETROL",
  color: "WHITE",
  norms_type: "Not Available",
  financer: "TEST BANK",
  financed: true,
  insurance_company: "Test Insurance Co",
  insurance_policy_number: "POLICY000",
  insurance_upto: "2027-01-15",
  manufacturing_date: "03/2015",
  manufacturing_date_formatted: "2015-03",
  registered_at: "TESTCITY, Teststate",
  less_info: false,
  tax_upto: "2099-12-31",
  pucc_number: "",
  pucc_upto: "",
  blacklist_status: "",
  noc_details: "",
  non_use_status: "",
  owner_number: "2",
  rc_status: "ACTIVE",
};

// --- normalizeRegistrationNumber / isValidIndianRegistration ---

Deno.test("plates are normalized the way people actually type them", () => {
  assertEquals(normalizeRegistrationNumber("mh-12 ab 1234"), "MH12AB1234");
  assertEquals(normalizeRegistrationNumber(" ka.01.ab.1234 "), "KA01AB1234");
});

Deno.test("standard and BH-series plates are accepted", () => {
  for (const plate of ["MH12AB1234", "DL3CAB1234", "KA011234", "GJ1A1", "22BH1234AA", "21BH0001A"]) {
    assertEquals(isValidIndianRegistration(plate), true, plate);
  }
});

Deno.test("non-plates are rejected before any paid call", () => {
  for (const plate of ["TEST0000", "1234", "MH", "MH12ABCD1234", "MH12AB12345", "22BH12AA", "<script>"]) {
    assertEquals(isValidIndianRegistration(plate), false, plate);
  }
});

// --- parseLookupRequest ---

Deno.test("mode defaults to customer", () => {
  assertEquals(parseLookupRequest({ registrationNumber: "mh12ab1234" }), {
    ok: true,
    registrationNumber: "MH12AB1234",
    mode: "customer",
  });
});

Deno.test("dealer mode is passed through (access is checked separately, server-side)", () => {
  const result = parseLookupRequest({ registrationNumber: "MH12AB1234", mode: "dealer" });
  assertEquals(result.ok && result.mode, "dealer");
});

Deno.test("unknown modes, missing and invalid plates are rejected", () => {
  assertEquals(parseLookupRequest({ registrationNumber: "MH12AB1234", mode: "admin" }).ok, false);
  assertEquals(parseLookupRequest({}).ok, false);
  assertEquals(parseLookupRequest(null).ok, false);
  const invalid = parseLookupRequest({ registrationNumber: "TEST0000" });
  assertEquals(invalid.ok === false && invalid.code, "invalid_registration");
});

// --- isUsableSurepassResponse ---

Deno.test("only success:true with a data object counts as a result", () => {
  assertEquals(isUsableSurepassResponse({ success: true, data: FIXTURE }), true);
  assertEquals(isUsableSurepassResponse({ success: false, data: FIXTURE }), false);
  assertEquals(isUsableSurepassResponse({ success: true, data: null }), false);
  assertEquals(isUsableSurepassResponse({ success: true }), false);
  assertEquals(isUsableSurepassResponse(null), false);
});

// --- shapeRcData ---

const PERSONAL_VALUES = [
  "TEST OWNER", "TEST FATHER", "TEST ADDRESS 1", "TEST ADDRESS 2",
  "TESTCHASSIS0001", "TESTENGINE0001", "TEST BANK", "POLICY000",
];

Deno.test("customer mode carries no owner identity, loan or vehicle identifiers", () => {
  const serialized = JSON.stringify(shapeRcData(FIXTURE, "customer"));
  for (const value of PERSONAL_VALUES) {
    assertEquals(serialized.includes(value), false, `customer response leaked ${value}`);
  }
  for (const key of ["ownership", "compliance", "identifiers"]) {
    assertEquals(key in shapeRcData(FIXTURE, "customer"), false, key);
  }
});

Deno.test("customer mode has the prefill and reminder fields", () => {
  const shaped = shapeRcData(FIXTURE, "customer");
  assertEquals(shaped.vehicle.make, "TEST MOTORS LTD");
  assertEquals(shaped.vehicle.model, "TESTMODEL VX");
  assertEquals(shaped.vehicle.fuelType, "PETROL");
  assertEquals(shaped.vehicle.manufacturingMonth, "2015-03");
  assertEquals(shapeRcData({ ...FIXTURE, cubic_capacity: "999.00" }, "customer").vehicle.cubicCapacity, "999.00");
  assertEquals(shaped.validity.insuranceUpto, "2027-01-15");
  assertEquals(shaped.validity.fitnessUpto, "2030-03-31");
});

Deno.test("dealer mode adds ownership, compliance and identifiers", () => {
  // deno-lint-ignore no-explicit-any
  const shaped = shapeRcData(FIXTURE, "dealer") as any;
  assertEquals(shaped.ownership, { ownerName: "TEST OWNER", ownerCount: 2, financed: true, financer: "TEST BANK" });
  assertEquals(shaped.compliance.rcStatus, "ACTIVE");
  assertEquals(shaped.identifiers.chassisNumber, "TESTCHASSIS0001");
});

Deno.test("dealer mode is a superset of customer mode", () => {
  const customer = shapeRcData(FIXTURE, "customer");
  // deno-lint-ignore no-explicit-any
  const dealer = shapeRcData(FIXTURE, "dealer") as any;
  assertEquals(dealer.vehicle, customer.vehicle);
  assertEquals(dealer.validity, customer.validity);
});

Deno.test("dealer mode carries the technical, registration and permit extras", () => {
  // deno-lint-ignore no-explicit-any
  const dealer = shapeRcData(
    { ...FIXTURE, seat_capacity: "5", unladen_weight: "1050", vehicle_gross_weight: "0", permit_type: "TEST PERMIT" },
    "dealer",
  ) as any;
  assertEquals(dealer.technical.seatCapacity, 5);
  assertEquals(dealer.technical.unladenWeightKg, 1050);
  assertEquals(dealer.technical.grossWeightKg, null); // Surepass's 0 means "not recorded"
  assertEquals(dealer.technical.emissionNorms, null); // "Not Available"
  assertEquals(dealer.permit.type, "TEST PERMIT");
});

Deno.test("father's name, addresses, mobile and policy number never leave the server in any mode", () => {
  const serialized = JSON.stringify(shapeRcData(FIXTURE, "dealer"));
  for (const value of ["TEST FATHER", "TEST ADDRESS 1", "TEST ADDRESS 2", "POLICY000"]) {
    assertEquals(serialized.includes(value), false, `dealer response leaked ${value}`);
  }
});

Deno.test('empty strings and "Not Available" become null, not blanks', () => {
  // deno-lint-ignore no-explicit-any
  const shaped = shapeRcData(FIXTURE, "dealer") as any;
  assertEquals(shaped.validity.puccUpto, null);
  assertEquals(shaped.compliance.blacklistStatus, null);
});

Deno.test("fields Surepass doesn't send come back null rather than throwing", () => {
  const shaped = shapeRcData({}, "customer");
  assertEquals(shaped.vehicle.make, null);
  assertEquals(shaped.validity.insuranceUpto, null);
});

Deno.test("Surepass error logging keeps the reason fields and drops the VAHAN record", () => {
  const body = JSON.stringify({
    data: { rc_number: "KA02MP3115", owner_name: "SOMEONE" },
    status_code: 500,
    success: false,
    message: "Source unavailable",
    message_code: "source_down",
  });
  assertEquals(surepassOutcome(body), {
    status_code: 500,
    success: false,
    message: "Source unavailable",
    message_code: "source_down",
  });
  assertEquals(surepassOutcome("<html>Bad Gateway</html>"), { unparseable_body_length: 24 });
});
