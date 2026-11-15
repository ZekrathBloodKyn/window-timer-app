package dev.wolsey.windowtimer

import dev.wolsey.windowtimer.data.Breakdown
import dev.wolsey.windowtimer.data.Crew
import dev.wolsey.windowtimer.data.CrewMember
import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.data.TypeCount
import dev.wolsey.windowtimer.data.TypePace
import dev.wolsey.windowtimer.data.TypePaces
import dev.wolsey.windowtimer.data.db.Run
import dev.wolsey.windowtimer.data.db.Visit
import dev.wolsey.windowtimer.data.db.WindowType
import dev.wolsey.windowtimer.data.paceFor
import dev.wolsey.windowtimer.data.routing.RouteOptimizer
import dev.wolsey.windowtimer.data.routing.parseCoordinates
import dev.wolsey.windowtimer.data.summarize
import dev.wolsey.windowtimer.timer.Availability
import dev.wolsey.windowtimer.timer.AvailabilityCodec
import dev.wolsey.windowtimer.timer.IntervalPlan
import dev.wolsey.windowtimer.timer.buildMarks
import dev.wolsey.windowtimer.timer.formatClock
import dev.wolsey.windowtimer.timer.formatCountdown
import dev.wolsey.windowtimer.timer.interiorMillis
import dev.wolsey.windowtimer.timer.markMillis
import dev.wolsey.windowtimer.timer.nextStopwatchAlert
import dev.wolsey.windowtimer.timer.soundStage
import dev.wolsey.windowtimer.timer.totalMillis
import dev.wolsey.windowtimer.ui.history.TimeFramePreset
import dev.wolsey.windowtimer.ui.history.timeRangeFor
import junit.framework.TestCase.assertEquals
import org.junit.Assert
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

class PlanAndBreakdownTest {

    private val standard = WindowType(
        name = "Standard", interiorPace = 24, exteriorPace = 22, isStandard = true, id = 1
    )
    private val french = WindowType(
        name = "French", interiorPace = 12, exteriorPace = 11, id = 2
    )
    private val types = listOf(standard, french)

    // ---- Estimate ----------------------------------------------------------

    @Test
    fun standardInteriorWindowsTakeOneHourPer24() {
        val counts = listOf(TypeCount(1, "Standard", interior = 24, exterior = 0))
        Assert.assertEquals(3_600_000L, totalMillis(counts, types))
    }

    @Test
    fun typesAreAddedAtTheirOwnPace() {
        val counts = listOf(
            TypeCount(1, "Standard", interior = 24, exterior = 0),  // 1 hour
            TypeCount(2, "French", interior = 0, exterior = 11)     // 1 hour
        )
        Assert.assertEquals(7_200_000L, totalMillis(counts, types))
    }

    @Test
    fun deletedTypeIsTimedAtTheStandardPace() {
        val counts = listOf(TypeCount(99, "Gone", interior = 22, exterior = 0))
        Assert.assertEquals(3_300_000L, totalMillis(counts, types))   // 22 / 24 hour
    }

    @Test
    fun noWindowsMeansNoTime() {
        Assert.assertEquals(0L, totalMillis(emptyList(), types))
    }

    // ---- Quarter marks and clocks -----------------------------------------

    @Test
    fun quarterMarksAreWholeSeconds() {
        Assert.assertEquals(900_000L, markMillis(3_600_000L, 0.25))
        Assert.assertEquals(250_000L, markMillis(1_001_000L, 0.25))   // 250.25 s rounds to 250 s
    }

    @Test
    fun formatClockShowsMinutesThenHours() {
        Assert.assertEquals("1:05", formatClock(65_000))
        Assert.assertEquals("1:01:01", formatClock(3_661_000))
        Assert.assertEquals("0:00", formatClock(-5_000))
    }

    @Test
    fun formatCountdownRoundsUp() {
        Assert.assertEquals("0:01", formatCountdown(1))
        Assert.assertEquals("0:00", formatCountdown(0))
        Assert.assertEquals("1:01", formatCountdown(61_000))
    }

    // ---- Breakdown ---------------------------------------------------------

    @Test
    fun breakdownSurvivesEncodeAndDecode() {
        val counts = listOf(
            TypeCount(1, "Standard", 12, 10),
            TypeCount(2, "French", 3, 0)
        )
        Assert.assertEquals(counts, Breakdown.decode(Breakdown.encode(counts)))
    }

