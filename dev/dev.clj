 (ns dev
    (:require [r3bot.septa :as septa]
              [r3bot.core :as core]
              [r3bot.chat :as chat]
              [r3bot.channels :as channels]
              [r3bot.fuzzy :as fuzzy]
              [r3bot.telegram-api :as telegram]
              [r3bot.state :refer [state]]
              [cuerdas.core :as s]
              [cheshire.core :refer [parse-string]]
              [clojure.core.async :as a]))

(def ch (a/chan))

(defn latch-off! []
  (-> @state ::channels/chans :chat-latch (reset! false)))

(defn latch-on! []
  (-> @state ::channels/chans :chat-latch (reset! true)))

