package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestTableTest {

    @Test
    fun runtimeWhitelistIsExactlyTheFrozenSevenRequests() {
        val actual = RequestTable.requests.map { Triple(it.header, it.command, it.timeoutMs) }
        assertEquals(
            listOf(
                Triple("7E0", "01040C0D0E10 2", 5000L),
                Triple("7E0", "21CDF3 3", 5000L),
                Triple("7E0", "01050607 1", 5000L),
                Triple("7E2", "21C3 6", 6000L),
                Triple("7E2", "21C4 5", 6000L),
                Triple("7E2", "21CF 4", 6000L),
                Triple(null, "ATRV", 4000L)
            ),
            actual
        )
        assertEquals(setOf("7E0", "7E2", null), RequestTable.requests.map { it.header }.toSet())
    }

    @Test
    fun forbiddenFamiliesCannotEnterTheFixedTable() {
        RequestTable.requests.forEach { request ->
            val command = request.command.uppercase()
            assertFalse(command.startsWith("22"))
            assertFalse(command.startsWith("2C"))
            assertFalse(command == "10 02" || command == "10 03")
            assertFalse(request.header in setOf("7E1", "7E3", "7E4"))
            assertTrue(request.targetPeriodMs > 0)
        }
    }

    @Test
    fun d046KeepsFrozenPeriodsAndUsesPromptDelimitedRuntimeTransactions() {
        assertEquals(
            listOf(800L, 1000L, 3000L, 800L, 1500L, 5000L, 3000L),
            RequestTable.requests.map { it.targetPeriodMs }
        )
        assertEquals(
            listOf(0L, 0L, 1500L, 0L, 0L, 2500L, 1500L),
            RequestTable.requests.map { it.phaseMs }
        )
        assertEquals(
            listOf(
                RequestPriority.FAST,
                RequestPriority.FAST,
                RequestPriority.SLOW,
                RequestPriority.FAST,
                RequestPriority.MEDIUM,
                RequestPriority.SLOW,
                RequestPriority.ADAPTER
            ),
            RequestTable.requests.map { it.priority }
        )
        RequestTable.requests.forEach { request ->
            assertEquals(request.targetPeriodMs, request.deadlineMs)
            assertTrue(request.phaseMs in 0L until request.targetPeriodMs)
            assertEquals(0L, request.minimumGapMs)
            assertEquals(0L, request.preDrainMs)
            assertEquals(0L, request.quietWindowMs)
        }
    }

    @Test
    fun d051PinsTrustedApi27DirectionalCostEvidence() {
        val model = RequestTable.trustedCostModel
        assertEquals(
            "api27_sp7731e_obdlink_v030_capacity_002_p95_v1",
            model.modelId
        )
        assertEquals(
            mapOf(
                "std_core" to Triple(141L, 5_631, true),
                "cd_f3" to Triple(139L, 4_503, true),
                "coolant" to Triple(139L, 1_501, true),
                "c3" to Triple(162L, 5_630, true),
                "c4" to Triple(169L, 3_000, true),
                "cf" to Triple(151L, 900, true),
                "atrv" to Triple(81L, 1_498, true)
            ),
            model.requestCosts.mapValues { (_, cost) ->
                Triple(cost.p95Ms, cost.sampleCount, cost.trusted)
            }
        )
        assertEquals(154L, model.headerSetupMs(null, "7E0"))
        assertEquals(154L, model.headerSetupMs(null, "7E2"))
        assertEquals(65L, model.headerSetupMs("7E0", "7E2"))
        assertEquals(116L, model.headerSetupMs("7E2", "7E0"))
        assertEquals(SchedulerCostEstimate(116L, 8_998, true), model.headerSetupCost)
        assertEquals(
            SchedulerCostEstimate(65L, 4_500, true),
            model.periodicHeaderCosts.getValue(SchedulerHeaderTransition("7E0", "7E2"))
        )
        assertEquals(
            SchedulerCostEstimate(116L, 4_498, true),
            model.periodicHeaderCosts.getValue(SchedulerHeaderTransition("7E2", "7E0"))
        )
        assertEquals(
            SchedulerCostEstimate(154L, 2, false),
            model.coldStartHeaderCosts.getValue("7E0")
        )
        assertEquals(
            SchedulerCostEstimate(154L, 0, false),
            model.coldStartHeaderCosts.getValue("7E2")
        )
        assertEquals(true, model.isTrustedFor(RequestTable.schedulerSpecs, 20))

        listOf(
            "0dc6b6a71f40365b18febe6a815eeb13f2a6bb32ee0a4dcb6237091421a245f7",
            "d304e9dfabb1f1d4a28eda9b9c0fc674c04276ce119e8ea5a94e8cdf783432d7",
            "8e55c6afae20ca64b9ea9bba5861bc85d8017c62",
            "841b1a4adb9f9e4a1834d2830dd6e94754a54cbcc3b2b3209023061da1969e9b"
        ).forEach { pinnedId -> assertTrue(model.sourceEvidenceId.contains(pinnedId)) }
    }

    @Test
    fun d051ExactProductionReplayIsAdmittedWithPositiveHeadroom() {
        val report = CapacityAdmission.assess(
            RequestTable.schedulerSpecs,
            RequestTable.trustedCostModel,
            horizonMs = 60_000L
        )

        assertEquals(AdmissionState.ADMITTED, report.state)
        assertEquals("ZERO_MISS_PRODUCTION_POLICY_REPLAY", report.reason)
        assertEquals(0L, report.projectedDeadlineMisses)
        assertEquals(0L, report.projectedCapacityRejections)
        assertTrue(requireNotNull(report.projectedUtilization) > 0.0)
        assertTrue(requireNotNull(report.projectedUtilization) < 1.0)
        assertEquals(0.9065333333333333, requireNotNull(report.projectedUtilization), 1e-12)
    }

    @Test
    fun d051SelectorAdmitsOnlyThePinnedApi27HardwareContext() {
        val exact = SchedulerCostApplicabilityContext(
            apiLevel = 27,
            manufacturer = "sprd",
            model = "sp7731e_1h10_native",
            device = "sp7731e_1h10",
            adapterName = "OBDLink MX+ 99905"
        )
        assertTrue(RequestTable.trustedCostModelApplies(exact))
        assertSame(RequestTable.trustedCostModel, RequestTable.schedulerCostModelFor(exact))
        assertEquals(
            AdmissionState.ADMITTED,
            CapacityAdmission.assess(
                RequestTable.schedulerSpecs,
                RequestTable.schedulerCostModelFor(exact)
            ).state
        )

        // A different serial suffix is still the exact, case-sensitive MX+
        // family; lookalike adapter names and any head-unit mismatch are not.
        assertTrue(
            RequestTable.trustedCostModelApplies(
                exact.copy(adapterName = "OBDLink MX+ A1234")
            )
        )
        val mismatches = listOf(
            exact.copy(apiLevel = 26),
            exact.copy(apiLevel = 28),
            exact.copy(manufacturer = "SPRD"),
            exact.copy(model = "sp7731e_1h10"),
            exact.copy(device = "sp7731e_1h10_native"),
            exact.copy(adapterName = null),
            exact.copy(adapterName = "obdlink mx+ 99905"),
            exact.copy(adapterName = "OBDLink MX 99905"),
            exact.copy(adapterName = "OBDLink LX 99905"),
            exact.copy(adapterName = " OBDLink MX+ 99905"),
            exact.copy(adapterName = "OBDLink MX+ 99905 ")
        )
        mismatches.forEach { context ->
            assertFalse(RequestTable.trustedCostModelApplies(context))
            val selected = RequestTable.schedulerCostModelFor(context)
            assertSame(RequestTable.diagnosticCostModel, selected)
            assertEquals(
                AdmissionState.UNKNOWN,
                CapacityAdmission.assess(RequestTable.schedulerSpecs, selected).state
            )
        }
    }
}
