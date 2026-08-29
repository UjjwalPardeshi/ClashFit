# 30 · Rules Delta — organiser clarifications, 20–24 Aug

Six things the organisers have said in the participant group that were **not** in the 41 documents
written before them. One is existential. Checked against the live site on 29 Aug.

---

## 1. 🚨 Office Kit does not run on Linux

> *"Office Kit runs on Windows 10 or later and macOS 10.14.6 or later. **There is no Linux
> support.** If you're Linux-only, sort out a dual-boot or a Windows/Mac machine before you
> travel — Red Light is more than half the build."* — organisers, 21 Aug

Confirmed on the site: Office Kit's `desktopReq` is *"Windows 10 or later · macOS 10.14.6 or later"*.

**Status, 29 Aug: deferred by the team — being handled separately.** It stays as R0 in the risk
register because nothing about the underlying constraint has changed.

**Omkar's machine is Ubuntu 24.04, single disk, two partitions — 1 GB EFI and 475 GB ext4. There
is no Windows partition.** The `Windows Boot Manager` EFI entry is stale: `bootmgfw.efi` is not on
the EFI partition, so that install was wiped.

**What it costs if unsolved:**

| | |
|---|---|
| Red Light | ~10.5 of 19 build hours. Without Office Kit the laptop is unusable in all of them — phone-only, no mirror, no clipboard, no file transfer, no remote control. |
| Office Kit score | **10% of the event rubric**, measured by HackTracker as counts and durations. A machine that cannot run it scores zero on that line. |
| Gemma weights | The plan moves the ~3 GB `.task` file to the phone over Office Kit file transfer. No Office Kit, no route. |
| Demo staging | Mirroring the phone to a laptop for the duel and the pitch ([11-DEMO-SCRIPT](11-DEMO-SCRIPT.md) §5) needs it. |

**Options, in order of how much I'd trust them:**

1. **Ujjwal's laptop runs Windows or macOS.** Cheapest fix by far — designate his as the Office Kit
   machine, pair both phones to it, and Omkar treats Red Light as genuinely phone-only. Costs
   nothing and needs confirming today.
2. **Borrow a Windows or Mac laptop** for the weekend.
3. **Dual-boot.** The whole 475 GB is one ext4 partition, so it needs shrinking first, plus a
   Windows licence and installation. Days out from the deadline this is real risk for a machine
   you also need working.
4. **Windows VM.** CPU virtualisation is supported, but Office Kit pairs to a phone over
   USB/wireless and passthrough is exactly where VMs fail. Do not discover this at the venue.

---

## 2. You may keep building on the prototype you submitted — and the site says the opposite

> *"If you submitted a prototype at registration, you **can keep building on it** on-site."*
> — organisers, 24 Aug

The site's Guide still reads: *"Original work only: code written during the event window. No
shipping a pre-built product,"* and *"Organisers may verify a project was built inside the event
window."*

**These contradict.** The reading that satisfies both, and the one this project follows anyway:

- The submitted prototype is a **browser** prototype. Carrying it forward is explicitly permitted.
- The Android app is **new work written at the event** — so nothing pre-built is being shipped as
  the deliverable.
- Disclose the prototype on the Phase-1 form regardless. Disclosure costs nothing and removes the
  entire question.

**Team decision, 29 Aug: building on the submitted prototype is treated as allowed.**

So the plan is now: continue from the prototype on-site rather than retyping the core, and disclose
it on the Phase-1 form regardless. The Android app is still new work written at the event, so
nothing pre-built is being shipped as the deliverable either way.

One caveat worth keeping visible: **the site's Guide still says the opposite**, and it is the
formal published rule. Disclosure is what makes this safe — a disclosed prototype that an organiser
has permitted in writing in the group is defensible; an undisclosed one is not, under either
reading. Getting the confirmation by email would remove the last of the doubt and costs one line.

---

## 3. "AI-first thinking" is now a named shortlisting criterion

The admin list of 24 Aug, which does **not** match the site's wording:

| # | Criterion |
|---|---|
| 1 | Problem clarity & relevance |
| 2 | Novelty and originality of the idea |
| 3 | **AI-first thinking — how central AI is to your solution** |
| 4 | iQOO device fit — how well it may use the phone |
| 5 | Feasibility & impact |

The site says screening weights *"novelty, tech impact, problem choice, idea/scope fit for 30
hours, and phone-first fit."* Satisfy both — they overlap on four of five.

**The one that is genuinely new is #3.** The Phase-1 description must make AI *central*, not a
component. Ours already is — pose estimation plus an on-device language model is the product, not
a feature bolted to it — but the copy has to lead with that rather than with the game.

---

## 4. A different idea per city

> *"Please submit a different idea for each; the same idea pasted everywhere reads weak."*

[16-PRE-EVENT-CHECKLIST](16-PRE-EVENT-CHECKLIST.md) previously suggested registering Hyderabad with
"the same prep carries over." **That advice was wrong** and has been corrected. A second city needs
a second idea, which is real work.

**Team decision, 29 Aug: Hyderabad is dropped.** Pune is the only city. That is the right call
three days out — a second idea built properly is a fortnight of work, and a weak one costs more in
reputation than the extra shot is worth.

---

## 5. You cannot leave the venue

> *"You stay in the arena for the full 30 hours. Leaving midway counts as a disqualification."*

Not in any runbook before now. It changes practical planning: no hotel run, no going out for food,
no stepping away to rest properly. Food and mattresses are provided. Pack accordingly
([15-ASSET-BRIEF](15-ASSET-BRIEF.md) §7).

---

## 6. Smaller items

| | |
|---|---|
| **Team locks at shortlisting** | Members can be added right up to shortlisting, never after. |
| **Recent graduates** | An unemployed recent graduate counts as a **working professional**. Confirms our bucket if it applied. |
| **OpenRouter credits** | Cloud AI credits are provided. Does not weaken the on-device story — it strengthens it, because everyone else will use the free cloud credits and we will not. |
| **Certificates** | Go to shortlisted participants who attend, not to everyone who registered. |
| **Prototype expectation** | *"You are expected only to build a very rudimentary prototype and not the full product."* Ours is far past rudimentary, which is an advantage, not a problem. |
| **External hardware** | Explicitly not recommended. We use none. ✅ |
| **Problem statements** | The admin says they are on the site and the dashboard. The dashboard is auth-gated, so **check yours** — [16-PRE-EVENT-CHECKLIST](16-PRE-EVENT-CHECKLIST.md) already flags the mandatory problem-statement step. |
