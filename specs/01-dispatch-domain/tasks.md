# Phase 01 — Dispatch domain · Tasks

Rules: one task = one commit. Test first. Tick only when `./mvnw verify` is green.

- [ ] **01.1** Value objects
  - Test: `QuantityTest`, `DeliveryWindowTest`, `VesselTest`, `BerthTest`, `CancellationReasonTest`
    (valid cases + each invalid boundary: 0, 10,000, 10,000.001, equal instants, 6/8-digit IMO…).
  - Code: records + `DomainValidationException`, `FuelType`, `OrderId`.
  - _Req: DOM-1.2, DOM-1.3, DOM-1.4, DOM-1.5, DOM-2.6, DOM-NF-2_
- [ ] **01.2** `OrderStatus` transition table
  - Test: parameterized test covering all 20 cells of the transition table.
  - Code: `OrderStatus`, `OrderAction`.
  - _Req: DOM-2.5_
- [ ] **01.3** `DispatchOrder.create` and `OrderCreated`
  - Test: creation sets id, `CREATED`, `createdAt` from `Clock.fixed`; registers one `OrderCreated`.
  - Code: aggregate, `DomainEvent` sealed interface, `OrderCreated`.
  - _Req: DOM-1.1, DOM-1.6, DOM-3.2_
- [ ] **01.4** Transitions approve / dispatch / deliver / cancel
  - Test: each happy path + event; each invalid transition throws and leaves state and events untouched.
  - Code: transition methods, remaining event records, `InvalidOrderTransitionException`.
  - _Req: DOM-2.1, DOM-2.2, DOM-2.3, DOM-2.4, DOM-2.5, DOM-2.7_
- [ ] **01.5** `pullDomainEvents` and `rehydrate`
  - Test: events returned in order, list cleared after pull; `rehydrate` restores state without events.
  - Code: methods on the aggregate.
  - _Req: DOM-3.1_
- [ ] **01.6** ArchUnit guard
  - Test: `HexagonalArchitectureTest` with the rules of `structure.md`.
  - Code: ArchUnit dependency (test scope).
  - _Req: DOM-4.1, DOM-4.2_
- [ ] **01.7** ADR 0002 hexagonal architecture + domain events
  - _Req: DOM-4.2_
- [ ] **01.8** Verify phase: `/spec-review 01`, JaCoCo 100% branches on `domain`, README roadmap updated.
  - _Req: DOM-NF-1_
