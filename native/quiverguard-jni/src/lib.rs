//! JNI bridge between `com.jhaiian.clint.quiver.engine.QuiverGuardNative` (Kotlin) and the
//! `adblock` crate (adblock-rust). Every exported function is `catch_unwind`-wrapped: a panic
//! here must never unwind across the FFI boundary into the JVM, which would abort the whole app.
//!
//! Two kinds of opaque handles are passed back to Kotlin as `jlong`s:
//!   - a `FilterSet` builder handle, used only while compiling filter lists into an engine, and
//!   - an `Engine` handle, used for all matching/cosmetic queries once compiled (or reloaded).
//! Kotlin is responsible for not calling into a handle after destroying it (see
//! `QuiverGuardEngine.kt`, which guards this with a `ReentrantReadWriteLock`).

mod bundled_resources;

use adblock::engine::Engine;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use jni::objects::{JClass, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;
use serde::Serialize;
use std::panic::{self, AssertUnwindSafe};

// ---------------------------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------------------------

fn read_jstring(env: &mut JNIEnv, s: &JString) -> String {
    if s.is_null() {
        return String::new();
    }
    env.get_string(s).map(|s| s.into()).unwrap_or_default()
}

fn make_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(j) => j.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[derive(Serialize)]
struct ErrorJson<'a> {
    error: &'a str,
}

fn error_json(message: &str) -> String {
    serde_json::to_string(&ErrorJson { error: message })
        .unwrap_or_else(|_| "{\"error\":\"unknown\"}".to_string())
}

/// Runs `body`, converting any panic into `on_panic` instead of unwinding into the JVM.
fn guard<T>(on_panic: T, body: impl FnOnce() -> T) -> T {
    panic::catch_unwind(AssertUnwindSafe(body)).unwrap_or(on_panic)
}

// ---------------------------------------------------------------------------------------------
// FilterSet builder lifecycle (used only while compiling)
// ---------------------------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeNewFilterSetBuilder(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    guard(0, || {
        let filter_set = FilterSet::new(false);
        Box::into_raw(Box::new(filter_set)) as jlong
    })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AddListStats {
    rule_lines: u32,
    comment_lines: u32,
    empty_lines: u32,
}

#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeAddFilterListRules(
    mut env: JNIEnv,
    _class: JClass,
    builder_handle: jlong,
    rules: JString,
) -> jstring {
    let json = guard(error_json("panic while adding filter list"), || {
        if builder_handle == 0 {
            return error_json("builder handle is null");
        }
        let rules_str = read_jstring(&mut env, &rules);

        let mut rule_lines = 0u32;
        let mut comment_lines = 0u32;
        let mut empty_lines = 0u32;
        for line in rules_str.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() {
                empty_lines += 1;
            } else if trimmed.starts_with('!') || trimmed.starts_with('[') {
                comment_lines += 1;
            } else {
                rule_lines += 1;
            }
        }

        let filter_set: &mut FilterSet = unsafe { &mut *(builder_handle as *mut FilterSet) };
        let _ = filter_set.add_filter_list(rules_str, ParseOptions::default());

        serde_json::to_string(&AddListStats {
            rule_lines,
            comment_lines,
            empty_lines,
        })
        .unwrap_or_else(|_| "{}".to_string())
    });
    make_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeDestroyFilterSetBuilder(
    _env: JNIEnv,
    _class: JClass,
    builder_handle: jlong,
) {
    guard((), || {
        if builder_handle != 0 {
            unsafe { drop(Box::from_raw(builder_handle as *mut FilterSet)) };
        }
    });
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct FinalizeResult {
    success: bool,
    size_bytes: u64,
    error: Option<String>,
}

/// Consumes the builder handle (always - even on failure, it must not be reused afterwards),
/// compiles it into an optimized `Engine`, registers the bundled uBO resources, serializes the
/// engine, and writes it to `output_path`. Does NOT load it as the active engine - call
/// `nativeLoadEngine` afterwards for that.
#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeFinalizeEngine(
    mut env: JNIEnv,
    _class: JClass,
    builder_handle: jlong,
    output_path: JString,
) -> jstring {
    let json = guard(
        serde_json::to_string(&FinalizeResult {
            success: false,
            size_bytes: 0,
            error: Some("panic while finalizing engine".to_string()),
        })
        .unwrap(),
        || {
            if builder_handle == 0 {
                return serde_json::to_string(&FinalizeResult {
                    success: false,
                    size_bytes: 0,
                    error: Some("builder handle is null".to_string()),
                })
                .unwrap();
            }
            let path = read_jstring(&mut env, &output_path);
            let filter_set = unsafe { *Box::from_raw(builder_handle as *mut FilterSet) };

            let mut engine = Engine::new_with_filter_set(filter_set);
            engine.use_resources(bundled_resources::bundled_resources());

            let bytes = engine.serialize();
            let (success, size_bytes, error) = match std::fs::write(&path, &bytes) {
                Ok(()) => (true, bytes.len() as u64, None),
                Err(e) => (false, 0, Some(format!("failed to write engine file: {e}"))),
            };

            serde_json::to_string(&FinalizeResult {
                success,
                size_bytes,
                error,
            })
            .unwrap_or_else(|_| "{\"success\":false}".to_string())
        },
    );
    make_jstring(&mut env, &json)
}

