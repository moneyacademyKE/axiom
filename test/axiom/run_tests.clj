(ns axiom.run-tests
  "Phase 0 unit tests.

  Targets the PURE surfaces only -- the predicate DSL (`eval-pred`) and the
  decision function (`decide`) -- so the loop's logic is verified with zero
  dependency on shell observers, locks, or git. Halts are deterministic:
  identical (config, world, state) -> identical action.

  Run:
    bb test/axiom/run_tests.clj        ;; exit 0 = all pass, 1 = failure"
  (:require [clojure.test :as t :refer [deftest is testing]]
            [axiom.config :as config]
            [axiom.core :as core]
            [axiom.git-test]
            [axiom.phase1-test]
            [axiom.phase2-test]
            [axiom.phase2-rungs-test]
            [axiom.phase2-guard-test]
            [axiom.phase3-test]
            [axiom.status-test]
            [axiom.notify-test]
            [axiom.phase4-bundles-test]
            [axiom.phase4-hotreload-test]
            [axiom.phase5-dogfood-test]
            [axiom.phase6-operator-test]
            [axiom.phase6-operator-ux-test]
            [axiom.phase7-harness-test]
            [axiom.phase9-opencode-dogfood-test]
            [axiom.phase10-control-test]))   ;; loaded so `t/run-tests` can find it below

;; ---------- predicate DSL ----------

(deftest eval-pred-comparisons
  (testing "numeric comparisons against the world value"
    (is (config/eval-pred {:level-count 5} {:op :>= :ref :level-count :value 3}))
    (is (config/eval-pred {:level-count 3} {:op :>= :ref :level-count :value 3}))
    (is (not (config/eval-pred {:level-count 2} {:op :>= :ref :level-count :value 3})))
    (is (config/eval-pred {:level-count 2} {:op :<= :ref :level-count :value 3}))
    (is (config/eval-pred {:n 4} {:op :> :ref :n :value 3}))
    (is (config/eval-pred {:n 3} {:op :< :ref :n :value 4})))
  (testing "equality / inequality on strings"
    (is (config/eval-pred {:build-ok "pass"} {:op := :ref :build-ok :value "pass"}))
    (is (not (config/eval-pred {:build-ok "fail"} {:op := :ref :build-ok :value "pass"})))
    (is (config/eval-pred {:build-ok "fail"} {:op :not= :ref :build-ok :value "pass"})))
  (testing "missing refs are nil -> comparisons fail safe (false), not crashes"
    (is (not (config/eval-pred {} {:op :>= :ref :level-count :value 0})))
    (is (not (config/eval-pred {} {:op :<  :ref :level-count :value 9})))))

(deftest eval-pred-exists
  (is (config/eval-pred {:level-count 0} {:op :exists :ref :level-count}))
  (is (config/eval-pred {:flag false} {:op :exists :ref :flag}))
  (is (not (config/eval-pred {} {:op :exists :ref :level-count}))))

(deftest eval-pred-boolean-combinators
  (testing ":not negates"
    (is (config/eval-pred {:build-ok "fail"}
                          {:op :not :expr {:op := :ref :build-ok :value "pass"}}))
    (is (not (config/eval-pred {:build-ok "pass"}
                               {:op :not :expr {:op := :ref :build-ok :value "pass"}}))))
  (testing ":and is all-true"
    (is (config/eval-pred {:a 1 :b 2}
                          {:op :and :exprs [{:op :>= :ref :a :value 1}
                                            {:op :<= :ref :b :value 2}]}))
    (is (not (config/eval-pred {:a 1 :b 2}
                               {:op :and :exprs [{:op :>= :ref :a :value 1}
                                                 {:op :> :ref :b :value 5}]}))))
  (testing ":or is any-true"
    (is (config/eval-pred {:a 1}
                          {:op :or :exprs [{:op := :ref :a :value 9}
                                           {:op := :ref :a :value 1}]}))
    (is (not (config/eval-pred {:a 1}
                               {:op :or :exprs [{:op := :ref :a :value 9}
                                                {:op := :ref :a :value 8}]}))))
  (testing "nested composition"
    (is (config/eval-pred {:build-ok "fail" :level-count 3}
                          {:op :and :exprs [{:op :not :expr {:op := :ref :build-ok :value "pass"}}
                                            {:op :>= :ref :level-count :value 3}]}))))

(deftest eval-pred-bare-keyword
  (is (config/eval-pred {:ready? true} :ready?))
  (is (config/eval-pred {:items [1 2]} :items))
  (is (not (config/eval-pred {:ready? false} :ready?)))
  (is (not (config/eval-pred {} :ready?))))

(deftest eval-pred-rejects-unknown-op
  ;; data-not-code: an unknown operator is an explicit error, never silent truthiness
  (is (thrown? Exception (config/eval-pred {:x 1} {:op :bogus :ref :x :value 1}))))

