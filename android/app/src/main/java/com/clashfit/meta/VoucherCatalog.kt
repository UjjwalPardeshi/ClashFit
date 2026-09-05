package com.clashfit.meta

/**
 * One reward a partner would fund, earned by training rather than bought.
 *
 * @param code the string the player reads out at the till. Fixed per voucher rather than generated:
 *   one code per reward, so a player can read it off the screen and a partner can recognise it.
 * @param milestone what earned it, in the words the app already uses on screen.
 */
data class Voucher(
    val id: String,
    val brand: String,
    val offer: String,
    val code: String,
    val milestone: String,
    val kind: VoucherKind,
)

/** What sort of partner this is, which is all the colour and the mark are drawn from. */
enum class VoucherKind { NUTRITION, GEAR, GYM, RECOVERY, CLINIC }

/**
 * The vouchers ClashFit can award, and what earns each one.
 *
 * The partners are ClashFit's own: five brands built for this programme rather than licensed from
 * anybody, which is why the offers can be stated plainly instead of hedged. Nothing here claims a
 * relationship with a company that has not agreed to one.
 *
 * They are rarer than badges on purpose. There are twenty-two badges and a badge is a pat on the
 * back; a voucher is meant to feel like the thing you tell someone about. So they hang off the
 * milestones that take real time — a week of showing up, a thousand clean reps, a rank — and never
 * off a single good session.
 *
 * The thresholds are deliberately the ones the badge catalogue does NOT use, so a milestone gives
 * you either a badge or a voucher and the two never arrive on top of each other.
 */
object VoucherCatalog {

    val all: List<Voucher> = listOf(
        // ── showing up, which is the thing a sponsor actually wants to pay for ────────────────
        Voucher(
            id = "streak_7",
            brand = "Iron Oak Nutrition",
            offer = "20% off your first order",
            code = "IRONOAK-7DAY-3K9F",
            milestone = "A seven-day streak",
            kind = VoucherKind.NUTRITION,
        ),
        Voucher(
            id = "streak_30",
            brand = "Northgate Athletic",
            offer = "A free pair of training shoes",
            code = "NORTHGATE-30DAY-QP2M",
            milestone = "A thirty-day streak",
            kind = VoucherKind.GEAR,
        ),

        // ── clean reps: earned by form, which is the measurement this whole app is built on ───
        Voucher(
            id = "clean_100",
            brand = "Basecamp Fitness",
            offer = "One free day pass",
            code = "BASECAMP-100-R7XD",
            milestone = "100 clean reps",
            kind = VoucherKind.GYM,
        ),
        Voucher(
            id = "clean_500",
            brand = "Iron Oak Nutrition",
            offer = "A month of recovery protein",
            code = "IRONOAK-500-W4NB",
            milestone = "500 clean reps",
            kind = VoucherKind.NUTRITION,
        ),
        Voucher(
            id = "clean_1000",
            brand = "Stillwater Recovery",
            offer = "A sports massage, on the house",
            code = "STILLWATER-1K-H8VC",
            milestone = "1,000 clean reps",
            kind = VoucherKind.RECOVERY,
        ),

        // ── rank ─────────────────────────────────────────────────────────────────────────────
        Voucher(
            id = "level_5",
            brand = "Basecamp Fitness",
            offer = "A week of classes, free",
            code = "BASECAMP-LVL5-Z6TR",
            milestone = "Reaching level 5",
            kind = VoucherKind.GYM,
        ),
        Voucher(
            id = "level_10",
            brand = "Northgate Athletic",
            offer = "30% off anything in store",
            code = "NORTHGATE-LVL10-Y3QK",
            milestone = "Reaching level 10",
            kind = VoucherKind.GEAR,
        ),
        Voucher(
            id = "level_20",
            brand = "Meridian Physio",
            offer = "A full movement assessment",
            code = "MERIDIAN-LVL20-M9DP",
            milestone = "Reaching level 20",
            kind = VoucherKind.CLINIC,
        ),

        // ── the weekly challenge: the one moment a brand could fund a single week ─────────────
        Voucher(
            id = "weekly_1",
            brand = "Stillwater Recovery",
            offer = "A free recovery session",
            code = "STILLWATER-WK1-B5LG",
            milestone = "Your first weekly challenge",
            kind = VoucherKind.RECOVERY,
        ),
        Voucher(
            id = "weekly_5",
            brand = "Meridian Physio",
            offer = "Half price on a mobility plan",
            code = "MERIDIAN-WK5-F2NW",
            milestone = "Five weekly challenges",
            kind = VoucherKind.CLINIC,
        ),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Voucher? = byId[id]
}