    @Test
    fun badOrEmptyBreakdownDecodesToNothing() {
        Assert.assertTrue(Breakdown.decode("").isEmpty())
        Assert.assertTrue(Breakdown.decode("not json").isEmpty())
    }

    @Test
    fun olderJobsWithoutBreakdownCountAsStandard() {
        val resolved = Breakdown.resolve("", interior = 3, exterior = 2, standard = standard)
        Assert.assertEquals(listOf(TypeCount(1, "Standard", 3, 2)), resolved)
    }

    @Test
    fun jobWithNoWindowsResolvesToNoRows() {
        Assert.assertTrue(Breakdown.resolve("", 0, 0, standard).isEmpty())
    }

    @Test
    fun totalsAddUpAcrossTypes() {
        val counts = listOf(TypeCount(1, "Standard", 12, 10), TypeCount(2, "French", 3, 4))
        Assert.assertEquals(15, Breakdown.totalInterior(counts))
        Assert.assertEquals(14, Breakdown.totalExterior(counts))
    }

    // ---- Crew estimate ------------------------------------------------------

    private val me = Person(name = "Me", interiorPace = 24, exteriorPace = 22, isOwner = true, id = 1)
    private val sam = Person(name = "Sam", interiorPace = 18, exteriorPace = 16, id = 2)

    @Test
    fun soloAtStandardPaceMatchesThePlainEstimate() {
        val counts = listOf(TypeCount(1, "Standard", interior = 24, exterior = 0))
        assertEquals(3_600_000L, totalMillis(counts, types, listOf(me)))
    }

    @Test
    fun twoPeopleShareTheWork() {
        // 24 windows at 24 + 18 = 42 per hour = 0.5714 h, which is 2057 s
        val counts = listOf(TypeCount(1, "Standard", interior = 24, exterior = 0))
        assertEquals(2_057_000L, totalMillis(counts, types, listOf(me, sam)))
    }

    @Test
    fun otherTypesScaleFromStandard() {
        // French is half the Standard pace, so the crew does 42 * 0.5 = 21 per hour
        val counts = listOf(TypeCount(2, "French", interior = 21, exterior = 0))
        assertEquals(3_600_000L, totalMillis(counts, types, listOf(me, sam)))
    }

    @Test
    fun aFasterPersonSpeedsUpTheJob() {
        val fast = me.copy(interiorPace = 48)
        val counts = listOf(TypeCount(1, "Standard", interior = 24, exterior = 0))
        assertEquals(1_800_000L, totalMillis(counts, types, listOf(fast)))
    }

    @Test
    fun emptyCrewFallsBackToTypePaces() {
        val counts = listOf(TypeCount(1, "Standard", interior = 24, exterior = 0))
        assertEquals(3_600_000L, totalMillis(counts, types, emptyList()))
    }

    @Test
    fun splitFollowsPaceAndKeepsTheTotals() {
        val planned = listOf(TypeCount(1, "Standard", interior = 24, exterior = 10))
        val split = Crew.split(planned, listOf(me, sam))
        // 24 at 24:18 is 13.7 and 10.3, so the leftover goes to the larger fraction
        assertEquals(listOf(14, 10), split.map { it[0].interior })
        assertEquals(10, split.sumOf { it[0].exterior })
    }

    @Test
    fun crewSurvivesEncodeAndDecode() {
        val members = listOf(
            CrewMember(1, "Me", 24, 22, listOf(TypeCount(1, "Standard", 14, 6))),
            CrewMember(2, "Sam", 18, 16, listOf(TypeCount(1, "Standard", 10, 4)))
        )
        assertEquals(members, Crew.decode(Crew.encode(members)))
    }

    @Test
    fun crewTotalsAddUpByType() {
        val members = listOf(
            CrewMember(1, "Me", 24, 22, listOf(TypeCount(1, "Standard", 14, 6))),
            CrewMember(
                2, "Sam", 18, 16,
                listOf(TypeCount(1, "Standard", 10, 4), TypeCount(2, "French", 3, 0))
            )
        )
        assertEquals(
            listOf(TypeCount(1, "Standard", 24, 10), TypeCount(2, "French", 3, 0)),
            Crew.totals(members)
        )
    }

