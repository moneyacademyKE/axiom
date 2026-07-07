(ns axiom.config
  "EDN config loading + the predicate DSL interpreter.

  Config is pure data (EDN). Predicates are interpreted by `eval-pred`,
  a pure function of (world, predicate-data) -> boolean. This keeps config
  inspectable, sandboxable, and testable -- no eval, no code-as-config.

  Per mission: 'The runner is simpler than the system it supervises.'
  Per decision-log D4: the (config, world) -> decision function must be
  testable independently of any real agent."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; ---------- Config loading ----------

(def ^:private required-keys [:name :observers :goal :act])

;; ---------- Axiom bundles (Phase 1) ----------
;; Pre-built integrity (axiom) check sets, keyed by project type. A config
;; may set :axiom-bundle <keyword> to pull in a standard set, composed with
;; any explicit :integrity checks. Keeps per-project configs DRY.

(def axiom-bundles
  "Pre-built integrity (axiom) check sets, keyed by project type. A config
  may set :axiom-bundle <keyword> to pull in a standard set, composed with
  any explicit :integrity checks. Keeps per-project configs DRY.

  Each bundle asserts the *baseline* that a build of that toolchain must
  preserve across every iteration -- not the goal, just the floor below
  which the iteration is considered to have poisoned the project."
  {:gleam-build [{:op := :ref :build-ok :value "pass"}]
   :rust-build  [{:op := :ref :build-ok :value "pass"}
                 {:op := :ref :tests-ok  :value "pass"}]
   ;; clj-build dogfoods Axiom's own toolchain (Babashka). A Clojure build
   ;; is `pass` when the project loads cleanly AND its test suite is green;
   ;; either going red means the act poisoned the project (halt, not retry).
   :clj-build   [{:op := :ref :build-ok :value "pass"}
                 {:op := :ref :tests-ok  :value "pass"}]
   :node-build  [{:op := :ref :build-ok :value "pass"}
                 {:op := :ref :tests-ok  :value "pass"}]
   :python-build [{:op := :ref :build-ok :value "pass"}
                  {:op := :ref :tests-ok  :value "pass"}]
   :go-build    [{:op := :ref :build-ok :value "pass"}
                 {:op := :ref :tests-ok  :value "pass"}]
   :clean-tree  [{:op := :ref :git-clean :value "true"}]})

(defn expand-axioms
  "Compose :axiom-bundle predicates with explicit :integrity checks.
  Bundle checks come first, explicit checks appended. Returns a list.
  Throws on an unknown bundle name (fail loud, never silently drop axioms).

  `:axiom-bundle` may be a single keyword OR a vector of keywords; a vector
  is flattened in order (Phase 4 composability) before any explicit
  :integrity checks are appended -- e.g. [:clj-build :clean-tree] asserts a
  green Clojure build AND a clean git tree, then your explicit checks."
  [cfg]
  (let [bundle-name (:axiom-bundle cfg)
        names       (cond
                      (nil? bundle-name)        nil
                      (keyword? bundle-name)   [bundle-name]
                      (coll? bundle-name)       (vec bundle-name)
                      :else                     (throw
                                                 (ex-info (str ":axiom-bundle must be a keyword or vector of keywords; got "
                                                               (pr-str bundle-name))
                                                          {:axiom-bundle bundle-name})))
        bundles     (when names
                      (mapv (fn [n]
                              (or (axiom-bundles n)
                                  (throw (ex-info (str "Unknown axiom bundle: " n)
                                                  {:bundle n
                                                   :known (keys axiom-bundles)}))))
                            names))
        ;; flatten each bundle's predicate list in order, then append explicit
        explicit    (:integrity cfg [])]
    (cond-> []
      (seq bundles) (into (mapcat identity bundles))
      :always       (into explicit))))

(defn- valid-pred?
  "Static structural check: is `pred` a well-formed predicate? Does NOT
  evaluate it (no world needed). Used by `validate-config` to reject
  malformed goals/axioms at load time (Phase 3) instead of discovering the
  breakage mid-loop. Mirrors the grammar accepted by `eval-pred`."
  [pred]
  (cond
    (keyword? pred) true
    (map? pred)
    (let [op (:op pred)]
      (case op
        (:>= :<= := :not= :> :<) (and (:ref pred) (contains? pred :value))
        :exists  (boolean (:ref pred))
        :not     (valid-pred? (:expr pred))
        :and     (and (coll? (:exprs pred)) (seq (:exprs pred))
                      (every? valid-pred? (:exprs pred)))
        :or      (and (coll? (:exprs pred)) (seq (:exprs pred))
                      (every? valid-pred? (:exprs pred)))
        false))
    :else false))

(defn validate-config
  "Pure: returns a vector of validation error maps for `cfg`, empty when
  valid. Phase 3 deliverable -- 'reject goals without a clean predicate +
  signature' (ROADMAP) -- so a malformed config fails loud at load time
  rather than producing a confusing mid-run halt.

  Each error: {:key <config-key> :problem <string>}. Empty vector = valid."
  [cfg]
  (let [act (:act cfg)
        act-errors
        (cond
          (nil? act)              [{:key :act :problem "missing"}]
          (map? act)              (if (:harness act)
                                    (when-not (:prompt act)
                                      [{:key :act :problem "harness act missing :prompt"}])
                                    (when-not (:sh act)
                                      [{:key :act :problem "act map missing :sh"}]))
          (vector? act)           (vec (keep (fn [[i a]]
                                               (cond
                                                 (:harness a)
                                                 (when-not (:prompt a)
                                                   {:key :act
                                                    :problem (str "act[" i "] harness missing :prompt")})
                                                 (not (:sh a))
                                                 {:key :act
                                                  :problem (str "act[" i "] missing :sh")}))
                                             (map-indexed vector act)))
          :else                   [{:key :act
                                     :problem "act must be a map or vector of maps"}])
        integrity-errors
        (vec (keep (fn [[i c]]
                     (when-not (valid-pred? c)
                       {:key :integrity
                        :problem (str "check[" i "] not a well-formed predicate")}))
                   (map-indexed vector (:integrity cfg []))))]
    (cond-> []
      (not (valid-pred? (:goal cfg)))
      (conj {:key :goal :problem "not a well-formed predicate"})

      (not (contains? cfg :progress))
      (conj {:key :progress :problem "missing progress signature"})

      :always (into act-errors)
      :always (into integrity-errors))))

(defn load-config
  "Read and parse an EDN config file. Validates required keys + structural
  validity (Phase 3): goal is a clean predicate, a progress signature is
  present, every act carries :sh, and every integrity check is well-formed.
  Expands :axiom-bundle into :integrity. Throws ex-info with diagnostic
  context on missing file, missing keys, validation errors, or unknown
  bundle."
  [path]
  (let [f (io/file path)]
    (when-not (.exists f)
      (throw (ex-info (str "Config not found: " path) {:path path})))
    (let [cfg (edn/read-string (slurp f))]
      (doseq [k required-keys]
        (when-not (contains? cfg k)
          (throw (ex-info (str "Config missing required key: " k)
                          {:key k :present (keys cfg)}))))
      (let [cfg    (assoc cfg :integrity (expand-axioms cfg))
            errors (validate-config cfg)]
        (when (seq errors)
          (throw (ex-info "Config validation failed" {:errors errors})))
        cfg))))

;; ---------- Hot-reload (Phase 4b) ----------

(defn file-mtime
  "Last-modified millis of the file at `path`, or nil when it does not exist.
  Used by `maybe-reload` to detect an on-disk config change."
  [path]
  (let [f (io/file path)]
    (when (.exists f)
      (.lastModified f))))

(defn maybe-reload
  "Hot-reload gate (Phase 4b). Given the current `cfg`, its on-disk file
  `path`, and the `last-mtime` recorded at the last load (nil = never),
  decide whether the config changed since the last load. Returns:
    [cfg      nil]      -- file unchanged since last-mtime: keep cfg, no swap
    [new-cfg  new-mtime] -- file changed: re-read + validate + expand, new cfg
  Throws ex-info when the file changed but is now missing or invalid -- a
  poisoned swap halts loudly rather than running a corrupt config. A
  vanished file counts as a change (mtime nil != last-mtime), which
  load-config rejects with 'Config not found'."
  [cfg path last-mtime]
  (let [now (file-mtime path)]
    (if (= now last-mtime)
      [cfg nil]
      (let [new-cfg (load-config path)]
        [new-cfg now]))))

;; ---------- Predicate DSL ----------

(defn- num-cmp
  "Fail-safe numeric comparison: a missing/non-numeric ref is treated as
  'not satisfied' (false) rather than throwing. Observers yield nil on
  failure (see axiom.observe), so a predicate must never crash the loop on
  a broken observer -- per the design, 'predicates treat nil as missing'."
  [f cur value]
  (and (number? cur) (f cur value)))

(defn eval-pred
  "Pure interpreter over a world map. Returns boolean.

  Predicate grammar (data, not code):

    {:op :>=  :ref :level-count :value 3}   comparison
    {:op :<=  :ref :level-count :value 3}
    {:op :=   :ref :build-ok    :value \"pass\"}
    {:op :not= :ref :status     :value \"broken\"}
    {:op :>   :ref :level-count :value 0}
    {:op :<   ...}
    {:op :exists :ref :level-count}         null-check
    {:op :not  :expr <pred>}                negation
    {:op :and  :exprs [<pred> ...]}         conjunction
    {:op :or   :exprs [<pred> ...]}         disjunction

  A bare keyword predicate is a truthy check on that key."
  [world pred]
  (cond
    ;; bare keyword -> truthy check
    (keyword? pred)
    (boolean (get world pred))

    (map? pred)
    (let [op     (:op pred)
          ref    (:ref pred)
          cur    (get world ref)]
      (case op
        :>=     (num-cmp >=  cur (:value pred))
        :<=     (num-cmp <=  cur (:value pred))
        :=      (=   cur (:value pred))
        :not=   (not= cur (:value pred))
        :>      (num-cmp >   cur (:value pred))
        :<      (num-cmp <   cur (:value pred))
        :exists (some? cur)
        :not    (not  (eval-pred world (:expr pred)))
        :and    (every? #(eval-pred world %) (:exprs pred))
        :or     (some   #(eval-pred world %) (:exprs pred))
        (throw (ex-info (str "Unknown predicate op: " op)
                        {:op op :pred pred}))))

    :else
    (throw (ex-info (str "Bad predicate: " (pr-str pred)) {:pred pred}))))

(defn integrity-violations
  "Returns a seq of violated integrity checks. Empty = all axioms hold.
  Each element is {:check <pred>} for diagnostics."
  [world integrity-spec]
  (keep (fn [check]
          (when-not (eval-pred world check)
            {:check check}))
        (or integrity-spec [])))

(defn goal-met?
  "Does the world satisfy the goal predicate?"
  [world goal-spec]
  (eval-pred world goal-spec))

;; ---------- Progress signature ----------

(defn progress-sig
  "Compute the monotonic progress signature from a world map.
  Unchanged signature across iterations = no forward progress.

  - keyword      : that world value
  - coll of keys : hash of those values (order-independent for sets)
  - nil          : hash of the whole world (change in anything = progress)"
  [world progress-spec]
  (cond
    (keyword? progress-spec) (get world progress-spec)
    (coll?    progress-spec) (hash (mapv #(get world %) progress-spec))
    (nil?     progress-spec) (hash world)
    :else                    (hash world)))
