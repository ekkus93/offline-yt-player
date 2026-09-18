# OYP-1704 Accessibility Qualification

OYP-1704 is guarded by a deterministic policy and JVM tests so regressions fail normal CI.

The qualification requires every primary product surface to preserve a minimum 48 dp action target, TalkBack semantics, logical focus ordering, a non-color status cue, and large-text compatibility. The policy covers Library, Add, Download Setup, Downloads, Player, and Settings.

`MidnightTransit.MinimumTouchTarget` remains the implementation-level 48 dp token. The accessibility gate independently rejects any surface below 48 dp. Large-text qualification uses the compact-profile scale already exercised by the portrait layout policy.

This gate intentionally records product invariants rather than claiming an emulator-driven TalkBack audit. Device-level accessibility testing remains useful release qualification, while the deterministic policy prevents the required semantics from silently disappearing from the product contract.
