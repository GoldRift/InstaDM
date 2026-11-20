---
name: jetpack-compose-performance
description: Diagnose and optimize Android Jetpack Compose UI performance bottlenecks (recomposition overhead, dropped frames, jank, lazy list scroll lag, custom modifiers). Use when profiling, auditing, or fixing Compose performance issues.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-10-22'
---

# Jetpack Compose Performance

## Prerequisites & audit scope

- Only profile/benchmark release builds with R8 enabled and Baseline Profiles where applicable; debuggable ART verification distorts results.
- Audit only the composables/files being touched unless the user requests a full performance audit.
- **On-demand references**: the rules and scope guards below are self-contained for most edits. Open a `references/*.md` file only when you hit a non-obvious edge case or need a detailed code migration pattern (such as `Modifier.Node` or `backwards-write`).

## 1. State, snapshots, and stability (`references/state-and-stability.md`)

- **Phase deferral**: wrap fast-changing state reads in lambdas (`Modifier.offset { ... }`, `Modifier.graphicsLayer { ... }`) so reads happen in Layout or Draw instead of Composition.
- **No backwards writes** (`references/android/develop/ui/compose/performance/backwards-write.md`): never write state after reading it in the same composition pass or in a later phase (`Read -> Write`, `Composition Read -> Layout/Draw Write`). Do **not** flag `Write -> Read` at the top of a composable as a backwards write.
- **No redundant state wrapping (`Write -> Read`)**: do not wrap synchronous values in `remember { mutableStateOf(...) }` only to overwrite them immediately at the top of composition; use a local `val` or read in the target phase (`drawWithCache`).
- **`remember` keys & slot overhead**: pass all captured inputs/parameters as `remember` keys. Only `remember` heavy allocations or identity-sensitive objects. Do **not** pass snapshot values (`state.value`) as keys when wrapping `derivedStateOf` (passing the `State` instance is fine).
- **`computedStateOf` vs `derivedStateOf`**: on Compose 1.13+, use `computedStateOf` for simple conditions/filters from fast-changing inputs (e.g. `scrollState.value > 0`); on Compose `< 1.13`, use `remember { derivedStateOf { ... } }`. Reserve `derivedStateOf` for heavy multi-read cached calculations. For trivial boolean/property lookups, prefer plain Kotlin `get()`.
- **Synchronous derivation**: replace `produceState` + `snapshotFlow` observing Compose state with synchronous `derivedStateOf` / `computedStateOf`.
- **Collections & snapshot reads**: prefer `mutableListOf()` + `mutableStateOf(list, neverEqualPolicy())` for whole-list swaps over `mutableStateListOf()`. Use `SnapshotStateList.toList()` for repeated iterations. Extract repeated `State` reads into a local `val`. Avoid reading Compose `State` inside custom property setters.
- **Stability & Strong Skipping** (`references/android/develop/ui/compose/performance/stability/strongskipping.md`):
  - Skipping is not free (slot storage + parameter comparisons). Strong Skipping Mode already skips unstable parameters (including `List`) via `O(1)` instance equality (`===`).
  - **Never** add `@Immutable` / `@Stable` to models containing collections (`List`, `Set`, `Map`) and **never** recommend replacing `List<T>` with `ImmutableList<T>` (forces `O(N)` `.equals()`).
  - **Removing existing `@Stable`/`@Immutable`**: only recommend removal when **both** (1) the model feeds a lazy layout (`LazyColumn`, `LazyRow`, `LazyGrid`, `Pager`, `ScalingLazyColumn`) **and** (2) it holds a large dataset or deeply nested collections. Leave all other existing annotations untouched.
  - **Adding `@Stable`/`@Immutable`**: flat `data class`es with `val` primitives/`String` in Compose modules are already inferred stable. Only annotate when the compiler cannot infer stability (non-Compose module/interface), a data source allocates fresh instances on unchanged emissions, and the class is small, flat, collection-free, with `O(1)` `.equals()`.
  - Focus on making composables cheap and deferring reads rather than chasing recomposition counts.

## 2. Layouts and measurement (`references/layouts.md`)

- **Coordinates**: avoid `onGloballyPositioned`; use `Modifier.onVisibilityChanged` for viewport tracking, `Modifier.onPlaced` / `Modifier.onLayoutRectChanged` for local bounds, or `requireLayoutCoordinates()` on demand in `Modifier.Node`.
- **Floating content**: anchor tooltips/labels via a custom `Layout` measuring both children together; never store `onGloballyPositioned` coordinates in `MutableState` + `offset` (causes 1-frame lag and extra composition).
- **Offsets & wrappers**: use `Modifier.offset { IntOffset(...) }` for dynamic offsets. Avoid redundant wrapper `Box`es and decorative `Canvas` nodes (attach modifiers directly or use `Modifier.drawBehind`).
- **Subcomposition & intrinsics**: avoid `BoxWithConstraints` / `SubcomposeLayout` in hot paths and lazy items (prefer `Modifier.layout` or `LayoutModifierNode`). Avoid deeply nested intrinsic measurements.