    @Test
    fun personPaceUsesSavedPaceElseScalesFromStandard() {
        val standard = WindowType("Standard", 24, 22, true, 1)
        val french = WindowType("French", 12, 11, false, 2)
        val sam = Person("Sam", 18, 16, false, 2)

        // Nothing saved: 18 * 12/24 = 9
        assertEquals(9.0, sam.paceFor(french, true, standard), 0.001)

        // Interior saved at 10; exterior still scales: 16 * 11/22 = 8
        val custom = sam.copy(typePaces = TypePaces.encode(mapOf(2L to TypePace(10, null))))
        assertEquals(10.0, custom.paceFor(french, true, standard), 0.001)
        assertEquals(8.0, custom.paceFor(french, false, standard), 0.001)
        // Standard always uses their own Standard pace
        assertEquals(18.0, custom.paceFor(standard, true, standard), 0.001)
    }

    @Test
    fun estimateUsesAPersonsSavedTypePace() {
        val standard = WindowType("Standard", 24, 22, true, 1)
        val french = WindowType("French", 12, 11, false, 2)
        val sam = Person("Sam", 18, 16, false, 2).copy(typePaces = TypePaces.encode(mapOf(2L to TypePace(10, null))))
        // 12 interior French at 10/hr is 1.2 hours
        assertEquals(
            4_320_000L,
            totalMillis(listOf(TypeCount(2, "French", 12, 0)), listOf(standard, french), listOf(sam))
        )
    }

    private fun crewRun(): Run {
        val members = listOf(
            CrewMember(1, "Me", 24, 22, listOf(TypeCount(1, "Standard", 14, 0))),
            CrewMember(2, "Sam", 18, 16, listOf(TypeCount(1, "Standard", 10, 0)))
        )
        return Run(
            jobName = "A", startedAt = 0L, plannedMs = 3_600_000L, actualMs = 3_600_000L,
            interior = 24, exterior = 0, crew = Crew.encode(members)
        )
    }

    private fun soloRun() = Run(
        jobName = "B", startedAt = 0L, plannedMs = 3_600_000L, actualMs = 3_600_000L,
        interior = 12, exterior = 0
    )

    @Test
    fun summaryForEveryoneUsesCrewAndPerPersonPace() {
        val s = summarize(listOf(crewRun(), soloRun()), null, ownerId = 1)!!
        assertEquals(2, s.count)
        assertEquals(18.0, s.overallPace!!, 0.001)     // 36 windows in 2 h
        assertEquals(12.0, s.perPersonPace!!, 0.001)   // 3 person-hours
    }

    @Test
    fun summaryForOnePersonOnlyCountsTheirRunsAndWindows() {
        val sam = summarize(listOf(crewRun(), soloRun()), 2L, ownerId = 1)!!
        assertEquals(1, sam.count)
        assertEquals(10.0, sam.overallPace!!, 0.001)

        // The solo run has no crew record, so it counts as the owner's.
        val me = summarize(listOf(crewRun(), soloRun()), 1L, ownerId = 1)!!
        assertEquals(2, me.count)
        assertEquals(13.0, me.overallPace!!, 0.001)    // 26 windows in 2 h
    }

    @Suppress("SameParameterValue")
    private fun day(y: Int, m: Int, d: Int) =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun timeRangesCoverTheRightDays() {
        val now = day(2026, 11, 10) + 12 * 3_600_000L   // Tuesday noon
        fun range(p: TimeFramePreset, first: DayOfWeek = DayOfWeek.SUNDAY) =
            timeRangeFor(p, now, now, now, ZoneOffset.UTC, first)!!

        assertEquals(null, timeRangeFor(TimeFramePreset.ALL, now, now, now, ZoneOffset.UTC))
        assertEquals(day(2026, 11, 10), range(TimeFramePreset.TODAY).startMillis)
        assertEquals(day(2026, 11, 11), range(TimeFramePreset.TODAY).endMillis)
        assertEquals(day(2026, 11, 8), range(TimeFramePreset.WEEK).startMillis)
        assertEquals(day(2026, 11, 9), range(TimeFramePreset.WEEK, DayOfWeek.MONDAY).startMillis)
        assertEquals(day(2026, 11, 1), range(TimeFramePreset.MONTH).startMillis)
        assertEquals(day(2026, 12, 1), range(TimeFramePreset.MONTH).endMillis)
        assertEquals(day(2026, 10, 12), range(TimeFramePreset.LAST_30).startMillis)
        assertEquals(day(2026, 1, 1), range(TimeFramePreset.YEAR).startMillis)
    }

