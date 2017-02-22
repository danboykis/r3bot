(ns r3bot.fuzzy
  (:require [cuerdas.core :as s]
            [r3bot.state :refer [state]]
            [clj-fuzzy.soundex :as soundex]))

(defn soundex-metric [s1 s2]
  (mapv (fn [a b] (- (int a) (int b))) (vec s1) (vec s2)))

(defn soundex-min [s-metric1 s-metric2]
  (loop [m1 s-metric1 m2 s-metric2]
    (cond (empty? m1) s-metric1
          (empty? m2) s-metric2
          (< (Math/abs (first m1)) (Math/abs (first m2))) s-metric1
          (< (Math/abs (first m2)) (Math/abs (first m1))) s-metric2
          :else (recur (rest m1) (rest m2)))))

(defn soundex-min-key
  ([k x] x)
  ([k x y] (let [kx (k x) ky (k y)] (if (= kx (soundex-min kx ky)) x y)))
  ([k x y & more]
   (reduce #(soundex-min-key k %1 %2) (soundex-min-key k x y) more)))


(defn fuzzy-decide-station [station]
  (let [stations             (map name (-> @state :r3bot.core/config :septa :stations keys))
        stations-soundex     (map soundex/process stations)
        index                (zipmap stations-soundex stations)
        soundex-with-metrics (map (fn [s] [s (soundex-metric (soundex/process (s/lower station)) s)]) stations-soundex)]
    (keyword (get index (first (apply soundex-min-key second soundex-with-metrics))))))
