(ns r3bot.specs
  (:require [clojure.spec :as s]))

(s/def ::bot-text (s/keys :req-un [::from ::to] :opt-un [::number-trains]))
(s/def ::from keyword?)
(s/def ::to keyword?)
(s/def ::number-trains pos-int?)


(defn validate [data spec]
  (let [cd (s/conform spec data)]
    (if (= cd ::s/invalid)
      {::error (s/explain-str spec data)}
      cd)))