## 3. Lazy layouts (`references/lazy-layouts.md`)

- **Scope guard**: `key` and `contentType` rules apply **only** to lazy containers (`LazyColumn`, `LazyRow`, `LazyGrid`), never standard `Column`/`Row`/`Box`.
- **Mandatory primitive `key` and `contentType`**: always provide primitive `key` (`Int`, `Long`, `String`) and `contentType` on `items`/`itemsIndexed` so composition slots are reused.
- **No `O(N²)` index lookups**: replace `list.indexOf(item)` with `itemsIndexed`.
- **Selection state**: use immutable `Set` in `MutableState`, `mutableStateSetOf()`, or `Map` for `O(1)` lookups; pass `isSelected: () -> Boolean` lambdas to leaf composables.
- **Hoist locals & flows**: resolve `CompositionLocal`s (`LocalContext`, `LocalDensity`, transition scopes) and `collectAsStateWithLifecycle()` once in the parent container, not inside each lazy item.
- **Visibility & small lists**: gate `Modifier.sharedBounds`/`sharedElement` and impression analytics in lazy items with `Modifier.onVisibilityChanged` (never `LaunchedEffect(Unit)` in lazy items, since items pre-compose offscreen). Read `.size` directly instead of `derivedStateOf`. Use plain `Column`/`Row` + `forEach` for small bounded lists (~1 to 10 items).

## 4. Animation and shared transitions (`references/animation.md`)

- Defer animated alpha/scale/rotation/color to `Modifier.graphicsLayer { ... }`, offsets to `Modifier.offset { IntOffset(...) }`, and size/constraints to `Modifier.layout { ... }` or `Modifier.animateBounds`.
- Pass lambda providers (`() -> Float`) instead of raw animated values to children.
- Gate `sharedBounds`/`sharedElement` in fast-scrolling lists with `Modifier.onVisibilityChanged`.
- Store persistent `Animatable` on `Modifier.Node` and launch in `onAttach()` via `coroutineScope.launch`.

## 5. Effects, lifecycle, and threading (`references/effects-and-threading.md`)

- **No side effects in composition body**: use `SideEffect`, `LaunchedEffect`, or event callbacks.
- **No non-suspending `LaunchedEffect` or empty `DisposableEffect`**: use `SideEffect` for synchronous work (Compose 1.12+ supports `SideEffect(keys)`; on `< 1.12`, track key changes in a `remember`ed holder inside `SideEffect { ... }`).
- **Effect cost & ordering**: cache heavy allocations in `remember`. Combine sibling `LaunchedEffect(Unit)` calls with nested `launch { ... }`. Avoid frequently cycling `LaunchedEffect` keys during scroll/animation. Guard optional effects (`if (callback != null)`). Use `rememberUpdatedState` in long-running effects to avoid stale captures.
- **Threading & lifecycle**: offload blocking I/O to `withContext(Dispatchers.IO)` (e.g. `produceState`). Use `collectAsStateWithLifecycle()` for Android flows doing background work, `collectAsState()` for lightweight in-memory `StateFlow`. Hoist `BroadcastReceiver`/system listeners to screen level. Replace `Handler.postDelayed`/`TimerTask` with `LaunchedEffect` + `delay()`.

## 6. Graphics and custom drawing (`references/graphics-performance.md`)

- Replace decorative `Canvas` with `Modifier.drawWithCache` to cache `Path`, `Paint`, and `Brush`.
- Use Coil's `AsyncImage` for remote images and `painterResource` for drawables.
- `remember` `RuntimeShader`/`ShaderBrush`, set size uniforms in `drawWithCache`, per-frame uniforms in `onDrawBehind`.
- Prefer `Path.rewind()` over `reset()` in hot draw loops. Avoid generic tuples (`Pair`, `Triple`) and boxed sets (`HashSet<Long>`); use `MutableLongSet` and `packInts`.

## 7. Custom modifiers: `Modifier.Node` (`references/android/develop/ui/compose/custom-modifiers.md`)

- Migrate legacy `Modifier.composed` to `ModifierNodeElement` + `Modifier.Node` (or `DelegatingNode`) with capability interfaces (`DrawModifierNode`, `LayoutModifierNode`, `SemanticsModifierNode`, `CompositionLocalConsumerModifierNode` via `currentValueOf(...)`).
- Query `requireLayoutCoordinates()` on demand inside pointer/draw handlers rather than `GlobalPositionAwareModifierNode.onGloballyPositioned`.
- Read `references/android/develop/ui/compose/custom-modifiers.md` when migrating complex modifiers to `Modifier.Node`.
