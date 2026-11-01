package com.enginesense.app.vehiclelookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RtoRecordTest {

    private val today = LocalDate.of(2026, 11, 1)

    private fun rc(
        insurance: String? = "2027-03-14", pucc: String? = "10-Jan-2027", fitness: String? = "01/04/2034", tax: String? = "LTT",
        mode: String = "customer", financed: Boolean? = null, blacklist: String? = null, rcStatus: String? = null, owners: Int? = null
    ) = RcLookupResult(
        registrationNumber = "MH12AB1234", make = "MARUTI SUZUKI", model = "SWIFT VXI", fuelType = "PETROL", color = "WHITE",
        category = null, manufacturingMonth = "2019-03", registrationDate = "2019-04-02", registeredAt = "PUNE RTO",
        cubicCapacity = "1197.00", insuranceCompany = "ICICI Lombard", insuranceUpto = insurance, puccUpto = pucc,
        fitnessUpto = fitness, taxUpto = tax, partial = false, source = "RTO (VAHAN) records via Surepass RC V2",
        mode = mode, financed = financed, financer = "HDFC BANK", blacklistStatus = blacklist, rcStatus = rcStatus,
        ownerCount = owners, chassisNumber = "MA3EWDE1S00123456"
    )

    @Test
    fun `dates in every shape Surepass uses are read`() {
        assertEquals(LocalDate.of(2027, 3, 14), RtoRecord.parseDate("2027-03-14"))
        assertEquals(LocalDate.of(2027, 1, 10), RtoRecord.parseDate("10-Jan-2027"))
        assertEquals(LocalDate.of(2027, 1, 10), RtoRecord.parseDate("10-JAN-2027"))
        assertEquals(LocalDate.of(2034, 4, 1), RtoRecord.parseDate("01/04/2034"))
        assertEquals(LocalDate.of(2026, 10, 27), RtoRecord.parseDate("2026-10-27T15:00:00Z"))
        assertNull(RtoRecord.parseDate("sometime"))
    }

    @Test
    fun `validity is marked expired, expiring soon, lifetime or unknown against today`() {
        assertEquals(ValidityStatus.EXPIRED, RtoRecord.status("2026-10-31", today))
        assertEquals(ValidityStatus.EXPIRES_SOON, RtoRecord.status("2026-11-20", today))
        assertEquals(ValidityStatus.VALID, RtoRecord.status("2027-03-14", today))
        assertEquals(ValidityStatus.LIFETIME, RtoRecord.status("LTT", today))
        assertEquals(ValidityStatus.UNKNOWN, RtoRecord.status(null, today))
        assertEquals(ValidityStatus.UNKNOWN, RtoRecord.status("ask the RTO", today))

        val lines = RtoRecord.validity(rc(pucc = null), today)
        assertEquals(listOf("Insurance", "PUC (pollution)", "Fitness", "Road tax"), lines.map { it.label })
        assertEquals("14 Mar 2027 · ICICI Lombard", lines[0].display)
        assertEquals("Not on record", lines[1].display)
        assertEquals("Lifetime (paid once)", lines[3].display)
    }

    @Test
    fun `customer mode has no ownership rows, dealer mode masks identifiers and flags concerns`() {
        assertTrue(RtoRecord.dealerRows(rc()).isEmpty())

        val rows = RtoRecord.dealerRows(rc(mode = "dealer", financed = true, blacklist = "NA", rcStatus = "ACTIVE", owners = 3))
        val byLabel = rows.associateBy { it.label }
        assertEquals("3rd owner", byLabel.getValue("Owners so far").value)
        assertTrue(byLabel.getValue("Owners so far").concern)
        assertEquals("Yes — HDFC BANK", byLabel.getValue("Loan (hypothecation)").value)
        assertTrue(byLabel.getValue("Loan (hypothecation)").concern)
        assertFalse(byLabel.getValue("Blacklist").concern)
        assertTrue(byLabel.getValue("Chassis no. (last 5)").value.endsWith("23456"))
        assertFalse(byLabel.getValue("Chassis no. (last 5)").value.contains("MA3"))
    }

    @Test
    fun `blacklist and RC status wording`() {
        assertFalse(RtoRecord.isBlacklisted("NA"))
        assertFalse(RtoRecord.isBlacklisted("Not Blacklisted"))
        assertFalse(RtoRecord.isBlacklisted(null))
        assertTrue(RtoRecord.isBlacklisted("BLACKLISTED - THEFT"))
        assertTrue(RtoRecord.isActive("active"))
        assertFalse(RtoRecord.isActive("SUSPENDED"))
    }

    @Test
    fun `vehicle rows read like the RC, engine size in cc`() {
        val rows = RtoRecord.vehicleRows(rc()).associate { it.label to it.value }
        assertEquals("MARUTI SUZUKI SWIFT VXI", rows["Make / model"])
        assertEquals("1197 cc", rows["Engine"])
        assertEquals("Mar 2019", rows["Manufactured"])
        assertEquals("2 Apr 2019", rows["Registered on"])
    }

    @Test
    fun `masking keeps only the last five characters`() {
        assertEquals("••••••••••••23456", maskIdentifier("MA3EWDE1S00123456"))
        assertEquals("•••12345", maskIdentifier("12345"))
        assertNull(maskIdentifier("  "))
    }
}
