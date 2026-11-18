# Provisional supply-chain exception — basic-toml (2026-11-18)

**Advisory:** RUSTSEC-2026-0374 (unmaintained, not a reported vulnerability)  
**Dependency:** `basic-toml` 0.1.10  
**Dependency path:** UniFFI 0.31 binding/code generation → `askama_derive` 0.14.0 → `basic-toml` 0.1.10  
**Scope:** Internal engineering CI advisory scan only. This is **not** public release or license/policy approval.

## Risk and reason for provisional exception

RustSec identifies `basic-toml` as **unmaintained**, with no patched version available. It is used through Askama's template-derivation machinery during UniFFI binding generation; it is not a production video URL parser, player, or Android media database handler. An unmaintained build-time dependency is still a supply-chain risk and is not silently approved.

The other alert in the 2026-11-18 scan, **RUSTSEC-2026-0459** for bundled SQLite, is *not* excepted: `rusqlite` was upgraded to 0.41, yielding a patched `libsqlite3-sys` release. The exact-head CI audit must continue to fail on any new unexcepted security advisory or warning.

## Mitigation and follow-up

- Keep the exception restricted to **RUSTSEC-2026-0374**; the workflow runs `cargo audit --deny warnings --ignore RUSTSEC-2026-0374`.
- Treat build templates/configuration as trusted project inputs, not input from video URLs, users, or providers.
- Track UniFFI/Askama upstream releases and remove the exception when the dependency can be eliminated.
- Reassess immediately if a vulnerability (not only unmaintained status) is published for this crate or a related dependency.
- **Expiry/review:** 2027-01-18 or before any public/app-store release, whichever occurs first. At that point replace the dependency or obtain an explicit owner-approved, updated exception; the present document itself provides no release authorization.

## Evidence

On 2026-11-18, the scan at exact SHA `42d18e187e60ef8d599d932d8df1fed4df5a6825`, GitHub Actions run `44403521226`, reported both RUSTSEC-2026-0459 and RUSTSEC-2026-0374. After the `rusqlite` upgrade, run `44404517416` at `aa28c4a051b467544cfa067dc955b783c4afe8a9` reported **only** the `basic-toml` unmaintained warning. The finalized commit must receive a fresh exact-head supply-chain run; this is not evidence of a pass for the final commit.

**Release-gate reminder:** Document this exception in the next exact-head RMD-1803 qualification audit and obtain the separate human service-policy/legal review before distribution.
