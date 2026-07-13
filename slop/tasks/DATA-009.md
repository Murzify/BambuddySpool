# [DATA]-[009] Implement a Fail-Closed SlotTopologyResolver

Status: Completed on 2026-07-13.

Implemented scope:

- Added a common `SlotTopologyResolver` contract and `KnownSlotTopologyResolver`.
- Centralized the confirmed Bambu Lab A1 external mapping in the topology package only.
- Resolves physical external slots from `PrinterStatus.virtualTrays`; assignments are used only for current assignment state and AMS evidence.
- Returns either a complete supported slot list or a typed `UnsupportedTopology` reason.
- Represents multiple known external slots when an explicit rule set is supplied.
- Applies label precedence: API label, known topology label, then `External slot N`.
- Represents assignment-evidenced AMS slots as read-only and blocks AMS mutation targets with `AmsSlotSelectedForMutation`.
- Fails closed for missing physical slots, unknown mappings, partial known/unknown physical topology, contradictory coordinates, and unsupported mutation targets.
- Updated atomic snapshot sync to call the resolver and reject unsupported topology with a typed sync failure instead of publishing partial slot rows.

Definition of Done:

- [x] Pure common tests cover A1 resolution.
- [x] Pure common tests cover multiple known external slots.
- [x] Pure common tests cover AMS read-only representation and mutation blocking.
- [x] Pure common tests cover missing, contradictory, partial, and unknown physical topology.
- [x] Unsupported topology blocks mutation with a typed reason.
- [x] The confirmed A1 mapping constants are no longer duplicated in sync.

Verification:

```text
./gradlew :shared:testAndroidHostTest
```

Final full verification is recorded in the implementing agent response.