// ---------------------------------------------------------------------------------------------
// Engine lifecycle
// ---------------------------------------------------------------------------------------------

/// Loads a previously-compiled engine file from disk into an active `Engine`, re-registering
/// the bundled resources (resource metadata is intentionally not part of the serialized bytes,
/// so it must be re-added every time an engine is deserialized).
#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeLoadEngine(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jlong {
    guard(0, || {
        let path = read_jstring(&mut env, &path);
        let bytes = match std::fs::read(&path) {
            Ok(b) => b,
            Err(_) => return 0,
        };

        let mut engine = Engine::default();
        if engine.deserialize(&bytes).is_err() {
            return 0;
        }
        engine.use_resources(bundled_resources::bundled_resources());

        Box::into_raw(Box::new(engine)) as jlong
    })
}

#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeDestroyEngine(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    guard((), || {
        if handle != 0 {
            unsafe { drop(Box::from_raw(handle as *mut Engine)) };
        }
    });
}

// ---------------------------------------------------------------------------------------------
// Queries against a loaded engine
// ---------------------------------------------------------------------------------------------

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct NetworkCheckResult {
    matched: bool,
    important: bool,
    exception: bool,
    redirect: Option<String>,
    rewritten_url: Option<String>,
    csp: Option<String>,
}

#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeCheckNetworkRequest(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
    source_url: JString,
    request_type: JString,
    method: JString,
) -> jstring {
    let json = guard(error_json("panic during network check"), || {
        if handle == 0 {
            return error_json("engine handle is null");
        }
        let url = read_jstring(&mut env, &url);
        let source_url = read_jstring(&mut env, &source_url);
        let request_type = read_jstring(&mut env, &request_type);
        let method = read_jstring(&mut env, &method);

        let request = match Request::new(&url, &source_url, &request_type, &method) {
            Ok(r) => r,
            Err(e) => return error_json(&format!("invalid request: {e:?}")),
        };

        let engine: &Engine = unsafe { &*(handle as *const Engine) };
        let result = engine.check_network_request(&request);
        let csp = engine.get_csp_directives(&request);

        serde_json::to_string(&NetworkCheckResult {
            matched: result.should_block(),
            important: result.important,
            exception: result.exception.is_some(),
            redirect: result.redirect,
            rewritten_url: result.rewritten_url,
            csp,
        })
        .unwrap_or_else(|_| "{}".to_string())
    });
    make_jstring(&mut env, &json)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CosmeticResourcesResult {
    hide_selectors: Vec<String>,
    procedural_actions: Vec<String>,
    generic_hide: bool,
    injected_script: String,
    exceptions: Vec<String>,
}

#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeUrlCosmeticResources(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
) -> jstring {
    let json = guard(error_json("panic during cosmetic lookup"), || {
        if handle == 0 {
            return error_json("engine handle is null");
        }
        let url = read_jstring(&mut env, &url);

        let engine: &Engine = unsafe { &*(handle as *const Engine) };
        let resources = engine.url_cosmetic_resources(&url);

        serde_json::to_string(&CosmeticResourcesResult {
            hide_selectors: resources.hide_selectors.into_iter().collect(),
            procedural_actions: resources.procedural_actions.into_iter().collect(),
            generic_hide: resources.generichide,
            injected_script: resources.injected_script,
            exceptions: resources.exceptions.into_iter().collect(),
        })
        .unwrap_or_else(|_| "{}".to_string())
    });
    make_jstring(&mut env, &json)
}

// Per adblock-rust's own docs on url_cosmetic_resources: generic (non-hostname-specific)
// hiding rules keyed on a plain class or id selector are deliberately excluded from
// hide_selectors for performance - shipping the full generic ruleset on every page would
// mean injecting selectors for rules that are never relevant to that page's actual DOM.
// Instead, the caller collects the class/id tokens actually present on the page and asks
// for just the matching subset here. See quiver_guard_cosmetic.js for the DOM-scanning side
// of this - it re-calls this incrementally as new elements appear, via a JS bridge object
// (QuiverGuardWebIntegration.ensureBridgeInstalled) since this can't be precomputed the way
// the rest of the cosmetic script is.
#[no_mangle]
pub extern "system" fn Java_com_jhaiian_clint_quiver_engine_QuiverGuardNative_nativeHiddenClassIdSelectors(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    classes_json: JString,
    ids_json: JString,
    exceptions_json: JString,
) -> jstring {
    let json = guard("[]".to_string(), || {
        if handle == 0 {
            return "[]".to_string();
        }
        let classes_json = read_jstring(&mut env, &classes_json);
        let ids_json = read_jstring(&mut env, &ids_json);
        let exceptions_json = read_jstring(&mut env, &exceptions_json);

        let classes: Vec<String> = serde_json::from_str(&classes_json).unwrap_or_default();
        let ids: Vec<String> = serde_json::from_str(&ids_json).unwrap_or_default();
        let exceptions: std::collections::HashSet<String> =
            serde_json::from_str(&exceptions_json).unwrap_or_default();

        let engine: &Engine = unsafe { &*(handle as *const Engine) };
        let selectors = engine.hidden_class_id_selectors(classes, ids, &exceptions);

        serde_json::to_string(&selectors).unwrap_or_else(|_| "[]".to_string())
    });
    make_jstring(&mut env, &json)
}
