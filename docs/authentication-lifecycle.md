# Microsoft sign-in lifecycle regression

Based on PR #297 revision `fdc3c95d3ed6454874e0a9bfdb5e474366869ca6` by AntonSelin.
The native launcher/profile/dashboard implementation remains that existing MIT contribution.
This small supplemental patch and regression fixtures were written and reviewed by an AI agent;
no separate human review or human acceptance test is claimed. PR #298 was read for comparison;
none of its source was copied into this patch. No HMCL code or new dependencies are introduced.

## Defect and behavior

The modeless sign-in dialog allows another account choice while authentication is pending.
The original service installs the result unconditionally, so sign-in A can overwrite an offline
choice, or replace newer sign-in B if A finishes last. Closing the device dialog does not cancel
the service. Obsolete results can also change the persisted selected account and save credentials.

Each account intent now reserves a generation before the dashboard queues its worker. New sign-in,
offline selection, dialog Cancel/close and dashboard/service shutdown invalidate the earlier intent
and interrupt its polling worker. Device instructions and account persistence check the generation;
UI callbacks check a separate display generation. A late network result that ignores interruption
cannot commit. A queued older offline worker cannot cancel a newer sign-in. Changed Microsoft app
ID or credential persistence preferences cancel active sign-in. Cancellation does not mark a newer
operation's progress as failed. The original single-argument sign-in/offline service APIs remain usable.

Interruption stops sleeping polling. It does not guarantee immediate abort of an already executing
OkHttp HTTP call; existing connect/read timeouts still bound that call. The generation check prevents
its late response from changing state. This change does not refactor token-refresh locking or establish
transactional disk rollback if persisting a valid account fails.

## Reproduction and verification

`LauncherAuthenticationRaceTest` runs real `LauncherServices` state/persistence operations with a
latched fake `MicrosoftAccountService`. One fixture deliberately ignores interruption, so merely
interrupting a worker cannot satisfy the regression. Tests cover A→offline→A completion, A/B reverse
completion, cancellation, shutdown, delayed device instructions, stale queued sign-in/offline requests,
interruptible polling, changed auth preferences, and a valid current login. Test credentials and paths
are synthetic. All discovery is pointed at an explicit temporary Minecraft directory.

The baseline uses the original account state logic with only injectable-provider constructor plumbing
and backwards-compatible interface defaults, so the same tests compile. Its authentication methods,
offline method, close method and UI remain unchanged. It fails nine of ten cases, while the current-login
control passes. The patched service passes all ten.

`DashboardAuthenticationLifecycleTest` creates native Swing windows with a fake `LauncherActions`,
programmatically clicks actual buttons, answers the synthetic device-start confirmation, sends a native
window-closing event, and supplies delayed callbacks. It verifies repeated login selection, window close,
offline selection, and completion progress. It uses no real authentication or browser. Its image artifacts
are Swing renders of live native-peer windows, not photographs or OS screenshots. Service persistence
is tested separately above; the UI fixture is not an end-to-end Microsoft protocol test.

Typical commands after resolving existing Maven dependencies:

```sh
mvn -o -Djava.awt.headless=true \
  -Dtest=LauncherAuthenticationRaceTest,MicrosoftAccountServiceTest,DashboardPanelTest test
mvn -o -Djava.awt.headless=false \
  -Dtest=DashboardAuthenticationLifecycleTest,DashboardDisplayableTest test
```

The prepared workspace uses a checksum-verified portable JDK 21 and Maven 3.9.11, explicit blank Maven
settings, its own local dependency repository and `user.home`, and no `Main` invocation. Headless tests
run under the restricted environment with Maven offline. Native tests need desktop access; the first
restricted native attempt terminated the JVM (exit 134), then the explicitly scoped approved native run
succeeded. No automatic approval rejection occurred.

Validation covers Java 9 release compilation, ten service regressions, five existing auth fixtures,
sixteen existing dashboard cases and four native cases. It does not validate real Microsoft login,
Minecraft gameplay/mod compatibility, authenticated server admission, real-router sharing or all #296
acceptance requirements. No game/server artifacts, EULA acceptance, port mapping or payment action
are involved. This supplemental patch alone is not a complete bounty implementation.
