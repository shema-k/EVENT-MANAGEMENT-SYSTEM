# Event Management

A desktop system for running several events at once: the catalogue, the tickets, the
money, the programme, the door, and what happened afterwards. Written in plain Java with
Swing over an embedded H2 database — no build tool, no application server, no
dependencies beyond one JDBC driver.

```
event-management-system/
├── run.sh                    compile and launch
├── run-tests.sh              compile and run the test suite
├── lib/h2-2.2.224.jar        the only dependency
└── src/main/java/com/eventsuite/
    ├── App.java              entry point
    ├── DemoData.java         sample events for a first run
    ├── core/                 events, categories, lifecycle, venues
    ├── ticketing/            tiers, tickets, registrations, access levels
    ├── finance/              payments, invoices, budgets, money
    ├── people/               attendees, speakers, kit
    ├── marketing/            campaigns, discount codes
    ├── ops/                  check-in, gates, engagement
    ├── analytics/            dashboard metrics, ROI, feedback, sustainability,
    │                         reconciliation
    ├── workflow/             the automation rules and their log
    ├── data/                 schema and stores
    ├── service/              the operations the screens call
    └── ui/                   thirteen screens, charts, theme
```

## Run it

```bash
cd event-management-system
./run.sh
```

Needs JDK 17 or newer and a desktop session. `run.sh` compiles with `javac`, opens
`events.db`, and opens the window. Pass a file name to keep two sets of events apart:

```bash
./run.sh festival.db
```

Delete `events.db.mv.db` and run again to start over from the sample data.

## Test it

```bash
./run-tests.sh
```

123 tests covering the money arithmetic, the lifecycle rules, allocation, refunds, the
door, reconciliation and the automation engine. They run against a real embedded
database rather than mocks, because the mistakes worth catching — a sale that records a
ticket but not the payment, a refund that leaves the seat counted — only appear once
there is a database involved.

## The screens

| Screen | What it is for |
|---|---|
| Dashboard | Everything at once, and what needs a decision today |
| Catalogue | Every registered event, grouped by category |
| Ticketing | Tiers, taking payment, the tickets issued |
| Attendees | Who is coming, the waiting list, repeat attendance |
| Speakers and kit | The programme, and what has been booked for it |
| Marketing | Campaigns, what they cost, what they returned |
| Check-in | The door: scan a code, see what happened |
| Engagement | What people took part in, and the carbon footprint |
| Finance | Takings, costs, budget, and the ledger |
| Analytics | Feedback by topic, return on spend, the reconciliation |
| Automation | Rules, what they have done, what is waiting for approval |

Screens refresh every five seconds while the window is showing, so the dashboards are
live without being reloaded.

## How it is put together

**Money is `BigDecimal`, always.** A concert that takes nine thousand card payments
loses real money to binary rounding on its own. Every figure is normalised to two
decimal places on the way in, so adding a hundred ticket prices cannot leave a tail of
fractions to explain away later.

**Rules live in the domain, not in the screens.** `EventStatus` will not let an event go
backwards once money has been taken; `TicketStatus` will not let a ticket be used before
it is paid for; `BudgetLine` subtracts commitments as well as spend. One place decides,
so every screen behaves the same way.

**Operations are transactions.** `EventSuite.sellTicket` writes the ticket, the payment,
the registration and the discount redemption as one unit of work. A failure part way
through cannot leave a ticket nobody paid for. The window holds the connection open for
its own lifetime, because a refresh timer that outlives the connection would fail every
five seconds for as long as the window was open.

**Automation holds back the irreversible.** A rule that sends an email or raises a task
runs unattended. A rule that changes money or capacity — marking an event sold out,
stopping sales, issuing refunds — is held until somebody approves it, and the held-back
actions are listed at the top of the Automation screen.

**Committed and spent are separate columns.** Two thirds of a production budget promised
to suppliers six weeks out looks like a disaster if it is reported as spend, and looks
healthy if it is not reported at all. Showing both is what makes the difference visible.

**Attendance is measured against buyers, not capacity.** A sold-out event where a third
of the holders did not turn up is not a full house. It is a sold-out event with a third
of its holders absent, and those need different responses.

**Deletion is refused once there is history.** An event with tickets sold, money
recorded or people who have checked in is kept. Its record is the only evidence the
event happened, and that is not something a tidied-up list should be able to remove.

## What the first run gives you

Five events across different categories and different stages, chosen to cover the
awkward cases as much as the ordinary ones:

- **East Africa Product Summit** — on sale, nearly full
- **Lakeside Music Festival** — finished, with arrivals, feedback and a sustainability report
- **Meridian Product Launch** — finished and ready to reconcile, losing money
- **Pricing for Small Teams** — in planning, with a speaker who has not answered
- **Regional Logistics Forum** — a draft, with nothing sold

The seeds deliberately include a sold-out tier, three budget headings over their
allocation, an unconfirmed speaker and unpaid invoices, because those are the situations
the screens exist for. Data is only seeded when the database is empty, so it can never
overwrite anything real.

## Notes and limits

- **Single file, single user.** One database file, no accounts, no password. That is the
  right trade for one organiser or a small team on one machine, and it is worth naming:
  it is not a multi-user server.
- **No live payment gateway.** A sale records that money was taken by a method. Taking
  it for real needs a gateway integration, which is a separate piece of work.
- **Rates are UK defaults.** VAT at twenty per cent, fees of 1.4 per cent on card
  payments. They are constants in `PaymentMethod` and `BudgetCategory`, meant to be
  changed for another country rather than assumed universal.
- **Sustainability factors are stated, not hidden.** Carbon per kilowatt hour, per
  kilogram of waste, per litre of fuel. A footprint quoted without its assumptions is an
  assertion rather than a measurement.
