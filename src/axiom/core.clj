(ns axiom.core
  "The core loop: a PURE `decide` function + a side-effecting `run!`.

  Per decision-log D4: decide is pure (config, world, state) -> action,
  tested independently of any real agent. run! performs the I/O, threading
  decide through OBSERVE -> DECIDE -> [DONE | HALT | CHECKPOINT -> ACT ->
  REOBSERVE -> DECIDE].

  State machine (per SPEC):
    OBSERVE -> DECIDE -> { DONE | HALT | ACT -> REOBSERVE -> DECIDE }"
  (:require [axiom.config :as config]
            [axiom.observe :as observe]
            [axiom.lock :as lock]
            [axiom.log :as log]
            [axiom.git :as git]
            [axiom.notify :as notify]
            [axiom.harness :as harness]
            [axiom.control :as control]
            [axiom.budget :as budget]
            [babashka.process :refer [sh]]
            [clojure.string :as str]))

(defn- escalation-action
  "Return the next deterministic Phase 2 ladder action, or final halt.
  The ladder is data so configs can trim or reorder it later, while the
  default matches the roadmap: rollback -> reseed -> reframe ->
  escalate-model -> halt."
  [cfg world state]
  (let [ladder     (:escalations cfg [:rollback :reseed :reframe :escalate-model])
        index      (:escalation-index state 0)
        rung       (nth ladder index nil)
        iterations (:thrash state)]
    (if rung
      {:type :escalate
       :rung rung
       :escalation-index index
       :next-escalation-index (inc index)
       :iterations iterations
       :world world}
      {:type :halt
       :reason :no-convergence
       :iterations iterations
       :escalation-index index
       :world world})))

;; ---------- Pure decision ----------

(defn decide
  "Pure: given config, world, and run-state, return the next action.

  state: {:stall <n> :thrash <n> :attempt <n> :rollbacks <n> :perturb-index <n>}

  Returns:
    {:type :done}                                goal met
    {:type :halt :reason :integrity ...}         axiom violated (halt NOW)
    {:type :rollback ...}                        stall exhausted, rollbacks remain
    {:type :escalate :rung :reseed ...}          no-convergence ladder rung
    {:type :halt :reason :stall ...}             stall exhausted, no rollbacks left
    {:type :halt :reason :no-convergence ...}    ladder exhausted
    {:type :act}                                 perform the act

  Order matters: integrity is checked BEFORE goal -- a broken system halts
  even if the goal is coincidentally met (mission axiom #1: integrity is a
  halt condition, not a retry condition). Rollback-as-recovery (Phase 1):
  when the stall budget is exhausted but rollbacks remain, we reset to the
  last-known-good checkpoint and retry instead of dying."
  [cfg world state]
  (let [violations    (config/integrity-violations world (:integrity cfg))
        stall-after   (:stall-after cfg 3)
        max-rollbacks (:max-rollbacks cfg 2)
        rollbacks     (:rollbacks state 0)
        ;; Phase 2 rung 0: the no-convergence budget. Default off (huge) so
        ;; Phase 0/1 configs are unaffected; set :thrash-after to trigger
        ;; the escalation ladder when state changes but the goal never lands.
        thrash-after  (:thrash-after cfg Long/MAX_VALUE)]
    (cond
      (seq violations)
      {:type :halt :reason :integrity :violations violations :world world}

      (config/goal-met? world (:goal cfg))
      {:type :done :world world}

      (budget/exhausted cfg state)
      (merge {:type :halt :reason :budget-exhausted :world world}
             (budget/exhausted cfg state))

      (and (:max-attempts cfg) (>= (or (:attempt state) 0) (:max-attempts cfg)))
      {:type :halt :reason :budget-exhausted :budget :attempts :attempts (:attempt state 0) :world world}

      (let [started (:started-at-ms state) now (:now-ms state) max-ms (:max-wall-ms cfg)]
        (and max-ms started now (>= (- now started) max-ms)))
      {:type :halt :reason :budget-exhausted :budget :wall-clock :elapsed-ms (- (:now-ms state) (:started-at-ms state)) :world world}

      ;; Phase 1: stall budget exhausted. If rollbacks remain, recover by
      ;; resetting to last-known-good; otherwise halt for good.
      (>= (or (:stall state) 0) stall-after)
      (if (< rollbacks max-rollbacks)
        {:type :rollback :rollbacks rollbacks :max-rollbacks max-rollbacks
         :world world}
        {:type :halt :reason :stall :iterations (:stall state)
         :rollbacks rollbacks :world world})

      (>= (or (:thrash state) 0) thrash-after)
      (escalation-action cfg world state)

      :else
      {:type :act})))

(defn- render-template
  "Replace {{key}} placeholders with world values.
  e.g. \"./gen.sh {{attempt}}\" with {:attempt 2} -> \"./gen.sh 2\""
  [tmpl ctx]
  (reduce (fn [s [k v]]
            (str/replace s (str "{{" (name k) "}}") (str v)))
          tmpl ctx))