;; ---------- integrity / goal / progress ----------

(deftest integrity-violations
  (testing "empty when every axiom holds"
    (is (empty? (config/integrity-violations {:build-ok "pass" :disk 10}
                                              [{:op := :ref :build-ok :value "pass"}
                                               {:op :>= :ref :disk :value 5}]))))
  (testing "reports each violated check"
    (let [v (config/integrity-violations {:build-ok "fail" :disk 1}
                                          [{:op := :ref :build-ok :value "pass"}
                                           {:op :>= :ref :disk :value 5}])]
      (is (= 2 (count v)))
      (is (every? #(contains? % :check) v))))
  (testing "nil integrity spec -> no violations"
    (is (empty? (config/integrity-violations {:build-ok "fail"} nil)))))

(deftest expand-axioms-bundles
  (testing "an axiom bundle expands to its predicate set"
    (is (= [{:op := :ref :build-ok :value "pass"}]
           (config/expand-axioms {:axiom-bundle :gleam-build})))
    (is (= [{:op := :ref :build-ok :value "pass"}
            {:op := :ref :tests-ok  :value "pass"}]
           (config/expand-axioms {:axiom-bundle :clj-build}))))
  (testing "bundle checks are composed BEFORE explicit :integrity checks"
    (let [ax (config/expand-axioms {:axiom-bundle :rust-build
                                    :integrity [{:op :>= :ref :disk :value 1}]})]
      (is (= 3 (count ax)))
      (is (= :disk (-> ax last :ref)) "explicit checks are appended last")))
  (testing "configs with no bundle just pass :integrity through (possibly empty)"
    (is (= [] (config/expand-axioms {}))
        "no bundle + nil :integrity -> no axioms")
    (is (= [{:op := :ref :build-ok :value "pass"}]
           (config/expand-axioms {:integrity [{:op := :ref :build-ok :value "pass"}]}))))
  (testing "an unknown bundle name fails LOUD (never silently drop axioms)"
    (is (thrown? Exception (config/expand-axioms {:axiom-bundle :nonsense})))))

(deftest clj-build-bundle-halts-on-red-test-or-build
  ;; dogfood: the clj-build bundle must catch a poisoned build OR a red test
  ;; suite as an integrity (halt) condition, exactly as the roadmap demands.
  (let [green  {:build-ok "pass" :tests-ok "pass"}
        red-t  {:build-ok "pass" :tests-ok "FAIL"}
        broken {:build-ok "FAIL" :tests-ok "pass"}]
    (is (empty? (config/integrity-violations green (config/expand-axioms {:axiom-bundle :clj-build})))
        "green build+tests satisfies every clj-build axiom")
    (is (seq (config/integrity-violations red-t (config/expand-axioms {:axiom-bundle :clj-build})))
        "red tests violate the clj-build bundle -> halt, not retry")
    (is (seq (config/integrity-violations broken (config/expand-axioms {:axiom-bundle :clj-build})))
        "broken build violates the clj-build bundle -> halt")))

(deftest goal-met
  (is (config/goal-met? {:level-count 5} {:op :>= :ref :level-count :value 3}))
  (is (not (config/goal-met? {:level-count 1} {:op :>= :ref :level-count :value 3}))))

(deftest progress-signature
  (testing "keyword spec tracks one value"
    (is (= 3 (config/progress-sig {:level-count 3} :level-count))))
  (testing "coll spec hashes only the listed keys"
    (is (= (config/progress-sig {:a 1 :b 2} [:a :b])
           (config/progress-sig {:a 1 :b 2 :c 9} [:a :b])))
    (is (not= (config/progress-sig {:a 1 :b 2} [:a :b])
              (config/progress-sig {:a 1 :b 3} [:a :b]))))
  (testing "nil spec -> any change anywhere is progress"
    (is (= (config/progress-sig {:a 1} nil)
           (config/progress-sig {:a 1} nil)))
    (is (not= (config/progress-sig {:a 1} nil)
              (config/progress-sig {:a 2} nil)))))

;; ---------- decide (the pure core of the loop) ----------

(def base-cfg
  {:goal        {:op :>= :ref :level-count :value 3}
   :integrity   [{:op := :ref :build-ok :value "pass"}]
   :stall-after 3})

(deftest decide-goal-met
  (let [a (core/decide base-cfg {:level-count 3 :build-ok "pass"} {:stall 0})]
    (is (= :done (:type a)))))

(deftest decide-act-when-progressing
  (testing "under the stall budget and goal unmet -> :act"
    (let [a (core/decide base-cfg {:level-count 0 :build-ok "pass"} {:stall 0})]
      (is (= :act (:type a)))))
  (testing "at the last allowed stall iteration it still acts (stall < budget)"
    (let [a (core/decide base-cfg {:level-count 0 :build-ok "pass"} {:stall 2})]
      (is (= :act (:type a))))))

(deftest decide-stall-budget
  (testing "once stall reaches the budget, decide rolls back before halting"
    (let [a (core/decide base-cfg {:level-count 0 :build-ok "pass"} {:stall 3})]
      (is (= :rollback (:type a)))
      (is (= 0 (:rollbacks a)))
      (is (= 2 (:max-rollbacks a)))))
  (testing "stall halts after the rollback budget is exhausted"
    (let [tight (assoc base-cfg :stall-after 1 :max-rollbacks 1)
          a     (core/decide tight {:level-count 0 :build-ok "pass"} {:stall 1 :rollbacks 1})]
      (is (= :halt (:type a)))
      (is (= :stall (:reason a))))))

(deftest decide-integrity-halt
  (testing "a violated axiom halts :integrity"
    (let [a (core/decide base-cfg {:level-count 0 :build-ok "fail"} {:stall 0})]
      (is (= :halt (:type a)))
      (is (= :integrity (:reason a)))
      (is (seq (:violations a)))))
  (testing "integrity is checked BEFORE goal: a broken system halts even if the goal is met"
    (let [a (core/decide base-cfg {:level-count 5 :build-ok "fail"} {:stall 0})]
      (is (= :integrity (:reason a)))
      (is (not= :done (:type a))))))

(deftest decide-determinism
  ;; identical inputs -> identical actions (halt determinism is a Phase 0 guarantee)
  (let [w {:level-count 2 :build-ok "pass"}
        s {:stall 1 :thrash 0 :attempt 4}]
    (is (= (core/decide base-cfg w s)
           (core/decide base-cfg w s)))))

(deftest decide-no-convergence-ladder
  (testing "thrash budget exhausts -> deterministic Phase 2 escalation rungs"
    (let [cfg (assoc base-cfg :thrash-after 4)
          w   {:level-count 0 :build-ok "pass"}]            ;; goal (>=3) unmet, integrity ok
      ;; under budget -> act
      (is (= :act (:type (core/decide cfg w {:stall 0 :thrash 3 :attempt 3}))))
      ;; at budget -> first ladder rung
      (let [a (core/decide cfg w {:stall 0 :thrash 4 :attempt 4})]
        (is (= :escalate (:type a)))
        (is (= :rollback (:rung a)))
        (is (= 0 (:escalation-index a)))
        (is (= 1 (:next-escalation-index a)))
        (is (= 4 (:iterations a))))
      ;; later rungs are selected by explicit state, keeping decide pure
      (is (= :reseed (:rung (core/decide cfg w {:stall 0 :thrash 4 :escalation-index 1}))))
      (is (= :reframe (:rung (core/decide cfg w {:stall 0 :thrash 4 :escalation-index 2}))))
      (is (= :escalate-model (:rung (core/decide cfg w {:stall 0 :thrash 4 :escalation-index 3}))))))
  (testing "ladder exhaustion halts with a complete no-convergence action"
    (let [cfg (assoc base-cfg :thrash-after 4)
          w   {:level-count 0 :build-ok "pass"}
          a   (core/decide cfg w {:stall 0 :thrash 4 :escalation-index 4})]
      (is (= :halt (:type a)))
      (is (= :no-convergence (:reason a)))
      (is (= 4 (:iterations a)))
      (is (= 4 (:escalation-index a)))))
  (testing "thrash is ignored when :thrash-after is unset (Phase 0/1 back-compat)"
    (let [w {:level-count 0 :build-ok "pass"}]
      (is (= :act (:type (core/decide base-cfg w {:stall 0 :thrash 9999 :attempt 9999})))))))

;; ---------- runner ----------

;; Run whenever this file is loaded as a script. Exits 0 iff every test
;; passed (no failures, no errors). Runs BOTH the pure decide/predicate
;; suite AND every phase regression namespace, so the canonical `bb test`
;; command exercises the whole suite -- not just the no-shell core.
(let [summary (t/run-tests 'axiom.run-tests 'axiom.git-test 'axiom.phase1-test 'axiom.phase2-test 'axiom.phase2-rungs-test 'axiom.phase2-guard-test 'axiom.phase3-test 'axiom.status-test 'axiom.notify-test 'axiom.phase4-bundles-test 'axiom.phase4-hotreload-test 'axiom.phase5-dogfood-test 'axiom.phase6-operator-test 'axiom.phase6-operator-ux-test 'axiom.phase7-harness-test 'axiom.phase9-opencode-dogfood-test 'axiom.phase10-control-test)]
  (System/exit (if (pos? (+ (:fail summary 0) (:error summary 0))) 1 0)))