    @Test
    fun customRangeIncludesBothEndDaysInEitherOrder() {
        val a = day(2026, 11, 3)
        val b = day(2026, 11, 5)
        val r = timeRangeFor(TimeFramePreset.CUSTOM, b, a, a, ZoneOffset.UTC)!!
        assertEquals(a, r.startMillis)
        assertEquals(day(2026, 11, 6), r.endMillis)
        assertEquals(true, (b + 5 * 3_600_000L) in r)
        assertEquals(false, day(2026, 11, 6) in r)
    }

    /** A shop at 0 and stops on a line at the given positions (stop 1 is positions[0], ...). */
    private fun lineMatrix(vararg positions: Double): Array<DoubleArray> {
        val at = doubleArrayOf(0.0) + positions
        return Array(at.size) { i -> DoubleArray(at.size) { j -> abs(at[i] - at[j]) } }
    }

    @Test
    fun optimizerVisitsStopsInTheShortestOrder() {
        val d = lineMatrix(3.0, 1.0, 2.0)          // stop 1 at 3, stop 2 at 1, stop 3 at 2
        assertEquals(listOf(2, 3, 1), RouteOptimizer.order(d, returnToStart = false))
        assertEquals(listOf(2, 3, 1), RouteOptimizer.order(d, returnToStart = true))
        assertEquals(3.0, RouteOptimizer.cost(d, listOf(2, 3, 1), false), 0.001)
        assertEquals(6.0, RouteOptimizer.cost(d, listOf(2, 3, 1), true), 0.001)
    }

    @Test
    fun optimizerHandlesNoStopsAndOneStop() {
        assertEquals(emptyList<Int>(), RouteOptimizer.order(arrayOf(doubleArrayOf(0.0)), true))
        assertEquals(listOf(1), RouteOptimizer.order(lineMatrix(4.0), true))
    }

    @Test
    fun optimizerStillFindsAGoodRouteWithManyStops() {
        // 14 stops is past the exact solver, so this exercises the approximate one.
        val positions = (1..14).map { it.toDouble() }.shuffled(java.util.Random(7)).toDoubleArray()
        val d = lineMatrix(*positions)
        val order = RouteOptimizer.order(d, returnToStart = true)
        assertEquals(14, order.toSet().size)
        assertEquals(28.0, RouteOptimizer.cost(d, order, true), 0.001)
    }

    @Test
    fun coordinatesAreReadFromTheFormatMapsCopies() {
        val point = parseCoordinates("40.1149, -111.6549")!!
        assertEquals(40.1149, point.lat, 0.00001)
        assertEquals(-111.6549, point.lng, 0.00001)

        val spaced = parseCoordinates("(40.1149 -111.6549)")!!
        assertEquals(-111.6549, spaced.lng, 0.00001)
    }

    @Test
    fun badCoordinatesAreRejected() {
        assertEquals(null, parseCoordinates("95, 10"))       // latitude out of range
        assertEquals(null, parseCoordinates("40, 200"))      // longitude out of range
        assertEquals(null, parseCoordinates("Spanish Fork"))
    }

    @Test
    fun onSitePaceUsesVisitTimeAndCountsEachVisitOnce() {
        val twoHours = 2 * 3_600_000L
        val visit = Visit("A", arrivedAt = 0L, departedAt = twoHours, id = 5)
        val run = crewRun().copy(visitId = 5)

        // 24 windows over 2 hours on site
        val one = summarize(listOf(run), null, 1, mapOf(5L to visit))!!
        assertEquals(12.0, one.visitPace!!, 0.001)

        // Two runs in the same visit: 48 windows, and the 2 hours count once
        val two = summarize(listOf(run, run.copy(id = 2)), null, 1, mapOf(5L to visit))!!
        assertEquals(24.0, two.visitPace!!, 0.001)

        // One person's share: Sam did 10 of the 24
        val sam = summarize(listOf(run), 2L, 1, mapOf(5L to visit))!!
        assertEquals(5.0, sam.visitPace!!, 0.001)
    }

    @Test
    fun onSitePaceIsMissingWithoutAFinishedVisit() {
        val open = Visit("A", arrivedAt = 0L, departedAt = null, id = 5)
        val run = crewRun().copy(visitId = 5)
        assertEquals(null, summarize(listOf(run), null, 1, mapOf(5L to open))!!.visitPace)
        assertEquals(null, summarize(listOf(run), null, 1)!!.visitPace)
    }

    private val hour = 3_600_000L

    @Test
    fun defaultIntervalsAreTheQuarters() {
        val marks = buildMarks(hour, 0L, IntervalPlan())
        assertEquals(listOf(900_000L, 1_800_000L, 2_700_000L, hour), marks.map { it.timeMs })
        assertEquals(listOf("25%", "50%", "75%", "100%"), marks.map { it.label })
        assertEquals(listOf(0, 1, 2, 3), marks.map { it.sound })
    }

