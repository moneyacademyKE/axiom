#!/usr/bin/env bb
(ns axiom.main
  "Axiom -- autonomous goal-fulfillment runner.

  Usage:
    bb axiom.clj <config.edn> [--max-iters N] [--once]
    bb axiom.clj status <config.edn> [--last N]

  Exits 0 if the goal was fulfilled, 1 on halt (stall/integrity/max-iters),
  2 on usage error. The `status` subcommand always exits 0 (read-only).")

(require '[axiom.config :as config]
         '[axiom.core :as core]
         '[axiom.status :as status]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(defn- parse-run-args
  "Parse the flags for a normal run: --max-iters N / --max-iters=N / --once.
  The first positional arg is the config path."
  [args]
  (loop [args args acc {:max-iters 1000 :once false :config nil}]
    (cond
      (empty? args) acc
      (= (first args) "--max-iters")
      (recur (nnext args) (assoc acc :max-iters (Integer/parseInt (second args))))
      (str/starts-with? (first args) "--max-iters=")
      (recur (next args) (assoc acc :max-iters
                                (Integer/parseInt (subs (first args) 12))))
      (= (first args) "--once") (recur (next args) (assoc acc :once true))
      (= (first args) "--last")
      (recur (nnext args) (assoc acc :last (Integer/parseInt (second args))))
      (str/starts-with? (first args) "--last=")
      (recur (next args) (assoc acc :last (Integer/parseInt (subs (first args) 7))))
      :else (recur (next args) (assoc acc :config (first args))))))

(defn -main [& args]
  ;; `status` subcommand: read-only structured-log tail (Phase 3).
  (if (= (first args) "status")
    (let [opts (parse-run-args (rest args))]
      (when-not (:config opts)
        (println "Usage: bb axiom.clj status <config.edn> [--last N]")
        (System/exit 2))
      ;; Read config as raw EDN (no validation) so a partially-broken or
      ;; stale config can still report its last-known run state.
      (let [flat (edn/read-string (slurp (:config opts)))]
        (status/summarize! flat {:last (:last opts 20)})
        (System/exit 0)))
    ;; Normal run: load (validating), run the loop, exit on result.
    (let [opts (parse-run-args args)]
      (when-not (:config opts)
        (println "Usage: bb axiom.clj <config.edn> [--max-iters N] [--once]")
        (System/exit 2))
      (let [cfg (-> (config/load-config (:config opts))
                    (merge (when (:once opts) {:max-iters-override 1})))]
        (let [run-opts {:max-iters (if (:once opts) 1 (:max-iters opts))
                        :config-path (:config opts)}
              res      (core/run! cfg run-opts)]
          (System/exit (if (= :done (:status res)) 0 1)))))))

(apply -main *command-line-args*)