(defn- selected-act
  [cfg state]
  (let [act-specs (:act cfg)
        as-vec    (if (map? act-specs) [act-specs] act-specs)
        pidx      (or (:prompt-index state 0) 0)]
    (nth as-vec (min pidx (dec (count as-vec))))))

(defn- run-act!
  "Execute the configured act via bash -c. Returns the process result map.

  act may be a single map {:sh \"cmd {{attempt}}\" :timeout <ms>} (Phase 0/1)
  OR a vector of maps (Phase 2 :reframe ladder) -- the entry is selected by
  (:prompt-index state 0), clamped to the last template so a stale index
  never nil-pokes. {{model}} is rendered from (:models cfg) indexed by
  (:model-index state 0) for the :escalate-model rung; absent :models ->
  {{model}} collapses to an empty string, leaving Phase 0/1 templates intact."
  [cfg world state]
  (let [act     (selected-act cfg state)
        pidx    (or (:prompt-index state 0) 0)
        attempt (:attempt state 0)
        models  (:models cfg)
        midx    (or (:model-index state 0) 0)
        model   (when (seq models)
                  (nth models (min midx (dec (count models))) ""))]
    (if (:harness act)
      (let [invocation (harness/build-invocation cfg world state act)]
        (log/event! (:log-dir cfg) :info "acting via harness"
                    (select-keys invocation [:harness :argv :prompt :model]))
        (harness/invoke! cfg invocation))
      (let [tmpl    (:sh act)
            cmd     (render-template tmpl (assoc world :attempt attempt :model (or model "")))
            timeout (:timeout act 120000)]
        (log/event! (:log-dir cfg) :info "acting"
                    {:cmd cmd :attempt attempt :prompt-index pidx :model model})
        (sh {:out :string :err :string :dir (:workdir cfg ".")
             :continue true :timeout timeout} "bash" "-c" cmd)))))

(defn- halt-result!
  [cfg iteration action]
  (let [workdir    (:workdir cfg ".")
        log-dir    (:log-dir cfg)
        tag-prefix (get-in cfg [:checkpoint :tag-prefix] "axiom")
        reason     (:reason action)]
    (log/event! log-dir :halt (str "HALT: " (name reason))
                (dissoc action :type))
    (log/iteration! log-dir iteration (assoc action :event :halt))
    ;; ADR-0003 / Phase 1: restore last-known-good so a corrupting
    ;; or non-progressing act cannot poison the workspace. No-op
    ;; (returns nil) outside a git repo, so non-git demos are
    ;; unaffected; :rolled-back? records whether it fired.
    (let [tag      (str tag-prefix "-last-good")
          restored (git/rollback! workdir tag)]
      (when restored
        (log/event! log-dir :info "rollback" {:to tag}))
      ;; Phase 3: emit a self-contained diagnostic bundle so a human can
      ;; name the blocker from the bundle alone (ROADMAP Phase 3 done-when).
      ;; Written after the halt iteration record so the bundle includes it.
      (let [bundle (log/halt-bundle! log-dir action)]
        ;; Phase 3: fire the opt-in notifier (ROADMAP Phase 3). No-op when
        ;; :notify is absent, so Phase 0/1/2 configs + tests are unchanged.
        (notify/notify! cfg action bundle))
      {:status :halt :reason reason :iterations iteration
       :detail action :rolled-back? (boolean restored)})))

;; ---------- The loop ----------

;; ---------- Hot-reload cfg swap (Phase 4b) ----------

;; The loop's OS context never reloads: workdir, lock, log-dir, checkpoint,
;; config-path, and the hot-reload flag itself belong to the running process,
;; not the brain. A swapped config contributes the brain (observers, goal,
;; integrity, act, progress, budgets, notify, models, guard) while these
;; loop-owned values stay fixed. Run-state (stall/thrash/attempt/rollbacks/
;; prompt-index/model-index/seen-tuples) lives in `state`, never in cfg, so a
;; swap can never lose progress bookkeeping.
(defn- swap-cfg
  [old-cfg new-cfg]
  (merge new-cfg (select-keys old-cfg [:workdir :lock :log-dir :checkpoint
                                        :config-path :hot-reload])))

(defn run!
  "Drive the loop to completion. Returns:
    {:status :done  :world w :iterations n}
    {:status :halt  :reason :integrity|:stall|:max-iters ...}

  Acquires the lock (refuses if held live); releases on exit. Logs every
  iteration as structured EDN. opts: {:max-iters N :config-path path}."
  ([cfg] (run! cfg {}))
  ([cfg opts]
   (let [workdir     (:workdir cfg ".")
         lock-path   (:lock cfg (str workdir "/.axiom.lock"))
         log-dir     (:log-dir cfg)
         max-iters   (:max-iters opts 1000)
         config-path (:config-path opts)
         init-mtime  (when config-path (config/file-mtime config-path))]
     (lock/acquire! lock-path)
     (try
       (loop [iteration  0
              cfg         cfg
              last-mtime  init-mtime
              state       {:stall 0 :thrash 0 :attempt 0 :seen-tuples #{}
                           :started-at-ms (System/currentTimeMillis)}]
         (let [[cfg* maybe-mtime] (if (and (:hot-reload cfg false) config-path)
                                    (config/maybe-reload cfg config-path last-mtime)
                                    [cfg nil])
               swapped?   (some? maybe-mtime)
               cfg        (if swapped?
                            (do (log/event! log-dir :info "hot-reload"
                                            {:config-path config-path})
                                (swap-cfg cfg cfg*))
                            cfg)
               last-mtime (if swapped? maybe-mtime last-mtime)
               world      (observe/build-world (:observers cfg) {:dir workdir})
               state      (assoc state :now-ms (System/currentTimeMillis))
               ctl        (control/state cfg)
               action     (cond
                            (= :stop-requested (:state ctl)) {:type :halt :reason :operator-stop :world world}
                            (= :paused (:state ctl)) {:type :halt :reason :operator-paused :world world}
                            (>= iteration max-iters) {:type :halt :reason :max-iters :world {}}
                            :else (decide cfg world state))]
           (case (:type action)
             :done
             (do (log/event! log-dir :info "GOAL FULFILLED" {:world world})
                 (log/iteration! log-dir iteration {:event :done :world world})
                 {:status :done :world world :iterations iteration})

             :halt
             (halt-result! cfg iteration action)

             :rollback
             (let [tag      (str (get-in cfg [:checkpoint :tag-prefix] "axiom") "-last-good")
                   restored (git/rollback! workdir tag)
                   state'   (-> state
                                (update :rollbacks (fnil inc 0))
                                (assoc :stall 0))]
               (log/event! log-dir :warn "rollback recovery"
                           {:to tag :restored? (boolean restored)
                            :rollbacks (:rollbacks state')})
               (log/iteration! log-dir iteration
                               (assoc action :event :rollback
                                      :rolled-back? (boolean restored)))
               (recur (inc iteration) cfg last-mtime state'))

             :escalate
             (let [rung       (:rung action)
                   base-state (assoc state
                                     :stall 0 :thrash 0
                                     :escalation-index (:next-escalation-index action))
                   state'     (case rung
                                :reseed
                                (do (when-let [rs (:reseed cfg)]
                                      (let [cmd (render-template (:sh rs) world)]
                                        (log/event! log-dir :info "reseed" {:cmd cmd})
                                        (sh {:out :string :err :string :dir workdir
                                             :continue true} "bash" "-c" cmd)))
                                    base-state)
                                :reframe
                                (let [idx (inc (or (:prompt-index state 0) 0))]
                                  (log/event! log-dir :info "reframe" {:prompt-index idx})
                                  (assoc base-state :prompt-index idx))
                                :escalate-model
                                (let [idx (inc (or (:model-index state 0) 0))]
                                  (log/event! log-dir :info "escalate-model" {:model-index idx})
                                  (assoc base-state :model-index idx))
                                base-state)]
               (log/iteration! log-dir iteration
                               (assoc action :event :escalate :rung rung))
               (recur (inc iteration) cfg last-mtime state'))

             :act
             (let [attempt    (:attempt state 0)
                   pidx       (or (:prompt-index state 0) 0)
                   midx       (or (:model-index state 0) 0)
                   before     (config/progress-sig world (:progress cfg))
                   seen       (:seen-tuples state #{})
                   tuple      [pidx midx before]
                   identical? (and (:identical-retry-guard cfg false)
                                   (contains? seen tuple))]
               (if identical?
                 (do
                   (log/event! log-dir :info "identical-skip"
                               {:tuple tuple :stall (inc (:stall state 0))})
                   (log/iteration! log-dir iteration
                                   {:event :identical-skip :tuple tuple
                                    :prompt-index pidx :model-index midx
                                    :attempt attempt})
                   (recur (inc iteration) cfg last-mtime
                          (-> state
                              (assoc :stall (inc (:stall state 0)))
                              (update :thrash (fnil inc 0)))))
                 (let [_       (git/tag! workdir (str (get-in cfg [:checkpoint :tag-prefix] "axiom") "-last-good"))
                       t0      (System/currentTimeMillis)
                       result  (run-act! cfg world state)
                       after-w (observe/build-world (:observers cfg) {:dir workdir})
                       after   (config/progress-sig after-w (:progress cfg))
                       moved?  (not= before after)
                       state'  (-> state
                                   (budget/add-usage result)
                                   (update :attempt inc)
                                   (assoc :stall (if moved? 0 (inc (:stall state 0))))
                                   (update :thrash (fnil inc 0))
                                   (assoc :seen-tuples (conj seen tuple)))]
                   (log/iteration! log-dir iteration
                                   (merge {:event :act :attempt attempt :exit (:exit result)
                                           :duration-ms (- (System/currentTimeMillis) t0)
                                           :progress-before before :progress-after after
                                           :progressed? moved?}
                                          (budget/totals state')))
                   (recur (inc iteration) cfg last-mtime state')))))))
       (finally
         (lock/release! lock-path))))))