    @Test
    fun soundStagesSpreadAcrossEarlyMiddleAndLate() {
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 2, 2, 2, 3), (1..10).map { soundStage(it, 10) })
        assertEquals(listOf(3), (1..1).map { soundStage(it, 1) })
        assertEquals(listOf(0, 3), (1..2).map { soundStage(it, 2) })
    }

    @Test
    fun anyNumberOfIntervalsIsEvenlySpaced() {
        val marks = buildMarks(hour, 0L, IntervalPlan(count = 6))
        assertEquals(6, marks.size)
        assertEquals(hour, marks.last().timeMs)
        assertEquals(600_000L, marks[0].timeMs)
        assertEquals(1_200_000L, marks[1].timeMs)
    }

    @Test
    fun separateGroupsRunOneAfterTheOther() {
        val plan = IntervalPlan(separate = true, interiorCount = 2, exteriorCount = 4)
        val marks = buildMarks(3 * hour, hour, plan)   // 1 h interior, 2 h exterior

        assertEquals(
            listOf("Interior 50%", "Interior 100%", "Exterior 25%", "Exterior 50%", "Exterior 75%", "Exterior 100%"),
            marks.map { it.label }
        )
        assertEquals(
            listOf(1_800_000L, hour, hour + 1_800_000L, 2 * hour, 2 * hour + 1_800_000L, 3 * hour),
            marks.map { it.timeMs }
        )
        assertEquals(4, marks[1].sound)          // end of the first part: "part complete"
        assertEquals(3, marks.last().sound)      // end of the job: final
    }

    @Test
    fun exteriorCanGoFirst() {
        val plan = IntervalPlan(separate = true, interiorCount = 1, exteriorCount = 1, interiorFirst = false)
        val marks = buildMarks(3 * hour, hour, plan)
        assertEquals(listOf("Exterior 100%", "Interior 100%"), marks.map { it.label })
        assertEquals(listOf(2 * hour, 3 * hour), marks.map { it.timeMs })
    }

    @Test
    fun separateWithOneKindUsesThatKindsCountAndNoPrefix() {
        val plan = IntervalPlan(count = 4, separate = true, interiorCount = 2, exteriorCount = 5)
        val exteriorOnly = buildMarks(hour, 0L, plan)
        assertEquals(5, exteriorOnly.size)
        assertEquals("20%", exteriorOnly.first().label)
        val interiorOnly = buildMarks(hour, hour, plan)
        assertEquals(2, interiorOnly.size)
    }

    @Test
    fun interiorMillisSplitsTheEstimateAndAddsBackUp() {
        val standard = WindowType("Standard", 24, 22, true, 1)
        val types = listOf(standard)

        val both = listOf(TypeCount(1, "Standard", 24, 22))
        assertEquals(hour, interiorMillis(both, types, emptyList()))
        assertEquals(2 * hour, totalMillis(both, types, emptyList()))

        assertEquals(0L, interiorMillis(listOf(TypeCount(1, "Standard", 0, 22)), types, emptyList()))
        val interiorOnly = listOf(TypeCount(1, "Standard", 12, 0))
        assertEquals(totalMillis(interiorOnly, types, emptyList()), interiorMillis(interiorOnly, types, emptyList()))
    }

    @Test
    fun onlyTheFirstPartGetsThePartCompleteSound() {
        val plan = IntervalPlan(separate = true, interiorCount = 3, exteriorCount = 3)
        val marks = buildMarks(2 * hour, hour, plan)
        assertEquals(listOf(0, 1, 4, 0, 1, 3), marks.map { it.sound })

        // One kind only: that's the end of the job, so no part complete sound.
        val single = buildMarks(hour, hour, plan)
        assertEquals(3, single.last().sound)
    }

    @Test
    fun stopwatchRunsStayOutOfEstimateAveragesButCountForPace() {
        val timed = soloRun()                                          // 1 h estimate, 1 h actual, 12 windows
        val stopwatch = soloRun().copy(plannedMs = 0L, actualMs = 2 * hour)

        val s = summarize(listOf(timed, stopwatch))!!
        assertEquals(2, s.count)
        assertEquals(1, s.fullRunCount)
        assertEquals(hour, s.avgPlannedMs)
        assertEquals(0L, s.avgDiffMs)
        assertEquals(8.0, s.overallPace!!, 0.001)    // 24 windows over 3 hours
    }

    @Test
    fun stopwatchAlertsLandOnEveryMultipleOfTheInterval() {
        val every = 15 * 60_000L
        assertEquals(every, nextStopwatchAlert(0L, every))
        assertEquals(every, nextStopwatchAlert(every - 1, every))
        // Right on an alert (or just after), the next one is a full interval away.
        assertEquals(2 * every, nextStopwatchAlert(every, every))
        assertEquals(2 * every, nextStopwatchAlert(every + 200, every))
        assertEquals(5 * every, nextStopwatchAlert(4 * every + 1, every))
    }

    private val pairA = Person("A", 24, 22, true, 1)
    private val pairB = Person("B", 24, 22, false, 2)
    private val standardOnly = listOf(WindowType("Standard", 24, 22, true, 1))

    @Test
    fun aLateStartLengthensTheEstimate() {
        val counts = listOf(TypeCount(1, "Standard", 48, 0))      // 1 hour for the pair together
        val crew = listOf(pairA, pairB)
        assertEquals(hour, totalMillis(counts, standardOnly, crew))

        // B starts after 30 min: half speed for 30 min (15 min of work), then 45 min more
        val late = mapOf(2L to Availability(startMs = 30 * 60_000L))
        assertEquals(4_500_000L, totalMillis(counts, standardOnly, crew, late))
    }

    @Test
    fun leavingEarlyLengthensTheEstimate() {
        val counts = listOf(TypeCount(1, "Standard", 48, 0))
        val early = mapOf(2L to Availability(endMs = 30 * 60_000L))
        // 30 min together (30 min of work), then A alone does the other 30 min of work in 60
        assertEquals(5_400_000L, totalMillis(counts, standardOnly, listOf(pairA, pairB), early))
    }

    @Test
    fun interiorTimeFollowsWhoIsThere() {
        val counts = listOf(TypeCount(1, "Standard", 24, 22))     // 0.5 h interior + 0.5 h exterior
        val early = mapOf(2L to Availability(endMs = 15 * 60_000L))
        val crew = listOf(pairA, pairB)
        assertEquals(6_300_000L, totalMillis(counts, standardOnly, crew, early))
        assertEquals(2_700_000L, interiorMillis(counts, standardOnly, crew, early))
    }

    @Test
    fun availabilityAndCrewMembersSurviveEncoding() {
        val map = mapOf(
            2L to Availability(startMs = 1_800_000L),
            3L to Availability(endMs = 600_000L)
        )
        assertEquals(map, AvailabilityCodec.decode(AvailabilityCodec.encode(map)))
        assertEquals(emptyMap<Long, Availability>(), AvailabilityCodec.decode(""))

        val members = listOf(
            CrewMember(1, "Me", 24, 22, listOf(TypeCount(1, "Standard", 14, 0))),
            CrewMember(2, "Sam", 18, 16, listOf(TypeCount(1, "Standard", 5, 0)), 600_000L, 2_400_000L)
        )
        assertEquals(members, Crew.decode(Crew.encode(members)))
        assertEquals(1_800_000L, members[1].presentMs(hour))
        assertEquals(hour, members[0].presentMs(hour))
    }

    @Test
    fun splitFollowsTimePresent() {
        val planned = listOf(TypeCount(1, "Standard", 30, 0))
        val split = Crew.split(planned, listOf(pairA, pairB), mapOf(1L to 2 * hour, 2L to hour))
        assertEquals(listOf(20, 10), split.map { it[0].interior })
    }

    @Test
    fun personalPaceUsesTheTimeTheyWereThere() {
        val members = listOf(
            CrewMember(1, "Me", 24, 22, listOf(TypeCount(1, "Standard", 20, 0))),
            CrewMember(2, "Sam", 18, 16, listOf(TypeCount(1, "Standard", 5, 0)), 0L, hour / 2)
        )
        val run = Run(
            jobName = "A", startedAt = 0L, plannedMs = hour, actualMs = hour,
            interior = 25, exterior = 0, crew = Crew.encode(members)
        )
        // Sam did 5 windows in the 30 minutes they were there: 10 an hour
        assertEquals(10.0, summarize(listOf(run), 2L, 1)!!.overallPace!!, 0.001)
        // The crew pace stays windows over the run: 25 in an hour
        assertEquals(25.0, summarize(listOf(run), null, 1)!!.overallPace!!, 0.001)
    }
}